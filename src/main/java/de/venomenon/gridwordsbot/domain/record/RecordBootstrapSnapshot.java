package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RecordBootstrapSnapshot(
        RecordBootstrapKey key,
        RecordWorkState state,
        Optional<UUID> claimToken,
        Optional<Instant> claimUntil,
        Optional<Instant> startedAt,
        Optional<Instant> completedAt,
        int attemptCount,
        Optional<Instant> nextRetryAt,
        Optional<RecordWorkFailure> failure,
        Instant createdAt,
        Instant updatedAt) {
    public RecordBootstrapSnapshot {
        Objects.requireNonNull(key, "key"); Objects.requireNonNull(state, "state");
        claimToken = Objects.requireNonNull(claimToken, "claimToken"); claimUntil = Objects.requireNonNull(claimUntil, "claimUntil");
        startedAt = Objects.requireNonNull(startedAt, "startedAt"); completedAt = Objects.requireNonNull(completedAt, "completedAt");
        nextRetryAt = Objects.requireNonNull(nextRetryAt, "nextRetryAt"); failure = Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(createdAt, "createdAt"); Objects.requireNonNull(updatedAt, "updatedAt");
        if (attemptCount < 0 || updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("invalid bootstrap counters or timestamps");
        if ((state == RecordWorkState.CLAIMED) != (claimToken.isPresent() && claimUntil.isPresent())) throw new IllegalArgumentException("invalid bootstrap claim");
        if ((state == RecordWorkState.RETRYABLE) != nextRetryAt.isPresent()) throw new IllegalArgumentException("invalid bootstrap retry");
        if ((state == RecordWorkState.SUCCEEDED || state == RecordWorkState.FAILED_PERMANENT) != completedAt.isPresent()) throw new IllegalArgumentException("invalid bootstrap completion");
    }
}
