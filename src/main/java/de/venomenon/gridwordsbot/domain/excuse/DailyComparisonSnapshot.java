package de.venomenon.gridwordsbot.domain.excuse;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Name-free aggregate of the already visible same-game comparison set. It can be persisted later
 * without allowing subsequently received results to alter the original decision.
 */
public record DailyComparisonSnapshot(
        GameType gameType,
        int comparedResultCount,
        boolean allComparedResultsSolved,
        OptionalInt highestSolvedAttempts,
        Duration longestDuration) {

    public DailyComparisonSnapshot {
        Objects.requireNonNull(gameType, "gameType");
        if (comparedResultCount < 0) {
            throw new IllegalArgumentException("comparedResultCount must not be negative");
        }
        Objects.requireNonNull(highestSolvedAttempts, "highestSolvedAttempts");
        Objects.requireNonNull(longestDuration, "longestDuration");
        if (longestDuration.isNegative()) {
            throw new IllegalArgumentException("longestDuration must not be negative");
        }
        if (comparedResultCount == 0 && (allComparedResultsSolved || highestSolvedAttempts.isPresent()
                || !longestDuration.isZero())) {
            throw new IllegalArgumentException("an empty comparison must not have aggregate values");
        }
        if (highestSolvedAttempts.isPresent() && highestSolvedAttempts.getAsInt() < 1) {
            throw new IllegalArgumentException("highestSolvedAttempts must be positive when present");
        }
        if (!allComparedResultsSolved && highestSolvedAttempts.isPresent()) {
            throw new IllegalArgumentException("attempt aggregate requires only solved compared results");
        }
    }

    public static DailyComparisonSnapshot from(GameType gameType, List<ExcuseDailyResult> results) {
        Objects.requireNonNull(gameType, "gameType");
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        if (results.isEmpty()) {
            return new DailyComparisonSnapshot(gameType, 0, false, OptionalInt.empty(), Duration.ZERO);
        }
        if (results.stream().anyMatch(result -> result.gameType() != gameType)) {
            throw new IllegalArgumentException("comparison results must have the requested game type");
        }
        boolean allSolved = results.stream().allMatch(result -> result.outcome() instanceof ShareOutcome.Solved);
        OptionalInt highestAttempts = allSolved
                ? OptionalInt.of(results.stream().mapToInt(result -> ((ShareOutcome.Solved) result.outcome()).attemptsUsed())
                        .max().orElseThrow())
                : OptionalInt.empty();
        Duration longestDuration = results.stream().map(ExcuseDailyResult::duration).max(Duration::compareTo).orElseThrow();
        return new DailyComparisonSnapshot(gameType, results.size(), allSolved, highestAttempts, longestDuration);
    }

    public boolean hasAtLeastTwoResults() {
        return comparedResultCount >= 2;
    }
}
