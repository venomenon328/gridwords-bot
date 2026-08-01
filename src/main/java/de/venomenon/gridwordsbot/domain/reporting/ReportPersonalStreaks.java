package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** The five explicitly named personal report streaks. */
public record ReportPersonalStreaks(
        ReportStreakSnapshot activity,
        ReportStreakSnapshot complete,
        ReportStreakSnapshot gridWordsSolved,
        ReportStreakSnapshot quadWordsSolved,
        ReportStreakSnapshot perfect) {
    public ReportPersonalStreaks {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(complete, "complete");
        Objects.requireNonNull(gridWordsSolved, "gridWordsSolved");
        Objects.requireNonNull(quadWordsSolved, "quadWordsSolved");
        Objects.requireNonNull(perfect, "perfect");
    }
}
