package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;
import java.util.Optional;

/** Immutable facts used to register one delivery before any external message I/O occurs. */
public record PeriodicReportDeliveryRegistration(
        PeriodicReportDeliveryKey key,
        PeriodicReportDeliveryMetadata metadata,
        Optional<PeriodicReportDeliveryContent> content) {
    public PeriodicReportDeliveryRegistration {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(metadata, "metadata");
        content = Objects.requireNonNull(content, "content");
        if (!key.periodStart().equals(metadata.period().startDate())) {
            throw new IllegalArgumentException("delivery key periodStart must match metadata period start");
        }
    }
}
