package de.venomenon.gridwordsbot.application.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import de.venomenon.gridwordsbot.port.out.ReminderCandidateStore;
import de.venomenon.gridwordsbot.port.out.ReminderMessageGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReminderDeliveryServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);

    @Test
    void sendsExactlyCandidateUsersAndMissingGames() {
        RecordingStore store = new RecordingStore();
        List<ReminderCandidateStore.ReminderCandidate> selected = List.of(
                new ReminderCandidateStore.ReminderCandidate(42, "Name", List.of(GameType.GRIDWORDS)),
                new ReminderCandidateStore.ReminderCandidate(43, "Other", List.of(GameType.GRIDWORDS, GameType.QUADWORDS)));
        RecordingGateway gateway = new RecordingGateway();

        service(store, date -> selected, gateway).deliver(DATE, 1, LocalTime.of(18, 0));

        assertThat(gateway.allowed).containsExactlyInAnyOrder(42L, 43L);
        assertThat(gateway.candidates).isEqualTo(selected);
        assertThat(gateway.date).isEqualTo(DATE);
        assertThat(gateway.stage).isOne();
        assertThat(store.completed).isEqualTo(DailyStatusStore.ReminderState.SENT);
        assertThat(store.messageId).contains(99L);
    }

    @Test
    void persistsNoCandidatesWithoutDiscordCall() {
        RecordingStore store = new RecordingStore();
        RecordingGateway gateway = new RecordingGateway();
        service(store, date -> List.of(), gateway).deliver(DATE, 1, LocalTime.of(18, 0));
        assertThat(store.completed).isEqualTo(DailyStatusStore.ReminderState.NO_CANDIDATES);
        assertThat(gateway.calls).isZero();
    }

    @Test
    void secondAttemptReloadsCandidates() {
        RecordingStore store = new RecordingStore();
        int[] reads = {0};
        ReminderCandidateStore candidates = date -> ++reads[0] == 1
                ? List.of(new ReminderCandidateStore.ReminderCandidate(42, "Name", List.of(GameType.GRIDWORDS)))
                : List.of(new ReminderCandidateStore.ReminderCandidate(43, "Other", List.of(GameType.QUADWORDS)));
        RecordingGateway gateway = new RecordingGateway();
        ReminderDeliveryService service = service(store, candidates, gateway);

        service.deliver(DATE, 1, LocalTime.of(18, 0));
        service.deliver(DATE, 2, LocalTime.of(23, 0));

        assertThat(reads[0]).isEqualTo(2);
        assertThat(gateway.allowed).containsExactly(43L);
    }

    @Test
    void permanentFailureIsClassifiedWithoutThrowing() {
        RecordingStore store = new RecordingStore();
        ReminderMessageGateway gateway = (channel, date, stage, candidates, allowed) -> {
            throw DiscordDeliveryException.permanent("missing permission", null);
        };
        service(store, candidate(), gateway).deliver(DATE, 1, LocalTime.of(18, 0));
        assertThat(store.failedPermanent).isTrue();
        assertThat(store.failedError).isEqualTo("missing permission");
    }

    @Test
    void lostClaimPreventsAnyCandidateOrDiscordWork() {
        RecordingStore store = new RecordingStore();
        store.claimAvailable = false;
        RecordingGateway gateway = new RecordingGateway();
        int[] reads = {0};
        service(store, date -> { reads[0]++; return List.of(); }, gateway)
                .deliver(DATE, 1, LocalTime.of(18, 0));
        assertThat(reads[0]).isZero();
        assertThat(gateway.calls).isZero();
    }

    private static ReminderCandidateStore candidate() {
        return date -> List.of(new ReminderCandidateStore.ReminderCandidate(42, "Name", List.of(GameType.GRIDWORDS)));
    }

    private static ReminderDeliveryService service(RecordingStore store, ReminderCandidateStore candidates,
            ReminderMessageGateway gateway) {
        return new ReminderDeliveryService(store, candidates, gateway,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), 1, 2);
    }

    private static final class RecordingStore implements DailyStatusStore {
        boolean claimAvailable = true;
        ReminderState completed;
        Optional<Long> messageId = Optional.empty();
        String failedError;
        boolean failedPermanent;

        @Override public Optional<StatusDelivery> claimStatus(long a, long b, LocalDate c, String d, boolean e, Instant f) { return Optional.empty(); }
        @Override public void completeStatus(StatusDelivery a, long b, String c) { }
        @Override public void failStatus(StatusDelivery a, String b, boolean c) { }
        @Override public boolean statusExists(long a, long b, LocalDate c) { return false; }
        @Override public Optional<ReminderDelivery> claimReminder(long a, long b, LocalDate c, int d, LocalTime e, Instant f) {
            return claimAvailable ? Optional.of(new ReminderDelivery(a, b, c, d, e, UUID.randomUUID())) : Optional.empty();
        }
        @Override public void completeReminder(ReminderDelivery claim, ReminderState state, Optional<Long> id) {
            completed = state; messageId = id;
        }
        @Override public void failReminder(ReminderDelivery claim, String error, boolean permanent) {
            failedError = error; failedPermanent = permanent;
        }
        @Override public void supersedeReminder(long a, long b, LocalDate c, int d, LocalTime e) { }
        @Override public void expireOpenRemindersBefore(long a, long b, LocalDate c) { }
    }

    private static final class RecordingGateway implements ReminderMessageGateway {
        int calls;
        LocalDate date;
        int stage;
        List<ReminderCandidateStore.ReminderCandidate> candidates = List.of();
        Set<Long> allowed = Set.of();
        @Override public long send(long channel, LocalDate date, int stage,
                List<ReminderCandidateStore.ReminderCandidate> candidates, Set<Long> ids) {
            calls++; this.date = date; this.stage = stage; this.candidates = candidates; allowed = ids; return 99;
        }
    }
}