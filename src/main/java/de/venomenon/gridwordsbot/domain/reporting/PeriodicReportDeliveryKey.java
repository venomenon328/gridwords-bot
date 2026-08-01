package de.venomenon.gridwordsbot.domain.reporting;

import java.time.LocalDate;
import java.util.Objects;

/** The stable business identity of one logical periodic report delivery. */
public record PeriodicReportDeliveryKey(
        long guildId, long channelId, ReportType reportType, LocalDate periodStart) {
    public PeriodicReportDeliveryKey {
        if (guildId <= 0 || channelId <= 0) {
            throw new IllegalArgumentException("guildId and channelId must be positive");
        }
        Objects.requireNonNull(reportType, "reportType");
        Objects.requireNonNull(periodStart, "periodStart");
    }
}
