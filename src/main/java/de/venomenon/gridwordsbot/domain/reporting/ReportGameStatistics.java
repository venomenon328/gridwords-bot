package de.venomenon.gridwordsbot.domain.reporting;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Raw, game-specific statistics for one participant and one report period. */
public record ReportGameStatistics(
        GameType gameType,
        int possibleDays,
        int submitted,
        int solved,
        int unsolved,
        int missing,
        Optional<ReportRatio> solutionRate,
        long solvedAttemptsTotal,
        int solvedAttemptsCount,
        Duration solvedDurationTotal,
        int solvedDurationCount,
        Optional<Duration> bestSolvedDuration) {
    public ReportGameStatistics {
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(solutionRate, "solutionRate");
        Objects.requireNonNull(solvedDurationTotal, "solvedDurationTotal");
        Objects.requireNonNull(bestSolvedDuration, "bestSolvedDuration");
        if (possibleDays < 0 || submitted < 0 || solved < 0 || unsolved < 0 || missing < 0
                || solvedAttemptsTotal < 0 || solvedAttemptsCount < 0 || solvedDurationCount < 0
                || solvedDurationTotal.isNegative()) {
            throw new IllegalArgumentException("statistics values must not be negative");
        }
        if (submitted != solved + unsolved || possibleDays != submitted + missing) {
            throw new IllegalArgumentException("submitted and missing counts must be consistent");
        }
        if (solvedAttemptsCount != solved || solvedDurationCount != solved) {
            throw new IllegalArgumentException("solved aggregates must include every solved result");
        }
        if (submitted == 0 ? solutionRate.isPresent() : solutionRate.map(rate ->
                rate.numerator() != solved || rate.denominator() != submitted).orElse(true)) {
            throw new IllegalArgumentException("solution rate must match solved and submitted counts");
        }
        if (solved == 0 ? bestSolvedDuration.isPresent() : bestSolvedDuration.isEmpty()) {
            throw new IllegalArgumentException("best solved duration must match solved results");
        }
    }
}
