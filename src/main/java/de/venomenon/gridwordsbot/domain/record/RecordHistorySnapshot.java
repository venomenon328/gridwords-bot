package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Complete, immutable and deterministically ordered canonical history of one guild. */
public record RecordHistorySnapshot(List<Result> results, List<GameParticipationPeriod> participationPeriods) {
    public RecordHistorySnapshot {
        results = List.copyOf(Objects.requireNonNull(results, "results")).stream()
                .sorted(Comparator.comparing(Result::gameDate).thenComparing(Result::firstAcceptedAt)
                        .thenComparingLong(Result::resultId)).toList();
        participationPeriods = List.copyOf(Objects.requireNonNull(participationPeriods, "participationPeriods")).stream()
                .sorted(Comparator.comparingLong(GameParticipationPeriod::playerId)
                        .thenComparing(GameParticipationPeriod::gameType)
                        .thenComparing(GameParticipationPeriod::activeFrom)).toList();
    }

    public record Result(long resultId, long resultVersion, long playerId, GameType game, LocalDate gameDate,
            ShareOutcome outcome, Duration duration, Instant firstAcceptedAt) {
        public Result {
            if (resultId <= 0 || resultVersion < 0 || playerId <= 0) throw new IllegalArgumentException("invalid result identity");
            Objects.requireNonNull(game, "game"); Objects.requireNonNull(gameDate, "gameDate");
            Objects.requireNonNull(outcome, "outcome"); Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(firstAcceptedAt, "firstAcceptedAt");
            if (duration.isNegative()) throw new IllegalArgumentException("duration must not be negative");
        }
        public ResultRecordObservation solvedObservation() {
            if (!(outcome instanceof ShareOutcome.Solved)) throw new IllegalStateException("result is not solved");
            return new ResultRecordObservation(resultId, resultVersion, playerId, game, gameDate, firstAcceptedAt, outcome, duration);
        }
        public de.venomenon.gridwordsbot.domain.streak.StreakGameResult streakResult() {
            return new de.venomenon.gridwordsbot.domain.streak.StreakGameResult(playerId, gameDate, game,
                    outcome instanceof ShareOutcome.Solved);
        }
    }
}
