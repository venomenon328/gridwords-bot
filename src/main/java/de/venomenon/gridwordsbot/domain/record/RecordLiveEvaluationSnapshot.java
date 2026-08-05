package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable persisted state of one versioned live record evaluation. */
public record RecordLiveEvaluationSnapshot(
        RecordLiveEvaluationKey key,
        RecordProcessingOrigin processingOrigin,
        RecordLiveEvaluationState state,
        Optional<UUID> claimToken,
        Optional<Instant> claimUntil,
        Optional<Instant> startedAt,
        Optional<Instant> completedAt,
        int attemptCount,
        Optional<Instant> nextRetryAt,
        Optional<RecordWorkFailure> failure,
        Instant createdAt,
        Instant updatedAt) {
    public RecordLiveEvaluationSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(processingOrigin, "processingOrigin");
        Objects.requireNonNull(state, "state");
        claimToken = Objects.requireNonNull(claimToken, "claimToken");
        claimUntil = Objects.requireNonNull(claimUntil, "claimUntil");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        nextRetryAt = Objects.requireNonNull(nextRetryAt, "nextRetryAt");
        failure = Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (attemptCount < 0 || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("invalid live evaluation counters or timestamps");
        }
        if ((state == RecordLiveEvaluationState.CLAIMED)
                != (claimToken.isPresent() && claimUntil.isPresent())) {
            throw new IllegalArgumentException("invalid live evaluation claim");
        }
        if ((state == RecordLiveEvaluationState.RETRYABLE) != nextRetryAt.isPresent()) {
            throw new IllegalArgumentException("invalid live evaluation retry");
        }
        boolean terminal = state == RecordLiveEvaluationState.SUCCEEDED
                || state == RecordLiveEvaluationState.FAILED_PERMANENT
                || state == RecordLiveEvaluationState.SUPERSEDED;
        if (terminal != completedAt.isPresent()) {
            throw new IllegalArgumentException("invalid live evaluation completion");
        }
        if (state == RecordLiveEvaluationState.RETRYABLE) {
            if (failure.isEmpty() || failure.orElseThrow().category() == RecordWorkFailureCategory.PERMANENT) {
                throw new IllegalArgumentException("retryable live evaluation needs a non-permanent failure");
            }
        } else if (state == RecordLiveEvaluationState.FAILED_PERMANENT) {
            if (failure.isEmpty() || failure.orElseThrow().category() != RecordWorkFailureCategory.PERMANENT) {
                throw new IllegalArgumentException("permanent live evaluation needs a permanent failure");
            }
        } else if (failure.isPresent()) {
            throw new IllegalArgumentException("only failed live evaluations may retain a failure");
        }
    }
}
