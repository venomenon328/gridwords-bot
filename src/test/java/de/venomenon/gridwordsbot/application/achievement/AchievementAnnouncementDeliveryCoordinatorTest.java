package de.venomenon.gridwordsbot.application.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementMessageGateway;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class AchievementAnnouncementDeliveryCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-08T07:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void revalidatesThenPersistsMessageIdBeforeSynchronizing() {
        Fixture fixture = new Fixture(true);
        try {
            assertThat(fixture.coordinator.runNext()).isEqualTo(AchievementAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
            verify(fixture.messages).create(eq(2L), any(RenderedAchievementAnnouncement.class));
            verify(fixture.announcements).replaceClaimedItems(eq(fixture.key), eq(fixture.token), eq(List.of(fixture.event.fact().eventId())));
            verify(fixture.announcements).markDelivered(eq(fixture.key), eq(fixture.token), eq(99L), any());
            verify(fixture.announcements).markSynchronized(eq(fixture.key), eq(fixture.token), any());
        } finally { fixture.executor.shutdownNow(); }
    }

    @Test
    void suppressesAnObsoleteLiveClaimWithoutAnyDiscordCreate() {
        Fixture fixture = new Fixture(false);
        try {
            assertThat(fixture.coordinator.runNext()).isEqualTo(AchievementAnnouncementDeliveryCoordinator.RunResult.SUPPRESSED);
            verify(fixture.announcements).replaceClaimedItems(eq(fixture.key), eq(fixture.token), eq(List.of()));
            verify(fixture.announcements).markSuppressed(eq(fixture.key), eq(fixture.token), any());
            verify(fixture.messages, never()).create(anyLong(), any(RenderedAchievementAnnouncement.class));
        } finally { fixture.executor.shutdownNow(); }
    }

    @Test
    void deliversHistoricalIntroductionEvenWhenParticipantHasNoAwards() {
        Fixture fixture = new Fixture(AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION, false, false);
        try {
            assertThat(fixture.coordinator.runNext()).isEqualTo(AchievementAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
            verify(fixture.announcements).replaceClaimedItems(eq(fixture.key), eq(fixture.token), eq(List.of()));
            verify(fixture.messages).create(eq(2L), any(RenderedAchievementAnnouncement.class));
            verify(fixture.announcements, never()).markSuppressed(eq(fixture.key), eq(fixture.token), any());
            verify(fixture.announcements).markSynchronized(eq(fixture.key), eq(fixture.token), any());
        } finally { fixture.executor.shutdownNow(); }
    }

    @Test
    void missingPersistedEventIsTechnicalFailureNotSilentSuppression() {
        Fixture fixture = new Fixture(true);
        try {
            when(fixture.events.find(fixture.event.fact().eventId())).thenReturn(Optional.empty());

            assertThatThrownBy(fixture.coordinator::runNext)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missing event");
            verify(fixture.announcements, never()).markSuppressed(eq(fixture.key), eq(fixture.token), any());
        } finally { fixture.executor.shutdownNow(); }
    }

    private static final class Fixture {
        final AchievementAnnouncementStore announcements = mock(AchievementAnnouncementStore.class);
        final AchievementEventStore events = mock(AchievementEventStore.class);
        final AchievementAwardStateStore awards = mock(AchievementAwardStateStore.class);
        final PlayerStore players = mock(PlayerStore.class);
        final AchievementAnnouncementMessageGateway messages = mock(AchievementAnnouncementMessageGateway.class);
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final UUID token = UUID.randomUUID();
        final AchievementAnnouncement.Key key;
        final AchievementEventFact.Snapshot event;
        final AchievementAnnouncementDeliveryCoordinator coordinator;

        Fixture(boolean active) {
            this(AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH, true, active);
        }

        Fixture(AchievementAnnouncement.Type type, boolean withEvent, boolean active) {
            var catalog = AchievementDefinitionCatalog.achievementsV1();
            var definition = catalog.definitions().getFirst();
            var awardKey = new AchievementAwardState.Key(1, 3, definition.key());
            key = new AchievementAnnouncement.Key(1,
                    type == AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH ? "live:achievement:1" : "historical:achievement:1");
            event = new AchievementEventFact.Snapshot(new AchievementEventFact.Draft(UUID.randomUUID(), "event:1", awardKey,
                    catalog.version(), AchievementEventFact.Type.UNLOCKED, LocalDate.of(2026, 8, 7),
                    AchievementEvidence.Kind.GAME_RESULT, "result:1", AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION, NOW), NOW);
            AchievementAnnouncement.Registration registration = new AchievementAnnouncement.Registration(1, 2, 3, catalog.version(),
                    type, key.idempotencyKey(), "old", "a".repeat(64));
            AchievementAnnouncement.Snapshot claim = new AchievementAnnouncement.Snapshot(1, registration,
                    AchievementAnnouncement.DeliveryState.CLAIMED, Optional.of(token), Optional.of(NOW.plusSeconds(60)), 1,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), NOW, NOW);
            when(announcements.claimNext(any())).thenReturn(Optional.of(claim));
            when(announcements.renewLease(any(), eq(token), any())).thenReturn(true);
            when(announcements.findItems(key)).thenReturn(withEvent
                    ? List.of(new AchievementAnnouncement.Item(0, event.fact().eventId())) : List.of());
            if (withEvent) {
                when(events.find(event.fact().eventId())).thenReturn(Optional.of(event));
                AchievementAwardState.Write write = new AchievementAwardState.Write(catalog.version(),
                        active ? AchievementAwardState.Status.ACTIVE : AchievementAwardState.Status.INVALIDATED,
                        LocalDate.of(2026, 8, 7), NOW, AchievementEvidence.Kind.GAME_RESULT, "result:1",
                        active ? Optional.empty() : Optional.of(NOW));
                AchievementAwardState.Snapshot state = new AchievementAwardState.Snapshot(awardKey, write,
                        AchievementAwardState.LockVersion.initial(), NOW, NOW);
                when(awards.findAll(1, 3)).thenReturn(List.of(state));
            } else {
                when(awards.findAll(1, 3)).thenReturn(List.of());
            }
            when(players.findByDiscordUserId(3)).thenReturn(Optional.of(new PlayerStore.StoredPlayer(3, "Ada", true, false, true, NOW, NOW)));
            when(announcements.markSuppressed(eq(key), eq(token), any())).thenReturn(true);
            when(announcements.replaceClaimedItems(eq(key), eq(token), any())).thenReturn(true);
            when(announcements.updateClaimedContent(eq(key), eq(token), any(), any())).thenReturn(true);
            when(messages.create(eq(2L), any())).thenReturn(99L);
            when(announcements.markDelivered(eq(key), eq(token), eq(99L), any())).thenReturn(true);
            when(announcements.markSynchronized(eq(key), eq(token), any())).thenReturn(true);
            coordinator = new AchievementAnnouncementDeliveryCoordinator(announcements, events, awards, players, messages,
                    catalog, AchievementEmojiResolver.unicodeOnly(), CLOCK, Duration.ofMinutes(2), Duration.ofSeconds(30),
                    Duration.ofSeconds(10), Duration.ofMinutes(5), executor);
        }
    }
}
