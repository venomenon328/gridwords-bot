package de.venomenon.gridwordsbot.domain.reporting;

import java.time.Instant;
import java.util.Objects;

/** Immutable scheduling facts persisted with a delivery, rather than recalculated during recovery. */
public record PeriodicReportDeliveryMetadata(
        ReportPeriod period, ReportDueAt dueAt, Instant catchUpEndsAt) {
    public PeriodicReportDeliveryMetadata {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(catchUpEndsAt, "catchUpEndsAt");
        if (!dueAt.localDate().isAfter(period.endDate())) {
            throw new IllegalArgumentException("report due date must be after the completed period");
        }
        if (!catchUpEndsAt.isAfter(dueAt.instant())) {
            throw new IllegalArgumentException("catchUpEndsAt must be after dueAt");
        }
    }
}
