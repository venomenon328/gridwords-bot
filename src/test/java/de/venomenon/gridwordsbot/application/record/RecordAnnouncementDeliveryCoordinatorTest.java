package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.DurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementClaim;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementMessage;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementPhase;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSubject;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordEventValidity;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementMessageGateway;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RecordAnnouncementDeliveryCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-06T20:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final RecordAnnouncementKey KEY = new RecordAnnouncementKey(1, 2, "record-live:1");
    private final List<ScheduledExecutorService> executors = new ArrayList<>();

    @AfterEach
    void stopHeartbeats() {
        executors.forEach(ScheduledExecutorService::shutdownNow);
    }

    @Test
    void editsThePersistedStablePageWithoutCreateDiscovery() {
        Fixture fixture = fixture(RecordAnnouncementProjection.EDIT, List.of(new RecordAnnouncementMessage(0, 100)), true);

        assertThat(fixture.coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);

        assertThat(fixture.gateway.edited).containsExactly(100L);
        assertThat(fixture.gateway.created).isEmpty();
        assertThat(fixture.gateway.deleted).isEmpty();
        assertThat(fixture.gateway.discoveryCalls).isZero();
        verify(fixture.store).replaceMessages(eq(KEY), eq(TOKEN),
                eq(List.of(new RecordAnnouncementMessage(0, 100))));
    }

    @Test
    void partialReductionEditsTheStableFirstPageAndDeletesTheNoLongerRenderedPage() {
        Fixture fixture = fixture(RecordAnnouncementProjection.EDIT,
                List.of(new RecordAnnouncementMessage(0, 100), new RecordAnnouncementMessage(1, 200)), true);

        assertThat(fixture.coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);

        assertThat(fixture.gateway.edited).containsExactly(100L);
        assertThat(fixture.gateway.deleted).containsExactly(200L);
        assertThat(fixture.gateway.created).isEmpty();
        assertThat(fixture.gateway.discoveryCalls).isZero();
        verify(fixture.store).replaceMessages(eq(KEY), eq(TOKEN),
                eq(List.of(new RecordAnnouncementMessage(0, 100))));
    }

    @Test
    void adoptsAnUnknownCreateAfterRestartWithoutCreatingOrEditingItAgain() {
        Fixture fixture = fixture(RecordAnnouncementProjection.CREATE, List.of(), false, true, 2);
        fixture.gateway.discovered = List.of(new RecordAnnouncementMessageGateway.PublishedPage(100, 0));

        assertThat(fixture.coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);

        assertThat(fixture.gateway.created).isEmpty();
        assertThat(fixture.gateway.edited).isEmpty();
        verify(fixture.store).replaceMessages(eq(KEY), eq(TOKEN),
                eq(List.of(new RecordAnnouncementMessage(0, 100))));
    }

    @Test
    void disabledModeDeletesPersistedAndUnknownCreatePagesBeforeSuppressing() {
        Fixture fixture = fixture(
                RecordAnnouncementProjection.CREATE, List.of(new RecordAnnouncementMessage(0, 100)), false, false, 2);
        fixture.gateway.discovered = List.of(new RecordAnnouncementMessageGateway.PublishedPage(200, 0));

        assertThat(fixture.coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.SUPPRESSED);

        verify(fixture.store).claimNext(any(), eq(false));
        assertThat(fixture.gateway.created).isEmpty();
        assertThat(fixture.gateway.edited).isEmpty();
        assertThat(fixture.gateway.deleted).containsExactly(100L, 200L);
        verify(fixture.store).replaceMessages(eq(KEY), eq(TOKEN), eq(List.of()));
        verify(fixture.store).markSuppressed(eq(KEY), eq(TOKEN), any());
    }

    @Test
    void disabledModeStillEditsAnAlreadyPublishedAnnouncement() {
        Fixture fixture = fixture(
                RecordAnnouncementProjection.EDIT, List.of(new RecordAnnouncementMessage(0, 100)), true, false);

        assertThat(fixture.coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);

        verify(fixture.store).claimNext(any(), eq(false));
        assertThat(fixture.gateway.edited).containsExactly(100L);
        verify(fixture.store, never()).markSuppressed(any(), any(), any());
    }

    @Test
    void disabledModeStillDeletesAnAlreadyPublishedAnnouncement() {
        Fixture fixture = fixture(
                RecordAnnouncementProjection.DELETE, List.of(new RecordAnnouncementMessage(0, 100)), true, false);

        assertThat(fixture.coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);

        verify(fixture.store).claimNext(any(), eq(false));
        assertThat(fixture.gateway.deleted).containsExactly(100L);
        assertThat(fixture.gateway.discoveryCalls).isZero();
        verify(fixture.store).replaceMessages(eq(KEY), eq(TOKEN), eq(List.of()));
        verify(fixture.store, never()).markSuppressed(any(), any(), any());
    }

    @Test
    void synchronizedCreateReconciliationDoesNotEditAnUnchangedPublishedPage() {
        Fixture fixture = fixture(RecordAnnouncementProjection.CREATE, List.of(new RecordAnnouncementMessage(0, 100)), true);

        assertThat(fixture.coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);

        assertThat(fixture.gateway.edited).isEmpty();
        assertThat(fixture.gateway.created).isEmpty();
        assertThat(fixture.gateway.deleted).isEmpty();
        assertThat(fixture.gateway.existenceChecks).containsExactly(100L);
        assertThat(fixture.gateway.discoveryCalls).isZero();
    }

    @Test
    void marksAChangedPublishedProjectionAsExternallyRemovedWithItsOwnOutcome() {
        Fixture fixture = fixture(RecordAnnouncementProjection.NO_OP, List.of(new RecordAnnouncementMessage(0, 100)), true);
        fixture.gateway.existing = false;

        assertThat(fixture.coordinator.runNext())
                .isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.EXTERNALLY_REMOVED);

        verify(fixture.store).markExternallyRemoved(eq(KEY), eq(TOKEN), any());
        assertThat(fixture.gateway.created).isEmpty();
        assertThat(fixture.gateway.edited).isEmpty();
    }

    @Test
    void unknownTechnicalFailureEscapesUnchangedAndIsNotClassifiedAsAConflict() {
        Fixture fixture = fixture(RecordAnnouncementProjection.CREATE, List.of(), false, true, 2);
        IllegalStateException unknown = new IllegalStateException("database mapping exploded");
        fixture.gateway.createFailure = unknown;

        assertThatThrownBy(fixture.coordinator::runNext).isSameAs(unknown);

        verify(fixture.store, never()).markRetryableFailure(any(), any(), any(), any());
        verify(fixture.store, never()).markPermanentFailure(any(), any(), any(), any());
    }

    @Test
    void heartbeatFencesAWorkerThatLosesItsLeaseDuringBlockingIdBasedEdit() throws Exception {
        Fixture fixture = fixture(RecordAnnouncementProjection.EDIT, List.of(new RecordAnnouncementMessage(0, 100)), true);
        AtomicInteger renewals = new AtomicInteger();
        CountDownLatch heartbeatAttempted = new CountDownLatch(1);
        when(fixture.store.renewLease(eq(KEY), eq(TOKEN), any())).thenAnswer(invocation -> {
            if (renewals.incrementAndGet() >= 3) {
                heartbeatAttempted.countDown();
                return false;
            }
            return true;
        });
        fixture.gateway.beforeReturningEdit = heartbeatAttempted;

        assertThat(fixture.coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.LOST_LEASE);

        assertThat(heartbeatAttempted.await(1, TimeUnit.SECONDS)).isTrue();
        verify(fixture.store, never()).replaceMessages(any(), any(), any());
        verify(fixture.store, never()).markSynchronized(any(), any(), any());
        assertThat(fixture.gateway.edited).containsExactly(100L);
    }

    @Test
    void persistsTheConcreteRetryableGatewayCauseForOperations() {
        Fixture fixture = fixture(RecordAnnouncementProjection.EDIT,
                List.of(new RecordAnnouncementMessage(0, 100)), true);
        fixture.gateway.editFailure = new RecordAnnouncementMessageGateway.RetryableMessageException(
                "record announcement edit failed (discord_error=SERVER_ERROR)", null);
        when(fixture.store.markRetryableFailure(eq(KEY), eq(TOKEN), any(), any())).thenReturn(true);

        assertThat(fixture.coordinator.runNext())
                .isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.FAILED_RETRYABLE);

        var failure = org.mockito.ArgumentCaptor.forClass(
                de.venomenon.gridwordsbot.domain.record.RecordWorkFailure.class);
        verify(fixture.store).markRetryableFailure(eq(KEY), eq(TOKEN), failure.capture(), any());
        assertThat(failure.getValue().safeMessage())
                .isEqualTo("record announcement edit failed (discord_error=SERVER_ERROR)");
    }

    private Fixture fixture(
            RecordAnnouncementProjection projection, List<RecordAnnouncementMessage> messages, boolean published) {
        return fixture(projection, messages, published, true, 1);
    }

    private Fixture fixture(
            RecordAnnouncementProjection projection, List<RecordAnnouncementMessage> messages,
            boolean published, boolean publicAnnouncementsEnabled) {
        return fixture(projection, messages, published, publicAnnouncementsEnabled, 1);
    }

    private Fixture fixture(
            RecordAnnouncementProjection projection, List<RecordAnnouncementMessage> messages,
            boolean published, boolean publicAnnouncementsEnabled, int attemptCount) {
        RecordAnnouncementStore store = mock(RecordAnnouncementStore.class);
        RecordEventStore events = mock(RecordEventStore.class);
        PlayerStore players = mock(PlayerStore.class);
        Gateway gateway = new Gateway();
        RecordAnnouncementClaim claim = new RecordAnnouncementClaim(KEY, TOKEN, NOW.plusSeconds(60), attemptCount);
        RecordAnnouncementSnapshot snapshot = snapshot(projection, messages, published, attemptCount);
        when(store.claimNext(any(), any(Boolean.class))).thenReturn(Optional.of(claim));
        when(store.find(KEY)).thenReturn(Optional.of(snapshot));
        when(store.renewLease(eq(KEY), eq(TOKEN), any())).thenReturn(true);
        when(store.replaceMessages(eq(KEY), eq(TOKEN), any())).thenReturn(true);
        when(store.markDelivered(eq(KEY), eq(TOKEN), any())).thenReturn(true);
        when(store.markSynchronized(eq(KEY), eq(TOKEN), any())).thenReturn(true);
        when(store.markExternallyRemoved(eq(KEY), eq(TOKEN), any())).thenReturn(true);
        when(store.markSuppressed(eq(KEY), eq(TOKEN), any())).thenReturn(true);
        when(events.find(EVENT_ID)).thenReturn(Optional.of(event()));
        when(players.findAllPlayers()).thenReturn(List.of(new PlayerStore.StoredPlayer(
                7, "Ada", true, false, false, NOW, NOW)));
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executors.add(executor);
        RecordAnnouncementDeliveryCoordinator coordinator = new RecordAnnouncementDeliveryCoordinator(
                store, events, players, gateway, new RecordAnnouncementRenderer(), Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30), Duration.ofMillis(10), Duration.ofSeconds(1), Duration.ofSeconds(10),
                executor, publicAnnouncementsEnabled, (result, duration) -> { });
        return new Fixture(store, gateway, coordinator);
    }

    private static RecordAnnouncementSnapshot snapshot(
            RecordAnnouncementProjection projection, List<RecordAnnouncementMessage> messages,
            boolean published, int attemptCount) {
        return new RecordAnnouncementSnapshot(
                new RecordAnnouncementRegistration(KEY, RecordAnnouncementSubject.player(7),
                        RecordAnnouncementPhase.LIVE_EVALUATION, projection, RecordAnnouncementRenderer.VERSION,
                        "a".repeat(64), List.of(EVENT_ID)),
                RecordWorkState.CLAIMED, Optional.of(TOKEN), Optional.of(NOW.plusSeconds(60)), attemptCount,
                Optional.empty(), Optional.empty(), messages, published ? Optional.of(NOW) : Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), NOW, NOW);
    }

    private static RecordEventSnapshot event() {
        RecordSourceReference.GameResult source = new RecordSourceReference.GameResult(
                1, 1, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6));
        RecordEventDraft draft = new RecordEventDraft(EVENT_ID, "event:delivery", new RecordStateKey(1,
                new RecordDefinitionKey("result.gridwords.fastest-solution.personal"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(7)),
                RecordEventType.RESULT_RECORD_BROKEN, Optional.of(new DurationRecordValue(Duration.ofSeconds(90))),
                new DurationRecordValue(Duration.ofSeconds(74)), Optional.of(5L), Optional.of(7L),
                Optional.of(source), source, "record-live:1", RecordProcessingOrigin.LIVE_SUBMISSION, NOW);
        return new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(), NOW, NOW);
    }

    private record Fixture(
            RecordAnnouncementStore store, Gateway gateway, RecordAnnouncementDeliveryCoordinator coordinator) { }

    private static final class Gateway implements RecordAnnouncementMessageGateway {
        private final List<Long> created = new ArrayList<>();
        private final List<Long> edited = new ArrayList<>();
        private final List<Long> deleted = new ArrayList<>();
        private final List<Long> existenceChecks = new ArrayList<>();
        private List<PublishedPage> discovered = List.of();
        private CountDownLatch beforeReturningEdit;
        private RuntimeException createFailure;
        private RuntimeException editFailure;
        private boolean existing = true;
        private int discoveryCalls;

        @Override public long create(long channelId, RenderedRecordAnnouncementPage page) {
            if (createFailure != null) throw createFailure;
            created.add(999L);
            return 999L;
        }
        @Override public void edit(long channelId, long messageId, RenderedRecordAnnouncementPage page) {
            if (beforeReturningEdit != null) await(beforeReturningEdit);
            if (editFailure != null) throw editFailure;
            edited.add(messageId);
        }
        @Override public void delete(long channelId, long messageId) { deleted.add(messageId); }
        @Override public boolean exists(long channelId, long messageId) {
            existenceChecks.add(messageId);
            return existing;
        }
        @Override public List<PublishedPage> discoverCreatedPages(
                long channelId, String publicationKey, List<RenderedRecordAnnouncementPage> expectedPages) {
            discoveryCalls++;
            return discovered;
        }
        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("heartbeat did not run");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for heartbeat", exception);
            }
        }
    }
}
