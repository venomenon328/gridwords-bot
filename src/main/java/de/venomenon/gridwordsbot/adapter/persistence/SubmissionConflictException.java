package de.venomenon.gridwordsbot.adapter.persistence;

/** Signals immutable source-message data that conflicts with an existing submission. */
public final class SubmissionConflictException extends RuntimeException {
    public SubmissionConflictException(String message) { super(message); }
}
