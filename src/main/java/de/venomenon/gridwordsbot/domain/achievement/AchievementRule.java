package de.venomenon.gridwordsbot.domain.achievement;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalTime;
import java.util.Objects;

/** Typisierte, rein fachliche Regelparameter des kuratierten Achievement-Katalogs. */
public sealed interface AchievementRule permits
        AchievementRule.ParticipationCount,
        AchievementRule.ParticipationStreak,
        AchievementRule.SuccessStreak,
        AchievementRule.ExactSolvedAttempts,
        AchievementRule.CrossGameParticipationCount,
        AchievementRule.CrossGameSuccessCount,
        AchievementRule.TotalResultCount,
        AchievementRule.QuadWordsConsecutiveBoardAttempts,
        AchievementRule.QuadWordsOutlierBoard,
        AchievementRule.CrossGameEqualFinalScore,
        AchievementRule.CrossGameExactAttempts,
        AchievementRule.ConsecutiveSameSuccessfulResults,
        AchievementRule.ConsecutiveFailures,
        AchievementRule.GridWordsRepeatedPattern,
        AchievementRule.AllYellowBoardRow,
        AchievementRule.LocalTimeBefore,
        AchievementRule.LocalTimeAtOrAfter {

    AchievementScope scope();

    record ParticipationCount(GameType game, int threshold) implements AchievementRule {
        public ParticipationCount {
            Objects.requireNonNull(game, "game");
            requirePositive(threshold, "threshold");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.forGame(game);
        }
    }

    record ParticipationStreak(GameType game, int threshold) implements AchievementRule {
        public ParticipationStreak {
            Objects.requireNonNull(game, "game");
            requirePositive(threshold, "threshold");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.forGame(game);
        }
    }

    record SuccessStreak(GameType game, int threshold) implements AchievementRule {
        public SuccessStreak {
            Objects.requireNonNull(game, "game");
            requirePositive(threshold, "threshold");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.forGame(game);
        }
    }

    record ExactSolvedAttempts(GameType game, int attempts) implements AchievementRule {
        public ExactSolvedAttempts {
            Objects.requireNonNull(game, "game");
            requireSolvedAttempts(game, attempts);
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.forGame(game);
        }
    }

    record CrossGameParticipationCount(int threshold) implements AchievementRule {
        public CrossGameParticipationCount {
            requirePositive(threshold, "threshold");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.CROSS_GAME;
        }
    }

    record CrossGameSuccessCount(int threshold) implements AchievementRule {
        public CrossGameSuccessCount {
            requirePositive(threshold, "threshold");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.CROSS_GAME;
        }
    }

    record TotalResultCount(int threshold) implements AchievementRule {
        public TotalResultCount {
            requirePositive(threshold, "threshold");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.GLOBAL;
        }
    }

    record QuadWordsConsecutiveBoardAttempts() implements AchievementRule {
        @Override
        public AchievementScope scope() {
            return AchievementScope.QUADWORDS;
        }
    }

    record QuadWordsOutlierBoard(int minimumGap) implements AchievementRule {
        public QuadWordsOutlierBoard {
            requirePositive(minimumGap, "minimumGap");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.QUADWORDS;
        }
    }

    record CrossGameEqualFinalScore() implements AchievementRule {
        @Override
        public AchievementScope scope() {
            return AchievementScope.CROSS_GAME;
        }
    }

    record CrossGameExactAttempts(int gridWordsAttempts, int quadWordsAttempts) implements AchievementRule {
        public CrossGameExactAttempts {
            requireSolvedAttempts(GameType.GRIDWORDS, gridWordsAttempts);
            requireSolvedAttempts(GameType.QUADWORDS, quadWordsAttempts);
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.CROSS_GAME;
        }
    }

    record ConsecutiveSameSuccessfulResults(GameType game, int resultCount) implements AchievementRule {
        public ConsecutiveSameSuccessfulResults {
            Objects.requireNonNull(game, "game");
            requireAtLeastTwo(resultCount, "resultCount");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.forGame(game);
        }
    }

    record ConsecutiveFailures(GameType game, int resultCount) implements AchievementRule {
        public ConsecutiveFailures {
            Objects.requireNonNull(game, "game");
            requireAtLeastTwo(resultCount, "resultCount");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.forGame(game);
        }
    }

    /** Three neighboring rows within one canonical GridWords board use the same five-cell pattern. */
    record GridWordsRepeatedPattern(int rowCount) implements AchievementRule {
        public GridWordsRepeatedPattern {
            requireAtLeastTwo(rowCount, "rowCount");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.GRIDWORDS;
        }
    }

    /** At least one canonical GridWords or QuadWords row is entirely yellow. */
    record AllYellowBoardRow() implements AchievementRule {
        @Override
        public AchievementScope scope() {
            return AchievementScope.GLOBAL;
        }
    }

    record LocalTimeBefore(LocalTime exclusiveUpperBound) implements AchievementRule {
        public LocalTimeBefore {
            Objects.requireNonNull(exclusiveUpperBound, "exclusiveUpperBound");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.GLOBAL;
        }
    }

    record LocalTimeAtOrAfter(LocalTime lowerBound) implements AchievementRule {
        public LocalTimeAtOrAfter {
            Objects.requireNonNull(lowerBound, "lowerBound");
        }

        @Override
        public AchievementScope scope() {
            return AchievementScope.GLOBAL;
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireAtLeastTwo(int value, String name) {
        if (value < 2) {
            throw new IllegalArgumentException(name + " must be at least two");
        }
    }

    private static void requireSolvedAttempts(GameType game, int attempts) {
        int minimum = game == GameType.GRIDWORDS ? 1 : 4;
        int maximum = game == GameType.GRIDWORDS ? 6 : 9;
        if (attempts < minimum || attempts > maximum) {
            throw new IllegalArgumentException(
                    "attempts must be within the solved range for " + game + ": " + minimum + ".." + maximum);
        }
    }
}
