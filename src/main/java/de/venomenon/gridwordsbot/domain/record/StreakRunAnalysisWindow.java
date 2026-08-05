package de.venomenon.gridwordsbot.domain.record;

import java.time.LocalDate;
import java.util.Objects;

/** Inclusive calendar window and explicit logical close state of its final day. */
public record StreakRunAnalysisWindow(LocalDate firstDate, LocalDate asOfDate, LocalDate firstOpenDate) {
    public StreakRunAnalysisWindow {
        Objects.requireNonNull(firstDate, "firstDate");
        Objects.requireNonNull(asOfDate, "asOfDate");
        if (firstDate.isAfter(asOfDate)) throw new IllegalArgumentException("firstDate must not be after asOfDate");
        Objects.requireNonNull(firstOpenDate, "firstOpenDate");
    }
    public StreakRunAnalysisWindow(LocalDate firstDate, LocalDate asOfDate, boolean asOfDateClosed) {
        this(firstDate, asOfDate, asOfDateClosed ? asOfDate.plusDays(1) : asOfDate);
    }
    public boolean asOfDateClosed() { return asOfDate.isBefore(firstOpenDate); }

    public boolean dayClosed(LocalDate day) {
        Objects.requireNonNull(day, "day");
        if (day.isBefore(firstDate) || day.isAfter(asOfDate)) {
            throw new IllegalArgumentException("day must be inside the analysis window");
        }
        return day.isBefore(firstOpenDate);
    }
}
