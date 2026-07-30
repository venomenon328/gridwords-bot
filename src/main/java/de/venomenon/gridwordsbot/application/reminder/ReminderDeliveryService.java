package de.venomenon.gridwordsbot.application.reminder;

import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import de.venomenon.gridwordsbot.port.out.ReminderCandidateStore;
import de.venomenon.gridwordsbot.port.out.ReminderMessageGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** Idempotent delivery of one reminder stage; candidates are deliberately loaded after the claim. */
public final class ReminderDeliveryService {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private final DailyStatusStore store; private final ReminderCandidateStore candidates;
    private final ReminderMessageGateway messages; private final Clock clock; private final long guildId; private final long channelId;
    public ReminderDeliveryService(DailyStatusStore store, ReminderCandidateStore candidates, ReminderMessageGateway messages,
            Clock clock, long guildId, long channelId) {
        this.store = store; this.candidates = candidates; this.messages = messages; this.clock = clock; this.guildId = guildId; this.channelId = channelId;
    }
    public void deliver(LocalDate date, int stage, LocalTime time) {
        DailyStatusStore.ReminderDelivery claim = store.claimReminder(guildId, channelId, date, stage, time, clock.instant().plus(LEASE)).orElse(null);
        if (claim == null) return;
        try {
            List<ReminderCandidateStore.ReminderCandidate> selected = candidates.findReminderCandidates(date);
            if (selected.isEmpty()) { store.completeReminder(claim, DailyStatusStore.ReminderState.NO_CANDIDATES, java.util.Optional.empty()); return; }
            Set<Long> allowed = selected.stream().map(ReminderCandidateStore.ReminderCandidate::discordUserId).collect(java.util.stream.Collectors.toUnmodifiableSet());
            long messageId = messages.send(channelId, selected, allowed);
            store.completeReminder(claim, DailyStatusStore.ReminderState.SENT, java.util.Optional.of(messageId));
        } catch (RuntimeException exception) { store.failReminder(claim, "reminder delivery failed", false); }
    }
}
