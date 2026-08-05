package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Persisted projection and concurrency anchor; it is not a second source of result or streak truth. */
public record RecordStateSnapshot(
        RecordStateKey key,
        Optional<Long> holderPlayerId,
        RecordValue value,
        RecordSourceReference source,
        boolean running,
        RecordLockVersion lockVersion,
        Instant createdAt,
        Instant updatedAt) {
    public RecordStateSnapshot {
        Objects.requireNonNull(key, "key");
        holderPlayerId = requirePlayer(holderPlayerId, "holderPlayerId");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(lockVersion, "lockVersion");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        if (source instanceof RecordSourceReference.GameResult && (value instanceof StreakRecordValue || running)) {
            throw new IllegalArgumentException("game-result record state must use a completed result value");
        }
        if (source instanceof RecordSourceReference.StreakRun && !(value instanceof StreakRecordValue)) {
            throw new IllegalArgumentException("streak source must use a streak value");
        }
        switch (key.scope()) {
            case RecordScope.Personal personal -> {
                if (holderPlayerId.isEmpty() || holderPlayerId.get() != personal.playerId()) {
                    throw new IllegalArgumentException("personal record state holder must be its scoped player");
                }
            }
            case RecordScope.ServerIndividual ignored -> {
                if (holderPlayerId.isEmpty()) throw new IllegalArgumentException("server record state needs a holder");
            }
            case RecordScope.Shared ignored -> {
                if (holderPlayerId.isPresent()) throw new IllegalArgumentException("shared record state has no holder");
            }
        }
    }

    private static Optional<Long> requirePlayer(Optional<Long> value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(id -> { if (id <= 0) throw new IllegalArgumentException(name + " must be positive"); });
        return value;
    }
}
