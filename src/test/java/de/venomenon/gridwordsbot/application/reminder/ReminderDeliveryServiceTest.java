package de.venomenon.gridwordsbot.application.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
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
    @Test void sendsExactlyTheCandidateUsersAndPersistsSentDelivery() {
        RecordingStore store = new RecordingStore();
        ReminderCandidateStore candidates = date -> List.of(new ReminderCandidateStore.ReminderCandidate(42, "Name", List.of(GameType.GRIDWORDS)));
        RecordingGateway gateway = new RecordingGateway();
        service(store, candidates, gateway).deliver(LocalDate.of(2026, 7, 30), 1, LocalTime.of(18, 0));
        assertEquals(Set.of(42L), gateway.allowed);
        assertEquals(DailyStatusStore.ReminderState.SENT, store.completed);
        assertEquals(Optional.of(99L), store.messageId);
    }
    @Test void persistsNoCandidatesWithoutDiscordCall() {
        RecordingStore store = new RecordingStore(); RecordingGateway gateway = new RecordingGateway();
        service(store, date -> List.of(), gateway).deliver(LocalDate.of(2026, 7, 30), 1, LocalTime.of(18, 0));
        assertEquals(DailyStatusStore.ReminderState.NO_CANDIDATES, store.completed);
        assertTrue(gateway.allowed.isEmpty());
    }
    private static ReminderDeliveryService service(RecordingStore store, ReminderCandidateStore candidates, ReminderMessageGateway gateway) {
        return new ReminderDeliveryService(store, candidates, gateway, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), 1, 2);
    }
    private static final class RecordingStore implements DailyStatusStore {
        DailyStatusStore.ReminderState completed; Optional<Long> messageId;
        public Optional<StatusDelivery> claimStatus(long a,long b,LocalDate c,Instant d){return Optional.empty();} public void completeStatus(StatusDelivery a,long b,String c){} public void failStatus(StatusDelivery a,String b,boolean c){}
        public Optional<ReminderDelivery> claimReminder(long a,long b,LocalDate c,int d,LocalTime e,Instant f){return Optional.of(new ReminderDelivery(a,b,c,d,e,UUID.randomUUID()));}
        public void completeReminder(ReminderDelivery claim,ReminderState state,Optional<Long> id){completed=state;messageId=id;} public void failReminder(ReminderDelivery a,String b,boolean c){} public void expireOpenRemindersBefore(long a,long b,LocalDate c){}
    }
    private static final class RecordingGateway implements ReminderMessageGateway { Set<Long> allowed=Set.of(); public long send(long channel,List<ReminderCandidateStore.ReminderCandidate> candidates,Set<Long> ids){allowed=ids;return 99;} }
}
