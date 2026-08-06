package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Token-owned lease for one versioned live record evaluation. */
public record RecordLiveEvaluationClaim(
        RecordLiveEvaluationKey key,
        RecordProcessingOrigin processingOrigin,
        UUID token,
        Instant leaseUntil,
        int attemptCount) {
    public RecordLiveEvaluationClaim {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(processingOrigin, "processingOrigin");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
    }
}
