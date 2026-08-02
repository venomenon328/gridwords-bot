package de.venomenon.gridwordsbot.application.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.ChannelMessageRetirementStore;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import de.venomenon.gridwordsbot.port.out.ReminderMessageGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelMessageRetirementServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T04:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);
    private static final String PUBLICATION_KEY = "gridwords-result-7";

    @Test
    void treatsUnknownCanonicalMessageAsRetiredAndKeepsRecoveryFence() {
        Fixture fixture = fixture();
        ChannelMessageRetirementStore.ResultMessage message = resultMessage(OptionalLong.of(99L));
        ChannelMessageRetirementStore.ResultRetirementClaim claim = resultClaim(7L);
        when(fixture.store.findResultMessagesBefore(11L, 12L, DATE.plusDays(1))).thenReturn(List.of(message));
        when(fixture.store.claimResultMessage(anyLong(), any())).thenReturn(Optional.of(claim));
        when(fixture.canonical.findAllByPublicationKey(12L, PUBLICATION_KEY)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new CanonicalMessageGateway.UnknownMessageException())
                .when(fixture.canonical).delete(12L, 99L);

        assertThat(fixture.service.retireResultMessagesBefore(DATE.plusDays(1))).isTrue();

        verify(fixture.store).completeResultRetirement(claim);
        verify(fixture.store, never()).failResultRetirement(any(), anyString(), anyBoolean());
    }

    @Test
    void retiresAmbiguousCanonicalDeliveryByPublicationKeyWithoutPersistedMessageId() {
        Fixture fixture = fixture();
        ChannelMessageRetirementStore.ResultMessage message = resultMessage(OptionalLong.empty());
        ChannelMessageRetirementStore.ResultRetirementClaim claim = resultClaim(7L);
        when(fixture.store.findResultMessagesBefore(11L, 12L, DATE.plusDays(1))).thenReturn(List.of(message));
        when(fixture.store.claimResultMessage(anyLong(), any())).thenReturn(Optional.of(claim));
        when(fixture.canonical.findAllByPublicationKey(12L, PUBLICATION_KEY)).thenReturn(List.of(101L, 102L));

        assertThat(fixture.service.retireResultMessagesBefore(DATE.plusDays(1))).isTrue();

        verify(fixture.canonical).delete(12L, 101L);
        verify(fixture.canonical).delete(12L, 102L);
        verify(fixture.store).completeResultRetirement(claim);
    }

    @Test
    void recordsRetryableAndPermanentFailuresWithoutRetiringTheMessage() {
        Fixture fixture = fixture();
        ChannelMessageRetirementStore.ResultMessage message = resultMessage(OptionalLong.of(99L));
        ChannelMessageRetirementStore.ResultRetirementClaim retryClaim = resultClaim(7L);
        when(fixture.store.findResultMessagesBefore(11L, 12L, DATE.plusDays(1))).thenReturn(List.of(message));
        when(fixture.store.claimResultMessage(anyLong(), any())).thenReturn(Optional.of(retryClaim));
        when(fixture.canonical.findAllByPublicationKey(12L, PUBLICATION_KEY)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(DiscordDeliveryException.retryable("temporary", null))
                .when(fixture.canonical).delete(12L, 99L);

        assertThat(fixture.service.retireResultMessagesBefore(DATE.plusDays(1))).isFalse();
        verify(fixture.store).failResultRetirement(retryClaim, "temporary", false);

        ChannelMessageRetirementStore.ResultRetirementClaim permanentClaim = resultClaim(7L);
        when(fixture.store.claimResultMessage(anyLong(), any())).thenReturn(Optional.of(permanentClaim));
        org.mockito.Mockito.doThrow(DiscordDeliveryException.permanent("forbidden", null))
                .when(fixture.canonical).delete(12L, 99L);

        assertThat(fixture.service.retireResultMessagesBefore(DATE.plusDays(1))).isFalse();
        verify(fixture.store).failResultRetirement(permanentClaim, "forbidden", true);
        verify(fixture.store, never()).completeResultRetirement(permanentClaim);
    }

    @Test
    void retiresAmbiguousReminderThroughItsStableDayAndStageKey() {
        Fixture fixture = fixture();
        ChannelMessageRetirementStore.ReminderMessage message =
                new ChannelMessageRetirementStore.ReminderMessage(11L, 12L, DATE, 1, OptionalLong.empty());
        ChannelMessageRetirementStore.ReminderRetirementClaim claim = new ChannelMessageRetirementStore
                .ReminderRetirementClaim(11L, 12L, DATE, 1, UUID.randomUUID(), NOW.plusSeconds(120));
        when(fixture.store.findFirstReminderMessagesReadyForRetirement(11L, 12L, DATE))
                .thenReturn(List.of(message));
        when(fixture.store.claimReminderMessage(anyLong(), anyLong(), any(), anyInt(), any()))
                .thenReturn(Optional.of(claim));

        assertThat(fixture.service.reconcileFirstReminderRetention(DATE)).isTrue();

        verify(fixture.reminders).delete(12L, DATE, 1, OptionalLong.empty());
        verify(fixture.store).completeReminderRetirement(claim);
    }

    @Test
    void doesNotDeleteWhenAnotherWorkerOwnsTheClaim() {
        Fixture fixture = fixture();
        ChannelMessageRetirementStore.ReminderMessage message =
                new ChannelMessageRetirementStore.ReminderMessage(11L, 12L, DATE, 1, OptionalLong.of(99L));
        when(fixture.store.findFirstReminderMessagesReadyForRetirement(11L, 12L, DATE))
                .thenReturn(List.of(message));
        when(fixture.store.claimReminderMessage(anyLong(), anyLong(), any(), anyInt(), any()))
                .thenReturn(Optional.empty());

        assertThat(fixture.service.reconcileFirstReminderRetention(DATE)).isFalse();

        verify(fixture.reminders, never()).delete(anyLong(), any(), anyInt(), any());
    }

    private static ChannelMessageRetirementStore.ResultMessage resultMessage(OptionalLong messageId) {
        return new ChannelMessageRetirementStore.ResultMessage(7L, 12L, messageId, DATE, PUBLICATION_KEY);
    }

    private static Fixture fixture() {
        ChannelMessageRetirementStore store = mock(ChannelMessageRetirementStore.class);
        CanonicalMessageGateway canonical = mock(CanonicalMessageGateway.class);
        ReminderMessageGateway reminders = mock(ReminderMessageGateway.class);
        return new Fixture(store, canonical, reminders, new ChannelMessageRetirementService(
                store, canonical, reminders, Clock.fixed(NOW, ZoneOffset.UTC), 11L, 12L));
    }

    private static ChannelMessageRetirementStore.ResultRetirementClaim resultClaim(long id) {
        return new ChannelMessageRetirementStore.ResultRetirementClaim(id, UUID.randomUUID(), NOW.plusSeconds(120));
    }

    private record Fixture(ChannelMessageRetirementStore store, CanonicalMessageGateway canonical,
                           ReminderMessageGateway reminders, ChannelMessageRetirementService service) {
    }
}
