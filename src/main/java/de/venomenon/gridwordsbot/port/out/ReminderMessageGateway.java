package de.venomenon.gridwordsbot.port.out;

import java.util.List;
import java.util.Set;

/** Sends an aggregated reminder with an explicit, closed user-mention allow-list. */
public interface ReminderMessageGateway {
    long send(long channelId, List<ReminderCandidateStore.ReminderCandidate> candidates, Set<Long> allowedUserIds);
}
