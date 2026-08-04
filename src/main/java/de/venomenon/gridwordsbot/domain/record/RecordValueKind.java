package de.venomenon.gridwordsbot.domain.record;

public enum RecordValueKind {
    ATTEMPTS_AND_DURATION(AttemptsDurationRecordValue.class),
    DURATION(DurationRecordValue.class),
    STREAK(StreakRecordValue.class);

    private final Class<? extends RecordValue> valueType;

    RecordValueKind(Class<? extends RecordValue> valueType) {
        this.valueType = valueType;
    }

    public Class<? extends RecordValue> valueType() {
        return valueType;
    }
}
