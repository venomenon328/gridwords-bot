package de.venomenon.gridwordsbot.domain.record;

/** Technische Optimistic-Lock-Version, bewusst getrennt von der fachlichen Definitionsversion. */
public record RecordLockVersion(long value) {
    public RecordLockVersion {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
    }

    public static RecordLockVersion initial() {
        return new RecordLockVersion(0);
    }

    public RecordLockVersion next() {
        if (value == Long.MAX_VALUE) {
            throw new IllegalStateException("record lock version overflow");
        }
        return new RecordLockVersion(value + 1);
    }
}
