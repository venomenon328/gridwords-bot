package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** A bounded, single-line diagnostic suitable for durable state and logs; it must never contain external payloads. */
public record PeriodicReportDeliveryFailure(PeriodicReportDeliveryFailureCategory category, String safeMessage) {
    public static final int MAX_SAFE_MESSAGE_LENGTH = 512;

    public PeriodicReportDeliveryFailure {
        Objects.requireNonNull(category, "category");
        safeMessage = Objects.requireNonNull(safeMessage, "safeMessage");
        if (safeMessage.isBlank() || safeMessage.length() > MAX_SAFE_MESSAGE_LENGTH
                || safeMessage.indexOf('\n') >= 0 || safeMessage.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("safeMessage must be a non-blank, single-line bounded value");
        }
    }
}
