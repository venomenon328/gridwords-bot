package de.venomenon.gridwordsbot.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/** Sends an aggregated reminder with an explicit, closed user-mention allow-list. */
public interface ReminderMessageGateway {
    long send(long channelId, LocalDate gameDate, int stage,
              List<ReminderCandidateStore.ReminderCandidate> candidates, Set<Long> allowedUserIds);

    /**
     * Deletes every visible reminder matching the stable day/stage key. A persisted message ID is an optional
     * fast-path; missing Discord messages are an idempotent success.
     */
    default void delete(long channelId, LocalDate gameDate, int stage, OptionalLong messageId) {
        throw new UnsupportedOperationException("reminder deletion is not available");
    }
}
