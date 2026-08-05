package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RecordLeaseClaim(UUID token, Instant leaseUntil) {
    public RecordLeaseClaim { Objects.requireNonNull(token, "token"); Objects.requireNonNull(leaseUntil, "leaseUntil"); }
}
