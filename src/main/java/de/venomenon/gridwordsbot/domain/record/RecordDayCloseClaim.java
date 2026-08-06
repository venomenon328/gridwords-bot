package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Claimed, token-fenced execution permission for one logical day close. */
public record RecordDayCloseClaim(RecordDayCloseKey key, UUID token, Instant leaseUntil, int attemptCount) {
    public RecordDayCloseClaim {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
    }
}
