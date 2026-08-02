package de.venomenon.gridwordsbot.domain.reporting;

import java.time.Instant;
import java.util.Objects;

/** The caller's time-bounded request for a new delivery claim. */
public record PeriodicReportDeliveryClaimRequest(Instant claimedAt, Instant leaseUntil) {
    public PeriodicReportDeliveryClaimRequest {
        Objects.requireNonNull(claimedAt, "claimedAt");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (!leaseUntil.isAfter(claimedAt)) {
            throw new IllegalArgumentException("leaseUntil must be after claimedAt");
        }
    }
}
