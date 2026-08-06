package de.venomenon.gridwordsbot.application.record;

import java.time.Duration;

/** Low-cardinality observation boundary for one claimed live-evaluation execution. */
public interface RecordLiveEvaluationMetrics {
    void record(RecordLiveEvaluationCoordinator.RunResult result, Duration duration);
}
