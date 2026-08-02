package de.venomenon.gridwordsbot.domain.reporting;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An opaque token which fences all durable progress for a short-lived delivery lease. */
public record PeriodicReportDeliveryClaim(UUID token, Instant leaseUntil) {
    private static final UUID EMPTY_TOKEN = new UUID(0L, 0L);

    public PeriodicReportDeliveryClaim {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (EMPTY_TOKEN.equals(token)) {
            throw new IllegalArgumentException("claim token must not be empty");
        }
    }
}
