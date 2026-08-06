package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;

/**
 * Targeted canonical read for one live result version.  It is deliberately
 * separate from the bootstrap history port: callers must not accidentally use
 * the bootstrap-wide scan on every submission.
 */
public interface RecordLiveHistoryQuery {
    RecordHistorySnapshot loadFor(RecordLiveEvaluationKey key);
}
