package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;

/** Caller-controlled clock values make lease boundaries deterministic in PostgreSQL tests. */
public record RecordLeaseClaimRequest(Instant claimedAt, Instant leaseUntil) {
    public RecordLeaseClaimRequest {
        Objects.requireNonNull(claimedAt, "claimedAt");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (!leaseUntil.isAfter(claimedAt)) throw new IllegalArgumentException("leaseUntil must be after claimedAt");
    }
}
