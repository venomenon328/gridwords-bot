package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;

/**
 * Targeted canonical read for one live result version.  It is deliberately
 * separate from the bootstrap history port: callers must not accidentally use
 * the bootstrap-wide scan on every submission.
 */
public interface RecordLiveHistoryQuery {
    RecordHistorySnapshot loadFor(RecordLiveEvaluationKey key);

    default RecordHistorySnapshot loadFor(RecordLiveEvaluationKey key, RecordProcessingOrigin origin) {
        return loadFor(key);
    }

    /**
     * Re-reads the bounded canonical input inside the short write boundary.
     * Implementations may replace this equality check with an equivalent
     * revision/generation query, but must cover every row used by
     * {@link #loadFor(RecordLiveEvaluationKey, RecordProcessingOrigin)}.
     */
    default boolean isCurrent(
            RecordLiveEvaluationKey key,
            RecordProcessingOrigin origin,
            RecordHistorySnapshot expected) {
        return loadFor(key, origin).equals(expected);
    }
}
