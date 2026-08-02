package de.venomenon.gridwordsbot.domain.reporting;

import java.time.Instant;
import java.util.Objects;

/** Immutable scheduling facts and the required reconciliation action for one periodic report period. */
public record PeriodicReportReconciliationCandidate(
        ReportType type,
        ReportPeriod period,
        ReportDueAt dueAt,
        Instant catchUpEndsAt,
        PeriodicReportReconciliationAction action) {

    public PeriodicReportReconciliationCandidate {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(catchUpEndsAt, "catchUpEndsAt");
        Objects.requireNonNull(action, "action");
        if (!period.equals(type.periodStartingOn(period.startDate()))) {
            throw new IllegalArgumentException("period must match its report type");
        }
        if (!dueAt.localDate().equals(period.endDate().plusDays(1))) {
            throw new IllegalArgumentException("report must be due on the day after its period ends");
        }
        if (!catchUpEndsAt.equals(dueAt.instant().plus(type.catchUpDuration()))) {
            throw new IllegalArgumentException("catchUpEndsAt must use the report type catch-up duration");
        }
    }
}
