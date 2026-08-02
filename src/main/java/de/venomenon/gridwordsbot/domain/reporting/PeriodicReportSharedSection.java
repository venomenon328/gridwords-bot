package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** The shared day and streak values of a transport-neutral periodic report. */
public record PeriodicReportSharedSection(ReportSharedDayCounts dayCounts, ReportSharedStreaks streaks) {
    public PeriodicReportSharedSection {
        Objects.requireNonNull(dayCounts, "dayCounts");
        Objects.requireNonNull(streaks, "streaks");
    }
}
