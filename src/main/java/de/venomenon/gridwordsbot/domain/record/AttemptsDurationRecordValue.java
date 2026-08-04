package de.venomenon.gridwordsbot.domain.record;

import java.time.Duration;
import java.util.Objects;

/** Vergleichswert für „wenigste Versuche“ mit Dauer als Tie-Breaker. */
public record AttemptsDurationRecordValue(int attempts, Duration duration) implements RecordValue {
    public AttemptsDurationRecordValue {
        if (attempts <= 0) {
            throw new IllegalArgumentException("attempts must be positive");
        }
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    @Override
    public RecordValueKind kind() {
        return RecordValueKind.ATTEMPTS_AND_DURATION;
    }
}
