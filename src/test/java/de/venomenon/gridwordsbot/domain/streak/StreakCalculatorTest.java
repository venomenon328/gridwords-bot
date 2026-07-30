package de.venomenon.gridwordsbot.domain.streak;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class StreakCalculatorTest {
    private static final long TOBIAS = 1L;
    private static final long GEORGIA = 2L;
    private static final long THIRD = 3L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 29);
    private final StreakCalculator calculator = new StreakCalculator();

    @Test
    void calculatesAllSevenIndependentStreaksAndKeepsAnIncompleteTodayOpen() {
        List<StreakCalculator.PlayerResult> results = List.of(
                result(TOBIAS, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(TOBIAS, TODAY, GameType.GRIDWORDS, true));
        StreakSummary streaks = calculator.calculate(results, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);
        assertThat(streaks.personalActivity()).isEqualTo(2);
        assertThat(streaks.personalComplete()).isEqualTo(1);
        assertThat(streaks.personalGridWordsSolved()).isEqualTo(2);
        assertThat(streaks.personalQuadWordsSolved()).isEqualTo(1);
        assertThat(streaks.personalPerfect()).isEqualTo(1);
        assertThat(streaks.sharedComplete()).isEqualTo(1);
        assertThat(streaks.sharedPerfect()).isEqualTo(1);
    }

    @Test
    void unsolvedTodayEndsRelevantSolvedAndPerfectStreakImmediately() {
        List<StreakCalculator.PlayerResult> results = List.of(
                result(TOBIAS, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(TOBIAS, TODAY, GameType.GRIDWORDS, false));
        StreakSummary streaks = calculator.calculate(results, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);
        assertThat(streaks.personalActivity()).isEqualTo(2);
        assertThat(streaks.personalGridWordsSolved()).isZero();
        assertThat(streaks.personalPerfect()).isZero();
    }

    @Test
    void missingHistoricalResultBreaksOnlyTheAffectedStreak() {
        List<StreakCalculator.PlayerResult> results = List.of(
                result(TOBIAS, TODAY, GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY.minusDays(2), GameType.GRIDWORDS, true));

        StreakSummary streaks = calculator.calculate(results, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);

        assertThat(streaks.personalActivity()).isEqualTo(1);
        assertThat(streaks.personalGridWordsSolved()).isEqualTo(1);
        assertThat(streaks.personalQuadWordsSolved()).isZero();
        assertThat(streaks.personalComplete()).isZero();
        assertThat(streaks.personalPerfect()).isZero();
    }

    @Test
    void unsolvedQuadWordsDoesNotBreakTheIndependentGridWordsSolvedStreak() {
        List<StreakCalculator.PlayerResult> results = List.of(
                result(TOBIAS, TODAY, GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY, GameType.QUADWORDS, false),
                result(TOBIAS, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY.minusDays(1), GameType.QUADWORDS, true));

        StreakSummary streaks = calculator.calculate(results, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);

        assertThat(streaks.personalActivity()).isEqualTo(2);
        assertThat(streaks.personalComplete()).isEqualTo(2);
        assertThat(streaks.personalGridWordsSolved()).isEqualTo(2);
        assertThat(streaks.personalQuadWordsSolved()).isZero();
        assertThat(streaks.personalPerfect()).isZero();
    }

    @Test
    void yesterdayBackfillRecalculatesCompleteAndPerfectStreaks() {
        List<StreakCalculator.PlayerResult> beforeBackfill = List.of(
                result(TOBIAS, TODAY, GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY, GameType.QUADWORDS, true),
                result(TOBIAS, TODAY.minusDays(1), GameType.GRIDWORDS, true));
        List<StreakCalculator.PlayerResult> afterBackfill = new java.util.ArrayList<>(beforeBackfill);
        afterBackfill.add(result(TOBIAS, TODAY.minusDays(1), GameType.QUADWORDS, true));

        StreakSummary before = calculator.calculate(beforeBackfill, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);
        StreakSummary after = calculator.calculate(afterBackfill, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);

        assertThat(before.personalComplete()).isEqualTo(1);
        assertThat(before.personalPerfect()).isEqualTo(1);
        assertThat(after.personalComplete()).isEqualTo(2);
        assertThat(after.personalPerfect()).isEqualTo(2);
    }

    @Test
    void sharedCompleteAndPerfectStreaksRemainIndependent() {
        List<StreakCalculator.PlayerResult> results = List.of(
                result(TOBIAS, TODAY, GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY, GameType.QUADWORDS, true),
                result(GEORGIA, TODAY, GameType.GRIDWORDS, false),
                result(GEORGIA, TODAY, GameType.QUADWORDS, true),
                result(TOBIAS, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.QUADWORDS, true));

        StreakSummary streaks = calculator.calculate(results, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);

        assertThat(streaks.sharedComplete()).isEqualTo(2);
        assertThat(streaks.sharedPerfect()).isZero();
    }

    @Test
    void thirdPlayerJoiningTodayDoesNotChangeYesterdaySharedStreak() {
        List<StreakCalculator.PlayerResult> results = completeResults(TOBIAS, GEORGIA, TODAY.minusDays(1));
        results = new java.util.ArrayList<>(results);
        ((java.util.ArrayList<StreakCalculator.PlayerResult>) results).addAll(completeResults(TOBIAS, GEORGIA, THIRD, TODAY));
        List<ParticipationPeriod> periods = List.of(
                new ParticipationPeriod(TOBIAS, TODAY.minusDays(10), null),
                new ParticipationPeriod(GEORGIA, TODAY.minusDays(10), null),
                new ParticipationPeriod(THIRD, TODAY, null));

        StreakSummary streaks = calculator.calculateWithParticipation(results, periods, TOBIAS, TODAY);

        assertThat(streaks.sharedComplete()).isEqualTo(2);
        assertThat(streaks.sharedPerfect()).isEqualTo(2);
    }

    @Test
    void thirdPlayerLeavingTodayDoesNotChangeEarlierSharedDays() {
        List<StreakCalculator.PlayerResult> results = new java.util.ArrayList<>(completeResults(TOBIAS, GEORGIA, TODAY));
        ((java.util.ArrayList<StreakCalculator.PlayerResult>) results)
                .addAll(completeResults(TOBIAS, GEORGIA, THIRD, TODAY.minusDays(1)));
        List<ParticipationPeriod> periods = List.of(
                new ParticipationPeriod(TOBIAS, TODAY.minusDays(10), null),
                new ParticipationPeriod(GEORGIA, TODAY.minusDays(10), null),
                new ParticipationPeriod(THIRD, TODAY.minusDays(10), TODAY));

        StreakSummary streaks = calculator.calculateWithParticipation(results, periods, TOBIAS, TODAY);

        assertThat(streaks.sharedComplete()).isEqualTo(2);
        assertThat(streaks.sharedPerfect()).isEqualTo(2);
    }

    @Test
    void fewerThanTwoParticipantsNeverCreatesASharedDay() {
        List<ParticipationPeriod> periods = List.of(new ParticipationPeriod(TOBIAS, TODAY.minusDays(10), null));

        StreakSummary streaks = calculator.calculateWithParticipation(
                completeResults(TOBIAS, TODAY), periods, TOBIAS, TODAY);

        assertThat(streaks.sharedComplete()).isZero();
        assertThat(streaks.sharedPerfect()).isZero();
    }

    private static List<StreakCalculator.PlayerResult> completeResults(long first, long second, LocalDate date) {
        return completeResults(new long[] {first, second}, date);
    }

    private static List<StreakCalculator.PlayerResult> completeResults(
            long first, long second, long third, LocalDate date) {
        return completeResults(new long[] {first, second, third}, date);
    }

    private static List<StreakCalculator.PlayerResult> completeResults(long player, LocalDate date) {
        return completeResults(new long[] {player}, date);
    }

    private static List<StreakCalculator.PlayerResult> completeResults(long[] players, LocalDate date) {
        java.util.ArrayList<StreakCalculator.PlayerResult> results = new java.util.ArrayList<>();
        for (long player : players) {
            results.add(result(player, date, GameType.GRIDWORDS, true));
            results.add(result(player, date, GameType.QUADWORDS, true));
        }
        return results;
    }

    private static StreakCalculator.PlayerResult result(
            long player, LocalDate date, GameType type, boolean solved) {
        int maximum = type == GameType.GRIDWORDS ? 6 : 9;
        ShareOutcome outcome = solved ? new ShareOutcome.Solved(1, maximum) : new ShareOutcome.Unsolved(maximum);
        Optional<NormalizedBoard> board = type == GameType.GRIDWORDS
                ? Optional.of(new NormalizedBoard(java.util.Collections.nCopies(solved ? 1 : 6, "⬜⬜⬜⬜⬜")))
                : Optional.empty();
        return new StreakCalculator.PlayerResult(
                player,
                new ParsedGameResult(type, date, outcome, Duration.ofSeconds(1), OptionalInt.empty(), board));
    }
}
