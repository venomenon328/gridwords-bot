package de.venomenon.gridwordsbot.domain.excuse;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/** Minimal valid-result facts used for a same-game, same-day comparison. */
public record ExcuseDailyResult(
        long playerId, GameType gameType, LocalDate gameDate, ShareOutcome outcome, Duration duration) {

    public ExcuseDailyResult {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId must be positive");
        }
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(gameDate, "gameDate");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }
}
