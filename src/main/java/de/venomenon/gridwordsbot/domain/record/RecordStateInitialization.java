package de.venomenon.gridwordsbot.domain.record;

/** Result of unique-key guarded first initialization. */
public sealed interface RecordStateInitialization permits RecordStateInitialization.Created, RecordStateInitialization.Existing {
    RecordStateSnapshot snapshot();
    record Created(RecordStateSnapshot snapshot) implements RecordStateInitialization { }
    record Existing(RecordStateSnapshot snapshot) implements RecordStateInitialization { }
}
