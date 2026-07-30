package de.venomenon.gridwordsbot.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Sends an aggregated reminder with an explicit, closed user-mention allow-list. */
public interface ReminderMessageGateway {
    long send(long channelId, LocalDate gameDate, int stage,
              List<ReminderCandidateStore.ReminderCandidate> candidates, Set<Long> allowedUserIds);
}