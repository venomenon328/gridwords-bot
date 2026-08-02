package de.venomenon.gridwordsbot.domain.reporting;

/** Final counts of a participant's report-period day features. */
public record ReportPersonalDayCounts(int participationDays, int activityDays, int completeDays, int perfectDays) {
    public ReportPersonalDayCounts {
        if (participationDays < 0 || activityDays < 0 || completeDays < 0 || perfectDays < 0
                || activityDays > participationDays || completeDays > activityDays || perfectDays > completeDays) {
            throw new IllegalArgumentException("invalid personal day counts");
        }
    }
}
