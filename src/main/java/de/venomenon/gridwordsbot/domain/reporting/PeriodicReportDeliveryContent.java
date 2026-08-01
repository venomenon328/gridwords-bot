package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** The immutable identity and expected visible size of a generated multi-page report. */
public record PeriodicReportDeliveryContent(String fingerprint, int expectedPageCount) {
    public PeriodicReportDeliveryContent {
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint must be a lowercase SHA-256 hex value");
        }
        if (expectedPageCount <= 0) {
            throw new IllegalArgumentException("expectedPageCount must be positive");
        }
    }
}
