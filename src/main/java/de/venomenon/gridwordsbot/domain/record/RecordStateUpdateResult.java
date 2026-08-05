package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;
import java.util.Optional;

/** Makes updated, unchanged, and stale-version outcomes distinguishable to the next package. */
public record RecordStateUpdateResult(Status status, Optional<RecordStateSnapshot> snapshot) {
    public enum Status { UPDATED, UNCHANGED, VERSION_CONFLICT }
    public RecordStateUpdateResult {
        Objects.requireNonNull(status, "status");
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if ((status == Status.UPDATED || status == Status.UNCHANGED) != snapshot.isPresent()) {
            throw new IllegalArgumentException("only successful state writes carry a snapshot");
        }
    }
}
