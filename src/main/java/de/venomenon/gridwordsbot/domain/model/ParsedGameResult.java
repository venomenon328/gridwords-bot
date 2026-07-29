package de.venomenon.gridwordsbot.domain.model;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** The complete, infrastructure-independent data extracted from one valid share. */
public record ParsedGameResult(GameType gameType, LocalDate gameDate, ShareOutcome outcome, Duration duration,
        OptionalInt gridgamesStreak, Optional<NormalizedBoard> board, Optional<QuadWordsBoards> quadWordsBoards) {
    public ParsedGameResult(GameType gameType, LocalDate gameDate, ShareOutcome outcome, Duration duration,
            OptionalInt gridgamesStreak, Optional<NormalizedBoard> board) {
        this(gameType, gameDate, outcome, duration, gridgamesStreak, board, Optional.empty());
    }
    public ParsedGameResult {
        Objects.requireNonNull(gameType); Objects.requireNonNull(gameDate); Objects.requireNonNull(outcome);
        Objects.requireNonNull(duration); Objects.requireNonNull(gridgamesStreak); Objects.requireNonNull(board); Objects.requireNonNull(quadWordsBoards);
        if (duration.isNegative()) throw new IllegalArgumentException("duration must not be negative");
        if (gridgamesStreak.isPresent() && gridgamesStreak.getAsInt() <= 0) throw new IllegalArgumentException("gridgamesStreak must be positive when present");
        if (gameType == GameType.GRIDWORDS) {
            if (outcome.maxAttempts() != 6) throw new IllegalArgumentException("a GridWords result must use 6 maximum attempts");
            NormalizedBoard grid = board.orElseThrow(() -> new IllegalArgumentException("a GridWords result requires a board"));
            int expected = outcome instanceof ShareOutcome.Solved solved ? solved.attemptsUsed() : 6;
            if (grid.rows().size() != expected || quadWordsBoards.isPresent()) throw new IllegalArgumentException("invalid GridWords boards");
        } else {
            if (outcome.maxAttempts() != 9 || board.isPresent()) throw new IllegalArgumentException("invalid QuadWords result");
            quadWordsBoards.ifPresent(boards -> {
                int expected = outcome instanceof ShareOutcome.Solved solved ? solved.attemptsUsed() : 9;
                if (boards.ordered().stream().anyMatch(candidate -> candidate.rows().size() != expected)) throw new IllegalArgumentException("QuadWords board row count does not match outcome");
            });
        }
    }
}