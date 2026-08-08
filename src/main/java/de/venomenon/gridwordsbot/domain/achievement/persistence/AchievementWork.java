package de.venomenon.gridwordsbot.domain.achievement.persistence;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Kleine eigenständige Verträge für tokengebundene Bootstrap- und Delivery-Arbeit. */
public final class AchievementWork {
    private AchievementWork() {}

    public enum State { OPEN, CLAIMED, RETRYABLE, SUCCEEDED, FAILED_PERMANENT }
    public enum FailureCategory { RETRYABLE, PERMANENT, UNKNOWN }

    public record Failure(FailureCategory category, String safeError) {
        public Failure {
            Objects.requireNonNull(category, "category");
            safeError = AchievementAwardState.requireText(safeError, "safeError");
        }
    }

    public record LeaseClaimRequest(Instant claimedAt, Instant leaseUntil) {
        public LeaseClaimRequest {
            Objects.requireNonNull(claimedAt, "claimedAt");
            Objects.requireNonNull(leaseUntil, "leaseUntil");
            if (!leaseUntil.isAfter(claimedAt)) throw new IllegalArgumentException("leaseUntil must be after claimedAt");
        }
    }

    public record LeaseClaim(UUID token, Instant leaseUntil) {
        public LeaseClaim {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(leaseUntil, "leaseUntil");
        }
    }

    public record BootstrapKey(long guildId, AchievementDefinitionVersion definitionVersion) {
        public BootstrapKey {
            if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
            Objects.requireNonNull(definitionVersion, "definitionVersion");
        }
    }

    public record BootstrapSnapshot(
            BootstrapKey key,
            State state,
            Optional<UUID> claimToken,
            Optional<Instant> claimUntil,
            Optional<Instant> startedAt,
            Optional<Instant> completedAt,
            int attemptCount,
            Optional<Instant> nextRetryAt,
            Optional<Failure> failure,
            Instant createdAt,
            Instant updatedAt) {
        public BootstrapSnapshot {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(state, "state");
            claimToken = Objects.requireNonNull(claimToken, "claimToken");
            claimUntil = Objects.requireNonNull(claimUntil, "claimUntil");
            startedAt = Objects.requireNonNull(startedAt, "startedAt");
            completedAt = Objects.requireNonNull(completedAt, "completedAt");
            nextRetryAt = Objects.requireNonNull(nextRetryAt, "nextRetryAt");
            failure = Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (attemptCount < 0) throw new IllegalArgumentException("attemptCount must not be negative");
            if ((state == State.CLAIMED) != (claimToken.isPresent() && claimUntil.isPresent())) {
                throw new IllegalArgumentException("claim token and lease are required exactly for CLAIMED state");
            }
            if ((state == State.RETRYABLE) != nextRetryAt.isPresent()) {
                throw new IllegalArgumentException("nextRetryAt is required exactly for RETRYABLE state");
            }
            if ((state == State.SUCCEEDED || state == State.FAILED_PERMANENT) != completedAt.isPresent()) {
                throw new IllegalArgumentException("completedAt is required exactly for terminal state");
            }
            if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }
}
