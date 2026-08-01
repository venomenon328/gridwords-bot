package de.venomenon.gridwordsbot.domain.reporting;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Objects;

/** Immutable scheduling facts and the required reconciliation action for one weekly period. */
public record WeeklyReportReconciliationCandidate(
        ReportPeriod period,
        ReportDueAt dueAt,
        Instant catchUpEndsAt,
        WeeklyReportReconciliationAction action) {

    public WeeklyReportReconciliationCandidate {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(catchUpEndsAt, "catchUpEndsAt");
        Objects.requireNonNull(action, "action");
        if (period.startDate().getDayOfWeek() != DayOfWeek.MONDAY
                || !period.endDate().equals(period.startDate().plusDays(6))) {
            throw new IllegalArgumentException("weekly period must run from Monday through Sunday");
        }
        if (!dueAt.localDate().equals(period.endDate().plusDays(1))) {
            throw new IllegalArgumentException("weekly report must be due on the following Monday");
        }
        if (!catchUpEndsAt.equals(dueAt.instant().plus(ReportType.WEEKLY.catchUpDuration()))) {
            throw new IllegalArgumentException("catchUpEndsAt must use the weekly catch-up duration");
        }
    }
}
