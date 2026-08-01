package de.venomenon.gridwordsbot.domain.reporting;

/** Final counts of report-period day features shared by all daily active participants. */
public record ReportSharedDayCounts(int sharedPossibleDays, int completeDays, int perfectDays) {
    public ReportSharedDayCounts {
        if (sharedPossibleDays < 0 || completeDays < 0 || perfectDays < 0
                || completeDays > sharedPossibleDays || perfectDays > completeDays) {
            throw new IllegalArgumentException("invalid shared day counts");
        }
    }
}
