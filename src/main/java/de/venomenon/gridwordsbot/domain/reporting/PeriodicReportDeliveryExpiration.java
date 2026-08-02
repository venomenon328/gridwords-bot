package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** Immutable facts for materializing a report delivery as expired without report content. */
public record PeriodicReportDeliveryExpiration(
        PeriodicReportDeliveryKey key,
        PeriodicReportDeliveryMetadata metadata) {
    public PeriodicReportDeliveryExpiration {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(metadata, "metadata");
        if (!key.periodStart().equals(metadata.period().startDate())) {
            throw new IllegalArgumentException("delivery key periodStart must match metadata period start");
        }
    }
}
