package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** The two explicitly named shared report streaks. */
public record ReportSharedStreaks(ReportStreakSnapshot complete, ReportStreakSnapshot perfect) {
    public ReportSharedStreaks {
        Objects.requireNonNull(complete, "complete");
        Objects.requireNonNull(perfect, "perfect");
    }
}
