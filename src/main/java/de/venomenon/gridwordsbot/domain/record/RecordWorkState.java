package de.venomenon.gridwordsbot.domain.record;

/** Shared persistence state vocabulary for bootstrap and outbound announcement work. */
public enum RecordWorkState {
    OPEN, CLAIMED, RETRYABLE, SUCCEEDED, SYNCHRONIZED, FAILED_PERMANENT, EXTERNALLY_REMOVED, SUPPRESSED
}
