package de.venomenon.gridwordsbot.port.out;

/** A reused event idempotency key must always describe the same immutable audit event. */
public final class RecordEventIdempotencyConflictException extends IllegalStateException {
    public RecordEventIdempotencyConflictException(String idempotencyKey) {
        super("conflicting record event idempotency key: " + idempotencyKey);
    }
}
