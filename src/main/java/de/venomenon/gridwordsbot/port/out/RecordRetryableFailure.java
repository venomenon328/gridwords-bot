package de.venomenon.gridwordsbot.port.out;

/** Explicit adapter translation for known transient record-store failures; unknown failures must escape. */
public final class RecordRetryableFailure extends RuntimeException {
    public RecordRetryableFailure(String message, Throwable cause) { super(message, cause); }
}
