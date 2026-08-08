package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import java.util.Objects;
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

    /**
     * Serializes the decision whether a normal trigger belongs to the historical bootstrap or to live handling.
     *
     * <p>Production persistence locks the existing bootstrap row. Keeping this as a small dedicated operation
     * prevents a check-then-act race at the {@code SUCCEEDED} edge without coupling Achievement work to a
     * process-local mutex.</p>
     */
    default <T> T inBootstrapFenceTransaction(AchievementWork.BootstrapKey key, Supplier<T> work) {
        Objects.requireNonNull(key, "key");
        return inTransaction(work);
    }
}
