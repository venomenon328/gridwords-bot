package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;

/** Whether an idempotent append inserted a new fact or returned the already durable one. */
public record RecordEventAppendResult(boolean appended, RecordEventSnapshot snapshot) {
    public RecordEventAppendResult { Objects.requireNonNull(snapshot, "snapshot"); }
}
