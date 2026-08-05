package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** A solved canonical result as the transport-neutral input of result-record evaluation. */
public record ResultRecordObservation(
        long resultId,
        long resultVersion,
        long playerId,
        GameType game,
        LocalDate gameDate,
        Instant firstAcceptedAt,
        ShareOutcome outcome,
        Duration duration) {

    public ResultRecordObservation {
        if (resultId <= 0) throw new IllegalArgumentException("resultId must be positive");
        if (resultVersion < 0) throw new IllegalArgumentException("resultVersion must not be negative");
        if (playerId <= 0) throw new IllegalArgumentException("playerId must be positive");
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(gameDate, "gameDate");
        Objects.requireNonNull(firstAcceptedAt, "firstAcceptedAt");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(duration, "duration");
        if (!(outcome instanceof ShareOutcome.Solved)) {
            throw new IllegalArgumentException("result-record observations must be solved");
        }
        int expectedMaximumAttempts = switch (game) {
            case GRIDWORDS -> 6;
            case QUADWORDS -> 9;
        };
        if (outcome.maxAttempts() != expectedMaximumAttempts) {
            throw new IllegalArgumentException("outcome maximum attempts do not match game");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    public int attemptsUsed() {
        return ((ShareOutcome.Solved) outcome).attemptsUsed();
    }

    public RecordSourceReference.GameResult sourceReference() {
        return new RecordSourceReference.GameResult(resultId, resultVersion, playerId, game, gameDate);
    }

    public RecordValue valueFor(ResultRecordMetric metric) {
        Objects.requireNonNull(metric, "metric");
        return switch (metric) {
            case FEWEST_ATTEMPTS -> new AttemptsDurationRecordValue(attemptsUsed(), duration);
            case FASTEST_SOLUTION, SLOWEST_SUCCESSFUL_SOLUTION -> new DurationRecordValue(duration);
        };
    }
}
