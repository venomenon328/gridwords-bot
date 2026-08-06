package de.venomenon.gridwordsbot.domain.record;

/** Persistent lifecycle of one versioned live record-evaluation job. */
public enum RecordLiveEvaluationState {
    OPEN,
    CLAIMED,
    RETRYABLE,
    SUCCEEDED,
    FAILED_PERMANENT,
    SUPERSEDED
}
