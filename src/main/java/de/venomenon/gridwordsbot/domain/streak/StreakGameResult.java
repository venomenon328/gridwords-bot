package de.venomenon.gridwordsbot.domain.streak;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.Objects;

/** Minimal canonical game-result fact required by streak day classification. */
public record StreakGameResult(long playerId, LocalDate gameDate, GameType game, boolean solved) {
    public StreakGameResult {
        if (playerId <= 0) throw new IllegalArgumentException("playerId must be positive");
        Objects.requireNonNull(gameDate, "gameDate");
        Objects.requireNonNull(game, "game");
    }
}
