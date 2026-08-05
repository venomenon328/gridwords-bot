package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;

/** Explicit compare-and-set command for a materialized record state. */
public record RecordStateUpdate(RecordStateKey key, RecordLockVersion expectedLockVersion, RecordStateWrite write) {
    public RecordStateUpdate {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expectedLockVersion, "expectedLockVersion");
        Objects.requireNonNull(write, "write");
    }
}
