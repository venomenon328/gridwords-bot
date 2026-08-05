package de.venomenon.gridwordsbot.port.out;

/** Explicit adapter/application translation for a validated non-retryable bootstrap failure. */
public final class RecordPermanentFailure extends RuntimeException {
    public RecordPermanentFailure(String message, Throwable cause) { super(message, cause); }
}
