package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable, transport-neutral fact to append to the record audit trail. */
public record RecordEventDraft(
        UUID eventId,
        String idempotencyKey,
        RecordStateKey stateKey,
        RecordEventType type,
        Optional<RecordValue> previousValue,
        RecordValue newValue,
        Optional<Long> previousHolderPlayerId,
        Optional<Long> newHolderPlayerId,
        Optional<RecordSourceReference> previousSource,
        RecordSourceReference newSource,
        String triggerKey,
        RecordProcessingOrigin processingOrigin,
        Instant detectedAt) {
    public RecordEventDraft {
        Objects.requireNonNull(eventId, "eventId");
        requireKey(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(stateKey, "stateKey");
        Objects.requireNonNull(type, "type");
        previousValue = Objects.requireNonNull(previousValue, "previousValue");
        Objects.requireNonNull(newValue, "newValue");
        previousHolderPlayerId = requirePlayer(previousHolderPlayerId, "previousHolderPlayerId");
        newHolderPlayerId = requirePlayer(newHolderPlayerId, "newHolderPlayerId");
        previousSource = Objects.requireNonNull(previousSource, "previousSource");
        Objects.requireNonNull(newSource, "newSource");
        requireKey(triggerKey, "triggerKey");
        Objects.requireNonNull(processingOrigin, "processingOrigin");
        Objects.requireNonNull(detectedAt, "detectedAt");
    }
    private static void requireKey(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 256) throw new IllegalArgumentException(name + " must be a non-blank stable key");
    }
    private static Optional<Long> requirePlayer(Optional<Long> value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(id -> { if (id <= 0) throw new IllegalArgumentException(name + " must be positive"); });
        return value;
    }
}
