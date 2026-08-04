package de.venomenon.gridwordsbot.domain.record;

/** Geschlossene Menge typisierter Vergleichswerte des Rekordkatalogs. */
public sealed interface RecordValue permits AttemptsDurationRecordValue, DurationRecordValue, StreakRecordValue {
    RecordValueKind kind();
}
