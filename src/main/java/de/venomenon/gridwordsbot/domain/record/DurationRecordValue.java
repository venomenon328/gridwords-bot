package de.venomenon.gridwordsbot.domain.record;

import java.time.Duration;
import java.util.Objects;

/** Vergleichswert für schnelle oder langsame erfolgreiche Lösungen. */
public record DurationRecordValue(Duration duration) implements RecordValue {
    public DurationRecordValue {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    @Override
    public RecordValueKind kind() {
        return RecordValueKind.DURATION;
    }
}
