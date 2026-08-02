package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** The exact persistent scope used to find deliveries of one report type. */
public record PeriodicReportDeliveryScope(long guildId, long channelId, ReportType reportType) {
    public PeriodicReportDeliveryScope {
        if (guildId <= 0 || channelId <= 0) {
            throw new IllegalArgumentException("guildId and channelId must be positive");
        }
        Objects.requireNonNull(reportType, "reportType");
    }
}
