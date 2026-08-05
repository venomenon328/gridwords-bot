package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Durable event snapshot; invalidation and supersession never delete its audit facts. */
public record RecordEventSnapshot(
        RecordEventDraft draft,
        RecordEventValidity validity,
        Optional<Instant> invalidatedAt,
        Optional<UUID> supersededBy,
        Instant createdAt,
        Instant updatedAt) {
    public RecordEventSnapshot {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(validity, "validity");
        invalidatedAt = Objects.requireNonNull(invalidatedAt, "invalidatedAt");
        supersededBy = Objects.requireNonNull(supersededBy, "supersededBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        boolean terminal = validity != RecordEventValidity.VALID;
        if (terminal != invalidatedAt.isPresent()) throw new IllegalArgumentException("event validity and invalidatedAt disagree");
        if ((validity == RecordEventValidity.SUPERSEDED) != supersededBy.isPresent()) {
            throw new IllegalArgumentException("event validity and supersededBy disagree");
        }
    }
}
