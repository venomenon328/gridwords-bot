package de.venomenon.gridwordsbot.domain.model;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** The complete, infrastructure-independent data extracted from one valid share. */
public record ParsedGameResult(
        GameType gameType,
        LocalDate gameDate,
        ShareOutcome outcome,
        Duration duration,
        OptionalInt gridgamesStreak,
        Optional<NormalizedBoard> board) {

    public ParsedGameResult {
        Objects.requireNonNull(gameType, "gameType must not be null");
        Objects.requireNonNull(gameDate, "gameDate must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(gridgamesStreak, "gridgamesStreak must not be null");
        Objects.requireNonNull(board, "board must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        if (gridgamesStreak.isPresent() && gridgamesStreak.getAsInt() <= 0) {
            throw new IllegalArgumentException("gridgamesStreak must be positive when present");
        }
        if (gameType == GameType.GRIDWORDS && board.isEmpty()) {
            throw new IllegalArgumentException("a GridWords result requires a board");
        }
        if (gameType == GameType.QUADWORDS && board.isPresent()) {
            throw new IllegalArgumentException("a QuadWords result must not contain a board in version 1");
        }
    }
}
