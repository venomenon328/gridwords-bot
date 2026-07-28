package de.venomenon.gridwordsbot.port.out;

/** Signals an immutable submission or result-storage replay that contradicts persisted data. */
public final class SubmissionConflictException extends RuntimeException {
    public SubmissionConflictException(String message) {
        super(message);
    }
}