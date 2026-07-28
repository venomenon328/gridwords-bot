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
        validateGameSpecificInvariants(gameType, outcome, board);
    }

    private static void validateGameSpecificInvariants(
            GameType gameType, ShareOutcome outcome, Optional<NormalizedBoard> board) {
        switch (gameType) {
            case GRIDWORDS -> validateGridWordsResult(outcome, board);
            case QUADWORDS -> {
                if (outcome.maxAttempts() != 9) {
                    throw new IllegalArgumentException("a QuadWords result must use 9 maximum attempts");
                }
                if (board.isPresent()) {
                    throw new IllegalArgumentException("a QuadWords result must not contain a board in version 1");
                }
            }
        }
    }

    private static void validateGridWordsResult(ShareOutcome outcome, Optional<NormalizedBoard> board) {
        if (outcome.maxAttempts() != 6) {
            throw new IllegalArgumentException("a GridWords result must use 6 maximum attempts");
        }
        NormalizedBoard gridWordsBoard = board.orElseThrow(
                () -> new IllegalArgumentException("a GridWords result requires a board"));
        int expectedBoardRows = outcome instanceof ShareOutcome.Solved solved
                ? solved.attemptsUsed()
                : 6;
        if (gridWordsBoard.rows().size() != expectedBoardRows) {
            throw new IllegalArgumentException("the GridWords board row count does not match the outcome");
        }
    }
}
