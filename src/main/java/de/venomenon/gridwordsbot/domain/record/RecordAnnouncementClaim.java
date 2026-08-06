package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Token-fenced permission to deliver exactly one logical record announcement. */
public record RecordAnnouncementClaim(RecordAnnouncementKey key, UUID token, Instant leaseUntil, int attemptCount) {
    public RecordAnnouncementClaim {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (attemptCount <= 0) throw new IllegalArgumentException("attemptCount must be positive");
    }
}
