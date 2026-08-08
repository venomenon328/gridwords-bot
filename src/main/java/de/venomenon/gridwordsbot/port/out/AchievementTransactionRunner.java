package de.venomenon.gridwordsbot.port.out;

import java.util.function.Supplier;

/** Infrastructure-owned transaction boundary for coupled achievement projection writes. */
public interface AchievementTransactionRunner {
    <T> T inTransaction(Supplier<T> work);

    /**
     * Runs achievement projection work serialized for one participant.
     *
     * <p>The default keeps non-database/test implementations source-compatible. Production persistence overrides
     * this with a database-backed participant lock so stale participant snapshots cannot overwrite newer
     * reconciliations.</p>
     */
    default <T> T inParticipantTransaction(long participantId, Supplier<T> work) {
        if (participantId <= 0) {
            throw new IllegalArgumentException("participantId must be positive");
        }
        return inTransaction(work);
    }
}
