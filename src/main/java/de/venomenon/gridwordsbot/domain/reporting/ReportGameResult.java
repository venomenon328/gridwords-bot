package de.venomenon.gridwordsbot.domain.reporting;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/** Transport-neutral result facts that are sufficient for game-specific report statistics. */
public record ReportGameResult(long playerId, GameType gameType, LocalDate gameDate, ShareOutcome outcome, Duration duration) {
    public ReportGameResult {
        if (playerId <= 0) throw new IllegalArgumentException("playerId must be positive");
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(gameDate, "gameDate");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) throw new IllegalArgumentException("duration must not be negative");
    }
}
