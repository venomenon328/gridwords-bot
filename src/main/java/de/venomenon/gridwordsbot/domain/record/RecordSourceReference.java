package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.Objects;

/** Transportneutrale Referenz auf das kanonische Ergebnis oder den abgeleiteten Serienlauf. */
public sealed interface RecordSourceReference
        permits RecordSourceReference.GameResult, RecordSourceReference.StreakRun {
    RecordSourceType sourceType();

    record GameResult(long resultId, long resultVersion, long playerId, GameType game, LocalDate gameDate)
            implements RecordSourceReference {
        public GameResult {
            if (resultId <= 0) throw new IllegalArgumentException("resultId must be positive");
            if (resultVersion < 0) throw new IllegalArgumentException("resultVersion must not be negative");
            if (playerId <= 0) throw new IllegalArgumentException("playerId must be positive");
            Objects.requireNonNull(game, "game");
            Objects.requireNonNull(gameDate, "gameDate");
        }

        @Override
        public RecordSourceType sourceType() {
            return RecordSourceType.GAME_RESULT;
        }
    }

    record StreakRun(StreakRecordMetric metric, StreakRunOwner owner, LocalDate startDate)
            implements RecordSourceReference {
        public StreakRun {
            Objects.requireNonNull(metric, "metric");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(startDate, "startDate");
            if (owner instanceof StreakRunOwner.Shared && !metric.sharedScopeAllowed()) {
                throw new IllegalArgumentException("metric does not allow a shared streak source");
            }
        }

        @Override
        public RecordSourceType sourceType() {
            return RecordSourceType.STREAK_RUN;
        }
    }

    sealed interface StreakRunOwner permits StreakRunOwner.Player, StreakRunOwner.Shared {
        record Player(long playerId) implements StreakRunOwner {
            public Player {
                if (playerId <= 0) throw new IllegalArgumentException("playerId must be positive");
            }
        }

        record Shared() implements StreakRunOwner {
        }
    }
}
