package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Mutable state payload without technical version or adapter-owned timestamps. */
public record RecordStateWrite(
        Optional<Long> holderPlayerId,
        RecordValue value,
        RecordSourceReference source,
        Optional<Instant> sourceGameFirstAcceptedAt,
        boolean running) {

    public RecordStateWrite(Optional<Long> holderPlayerId, RecordValue value,
            RecordSourceReference source, boolean running) {
        this(holderPlayerId, value, source, Optional.empty(), running);
    }

    public RecordStateWrite {
        Objects.requireNonNull(holderPlayerId, "holderPlayerId");
        holderPlayerId.ifPresent(id -> {
            if (id <= 0) throw new IllegalArgumentException("holderPlayerId must be positive");
        });
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceGameFirstAcceptedAt, "sourceGameFirstAcceptedAt");
        if (source instanceof RecordSourceReference.GameResult
                && (value instanceof StreakRecordValue || running)) {
            throw new IllegalArgumentException("game-result record state must use a completed result value");
        }
        if (source instanceof RecordSourceReference.StreakRun) {
            if (!(value instanceof StreakRecordValue)) {
                throw new IllegalArgumentException("streak source must use a streak value");
            }
            if (sourceGameFirstAcceptedAt.isPresent()) {
                throw new IllegalArgumentException("streak source must not use a game-result acceptance timestamp");
            }
        }
    }
}
