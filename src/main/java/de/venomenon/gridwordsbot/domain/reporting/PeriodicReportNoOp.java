package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** A completed period without participation days and therefore without a report to deliver. */
public record PeriodicReportNoOp(ReportType reportType, ReportPeriod period) implements PeriodicReportResult {
    public PeriodicReportNoOp {
        Objects.requireNonNull(reportType, "reportType");
        Objects.requireNonNull(period, "period");
    }
}
