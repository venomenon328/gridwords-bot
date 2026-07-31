package de.venomenon.gridwordsbot.application.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.out.DailyStatusMessageGateway;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyStatusRefreshServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T18:00:00Z"), ZoneId.of("UTC"));

    @Test
    void updateOnlyRefreshDoesNotCreateBeforeFirstResult() {
        RecordingStore store = new RecordingStore();
        DailyStatus status = status(false);
        service(store, status, (channel, id, value, changed) -> 99L).refreshExisting(DATE);
        assertThat(store.claims).isZero();
    }

    @Test
    void updateOnlyRefreshUpdatesExistingStatusWithoutResult() {
        RecordingStore store = new RecordingStore();
        store.statusExists = true;

        service(store, status(false), (channel, id, value, changed) -> 99L).refreshExisting(DATE);

        assertThat(store.claims).isOne();
        assertThat(store.completedMessage).contains(99L);
    }

    @Test
    void eventDrivenRefreshCreatesAndPersistsStatus() {
        RecordingStore store = new RecordingStore();
        service(store, status(true), (channel, id, value, changed) -> 99L).refresh(DATE);
        assertThat(store.claims).isOne();
        assertThat(store.completedMessage).contains(99L);
        assertThat(store.completedFingerprint).hasSize(64);
    }

    @Test
    void unchangedReconciliationChecksPresenceWithoutEditingContent() {
        RecordingStore store = new RecordingStore();
        DailyStatus status = status(true);
        store.previousFingerprint = Optional.of(DailyStatusRefreshService.fingerprint(status));
        boolean[] contentChanged = {true};

        service(store, status, (channel, id, value, changed) -> {
            contentChanged[0] = changed;
            return 99L;
        }).reconcile(DATE, true);

        assertThat(contentChanged[0]).isFalse();
        assertThat(store.completedMessage).contains(99L);
    }

    @Test
    void permanentDiscordFailureBecomesTerminal() {
        RecordingStore store = new RecordingStore();
        service(store, status(true), (channel, id, value, changed) -> {
            throw DiscordDeliveryException.permanent("missing permission", null);
        }).refresh(DATE);
        assertThat(store.failedPermanent).isTrue();
        assertThat(store.failedError).isEqualTo("missing permission");
    }

    @Test
    void unexpectedFailureIsRetryableAndDoesNotEscape() {
        RecordingStore store = new RecordingStore();
        service(store, status(true), (channel, id, value, changed) -> {
            throw new IllegalStateException("database details must not be persisted");
        }).refresh(DATE);
        assertThat(store.failedPermanent).isFalse();
        assertThat(store.failedError).isEqualTo("unexpected status delivery failure");
    }

    private static DailyStatusRefreshService service(RecordingStore store, DailyStatus status,
            DailyStatusMessageGateway gateway) {
        DailyStatusProjector projector = mock(DailyStatusProjector.class);
        when(projector.project(DATE, DATE)).thenReturn(status);
        return new DailyStatusRefreshService(projector, store, gateway, CLOCK, ZoneId.of("UTC"), 1L, 2L);
    }

    private static DailyStatus status(boolean withResult) {
        DailyStatus.PlayerLine player = new DailyStatus.PlayerLine(42L, "Player",
                Optional.empty(), Optional.empty(), new StreakSummary(1, 0, 1, 0, 0, 0, 0));
        if (!withResult) return new DailyStatus(DATE, List.of(player), 0, 0);
        de.venomenon.gridwordsbot.domain.model.ParsedGameResult result = new de.venomenon.gridwordsbot.domain.model.ParsedGameResult(
                de.venomenon.gridwordsbot.domain.model.GameType.GRIDWORDS, DATE,
                new de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved(1, 6), java.time.Duration.ofSeconds(1),
                java.util.OptionalInt.empty(), Optional.of(new de.venomenon.gridwordsbot.domain.model.NormalizedBoard(List.of("⬜⬜⬜⬜⬜"))));
        return new DailyStatus(DATE, List.of(new DailyStatus.PlayerLine(42L, "Player", Optional.of(result),
                Optional.empty(), player.streaks())), 0, 0);
    }

    private static final class RecordingStore implements DailyStatusStore {
        int claims;
        boolean statusExists;
        Optional<String> previousFingerprint = Optional.empty();
        Optional<Long> completedMessage = Optional.empty();
        String completedFingerprint;
        String failedError;
        boolean failedPermanent;

        @Override public Optional<StatusDelivery> claimStatus(long guildId, long channelId, LocalDate date,
                String fingerprint, boolean reconcile, Instant leaseUntil) {
            claims++;
            return Optional.of(new StatusDelivery(guildId, channelId, date, UUID.randomUUID(), Optional.empty(),
                    previousFingerprint, fingerprint));
        }
        @Override public void completeStatus(StatusDelivery claim, long messageId, String fingerprint) {
            completedMessage = Optional.of(messageId); completedFingerprint = fingerprint;
        }
        @Override public void failStatus(StatusDelivery claim, String safeError, boolean permanent) {
            failedError = safeError; failedPermanent = permanent;
        }
        @Override public boolean statusExists(long guildId, long channelId, LocalDate date) { return statusExists; }
        @Override public Optional<ReminderDelivery> claimReminder(long a, long b, LocalDate c, int d, LocalTime e, Instant f) { return Optional.empty(); }
        @Override public void completeReminder(ReminderDelivery a, ReminderState b, Optional<Long> c) { }
        @Override public void failReminder(ReminderDelivery a, String b, boolean c) { }
        @Override public void supersedeReminder(long a, long b, LocalDate c, int d, LocalTime e) { }
        @Override public void expireOpenRemindersBefore(long a, long b, LocalDate c) { }
    }
}
