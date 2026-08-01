package de.venomenon.gridwordsbot.domain.reporting;

import java.time.LocalDate;
import java.util.Objects;

/** An inclusive, completed calendar period used as the scope of a periodic report. */
public record ReportPeriod(LocalDate startDate, LocalDate endDate) {
    public ReportPeriod {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /** The explicit inclusive cutoff for report statistics and streaks. */
    public LocalDate statisticsAndStreakCutoff() {
        return endDate;
    }
}
