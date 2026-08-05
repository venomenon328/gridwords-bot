package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;

/** Sanitized adapter failure information; it must never contain Discord payloads or secrets. */
public record RecordWorkFailure(RecordWorkFailureCategory category, String safeMessage) {
    public RecordWorkFailure {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(safeMessage, "safeMessage");
        if (safeMessage.isBlank() || safeMessage.length() > 512) {
            throw new IllegalArgumentException("safeMessage must be non-blank and at most 512 characters");
        }
    }
}
