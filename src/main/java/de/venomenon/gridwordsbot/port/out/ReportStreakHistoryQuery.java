package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.reporting.ReportStreakHistory;
import java.time.LocalDate;
import java.util.Objects;

/** Bounded read boundary for all historical inputs needed by report streaks. */
public interface ReportStreakHistoryQuery {
    ReportStreakHistory findThrough(LocalDate inclusiveCutoff);

    default ReportStreakHistory requireThrough(LocalDate inclusiveCutoff) {
        return findThrough(Objects.requireNonNull(inclusiveCutoff, "inclusiveCutoff"));
    }
}
