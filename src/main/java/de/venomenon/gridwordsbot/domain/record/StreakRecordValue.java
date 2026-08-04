package de.venomenon.gridwordsbot.domain.record;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Inklusiver Kalendertageslauf mit seiner fachlichen Länge. */
public record StreakRecordValue(int length, LocalDate startDate, LocalDate endDate) implements RecordValue {
    public StreakRecordValue {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (inclusiveDays != length) {
            throw new IllegalArgumentException("length must match the inclusive calendar period");
        }
    }

    @Override
    public RecordValueKind kind() {
        return RecordValueKind.STREAK;
    }
}
