package de.venomenon.gridwordsbot.domain.reporting;

/** A final current streak at the report cutoff and its record through that same cutoff. */
public record ReportStreakSnapshot(int currentAtPeriodEnd, int allTimeRecordThroughPeriodEnd) {
    public ReportStreakSnapshot {
        if (currentAtPeriodEnd < 0 || allTimeRecordThroughPeriodEnd < 0
                || currentAtPeriodEnd > allTimeRecordThroughPeriodEnd) {
            throw new IllegalArgumentException("invalid report streak snapshot");
        }
    }
}
