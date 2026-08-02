package de.venomenon.gridwordsbot.domain.streak;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
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
    void calculatesAllNineIndependentStreaksAndKeepsAnIncompleteTodayOpen() {
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
        assertThat(streaks.sharedGridWordsSolved()).isEqualTo(1);
        assertThat(streaks.sharedQuadWordsSolved()).isEqualTo(1);
        assertThat(streaks.sharedComplete()).isEqualTo(1);
        assertThat(streaks.sharedPerfect()).isEqualTo(1);
    }

    @Test
    void unsolvedTodayEndsOnlyTheAffectedSolvedStreaksImmediately() {
        List<StreakCalculator.PlayerResult> results = List.of(
                result(TOBIAS, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(TOBIAS, TODAY, GameType.GRIDWORDS, false));

        StreakSummary streaks = calculator.calculate(results, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);

        assertThat(streaks.personalActivity()).isEqualTo(2);
        assertThat(streaks.personalGridWordsSolved()).isZero();
        assertThat(streaks.personalQuadWordsSolved()).isEqualTo(1);
        assertThat(streaks.personalPerfect()).isZero();
        assertThat(streaks.sharedGridWordsSolved()).isZero();
        assertThat(streaks.sharedQuadWordsSolved()).isEqualTo(1);
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
        assertThat(streaks.sharedGridWordsSolved()).isZero();
        assertThat(streaks.sharedQuadWordsSolved()).isZero();
    }

    @Test
    void unsolvedQuadWordsDoesNotBreakIndependentGridWordsSolvedStreaks() {
        List<StreakCalculator.PlayerResult> results = List.of(
                result(TOBIAS, TODAY, GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY, GameType.QUADWORDS, false),
                result(GEORGIA, TODAY, GameType.GRIDWORDS, true),
                result(GEORGIA, TODAY, GameType.QUADWORDS, true),
                result(TOBIAS, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(TOBIAS, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.QUADWORDS, true));

        StreakSummary streaks = calculator.calculate(results, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);

        assertThat(streaks.personalActivity()).isEqualTo(2);
        assertThat(streaks.personalComplete()).isEqualTo(2);
        assertThat(streaks.personalGridWordsSolved()).isEqualTo(2);
        assertThat(streaks.personalQuadWordsSolved()).isZero();
        assertThat(streaks.personalPerfect()).isZero();
        assertThat(streaks.sharedGridWordsSolved()).isEqualTo(2);
        assertThat(streaks.sharedQuadWordsSolved()).isZero();
        assertThat(streaks.sharedComplete()).isEqualTo(2);
        assertThat(streaks.sharedPerfect()).isZero();
    }

    @Test
    void currentMissingSharedResultIsProvisionalButHistoricalMissingResultEndsTheStreak() {
        List<StreakCalculator.PlayerResult> results = completeResults(TOBIAS, GEORGIA, TODAY.minusDays(1));
        results = new ArrayList<>(results);
        results.add(result(TOBIAS, TODAY, GameType.GRIDWORDS, true));

        StreakSummary provisional = calculator.calculateWithParticipation(
                results, alwaysActive(TOBIAS, GEORGIA), TOBIAS, TODAY, true);
        StreakSummary historical = calculator.calculateWithParticipation(
                results, alwaysActive(TOBIAS, GEORGIA), TOBIAS, TODAY, false);

        assertThat(provisional.sharedGridWordsSolved()).isEqualTo(1);
        assertThat(provisional.sharedQuadWordsSolved()).isEqualTo(1);
        assertThat(historical.sharedGridWordsSolved()).isZero();
        assertThat(historical.sharedQuadWordsSolved()).isZero();
    }

    @Test
    void everyActivePlayerMustSolveTheSelectedGame() {
        List<StreakCalculator.PlayerResult> results = new ArrayList<>(
                completeResults(TOBIAS, GEORGIA, THIRD, TODAY.minusDays(1)));
        results.add(result(TOBIAS, TODAY, GameType.GRIDWORDS, true));
        results.add(result(GEORGIA, TODAY, GameType.GRIDWORDS, true));
        results.add(result(THIRD, TODAY, GameType.GRIDWORDS, false));
        results.add(result(TOBIAS, TODAY, GameType.QUADWORDS, true));
        results.add(result(GEORGIA, TODAY, GameType.QUADWORDS, true));
        results.add(result(THIRD, TODAY, GameType.QUADWORDS, true));

        StreakSummary streaks = calculator.calculateWithParticipation(
                results, alwaysActive(TOBIAS, GEORGIA, THIRD), TOBIAS, TODAY);

        assertThat(streaks.sharedGridWordsSolved()).isZero();
        assertThat(streaks.sharedQuadWordsSolved()).isEqualTo(2);
    }

    @Test
    void yesterdayBackfillRestoresPersonalAndSharedSolvedStreaks() {
        List<StreakCalculator.PlayerResult> beforeBackfill = new ArrayList<>(
                completeResults(TOBIAS, GEORGIA, TODAY));
        beforeBackfill.add(result(TOBIAS, TODAY.minusDays(1), GameType.GRIDWORDS, true));
        beforeBackfill.add(result(TOBIAS, TODAY.minusDays(1), GameType.QUADWORDS, true));
        beforeBackfill.add(result(GEORGIA, TODAY.minusDays(1), GameType.QUADWORDS, true));

        List<StreakCalculator.PlayerResult> afterBackfill = new ArrayList<>(beforeBackfill);
        afterBackfill.add(result(GEORGIA, TODAY.minusDays(1), GameType.GRIDWORDS, true));

        StreakSummary before = calculator.calculate(beforeBackfill, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);
        StreakSummary after = calculator.calculate(afterBackfill, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);

        assertThat(before.sharedGridWordsSolved()).isEqualTo(1);
        assertThat(before.sharedQuadWordsSolved()).isEqualTo(2);
        assertThat(after.sharedGridWordsSolved()).isEqualTo(2);
        assertThat(after.sharedQuadWordsSolved()).isEqualTo(2);
        assertThat(after.sharedComplete()).isEqualTo(2);
        assertThat(after.sharedPerfect()).isEqualTo(2);
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

        assertThat(streaks.sharedGridWordsSolved()).isZero();
        assertThat(streaks.sharedQuadWordsSolved()).isEqualTo(2);
        assertThat(streaks.sharedComplete()).isEqualTo(2);
        assertThat(streaks.sharedPerfect()).isZero();
    }

    @Test
    void joiningLeavingAndRejoiningUseTheHistoricalDailyPopulation() {
        List<StreakCalculator.PlayerResult> results = new ArrayList<>();
        results.addAll(completeResults(TOBIAS, GEORGIA, TODAY.minusDays(3)));
        results.addAll(completeResults(TOBIAS, GEORGIA, THIRD, TODAY.minusDays(2)));
        results.addAll(completeResults(TOBIAS, GEORGIA, TODAY.minusDays(1)));
        results.addAll(completeResults(TOBIAS, GEORGIA, THIRD, TODAY));
        List<ParticipationPeriod> periods = List.of(
                new ParticipationPeriod(TOBIAS, TODAY.minusDays(10), null),
                new ParticipationPeriod(GEORGIA, TODAY.minusDays(10), null),
                new ParticipationPeriod(THIRD, TODAY.minusDays(2), TODAY.minusDays(1)),
                new ParticipationPeriod(THIRD, TODAY, null));

        StreakSummary streaks = calculator.calculateWithParticipation(results, periods, TOBIAS, TODAY);

        assertThat(streaks.sharedGridWordsSolved()).isEqualTo(4);
        assertThat(streaks.sharedQuadWordsSolved()).isEqualTo(4);
        assertThat(streaks.sharedComplete()).isEqualTo(4);
        assertThat(streaks.sharedPerfect()).isEqualTo(4);
    }

    @Test
    void fewerThanTwoParticipantsNeverCreatesAnySharedDay() {
        List<ParticipationPeriod> periods = List.of(
                new ParticipationPeriod(TOBIAS, TODAY.minusDays(10), null));

        StreakSummary streaks = calculator.calculateWithParticipation(
                completeResults(TOBIAS, TODAY), periods, TOBIAS, TODAY);

        assertThat(streaks.sharedGridWordsSolved()).isZero();
        assertThat(streaks.sharedQuadWordsSolved()).isZero();
        assertThat(streaks.sharedComplete()).isZero();
        assertThat(streaks.sharedPerfect()).isZero();
    }

    private static List<ParticipationPeriod> alwaysActive(long... players) {
        return java.util.Arrays.stream(players)
                .mapToObj(player -> new ParticipationPeriod(player, TODAY.minusDays(100), null))
                .toList();
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
        ArrayList<StreakCalculator.PlayerResult> results = new ArrayList<>();
        for (long player : players) {
            results.add(result(player, date, GameType.GRIDWORDS, true));
            results.add(result(player, date, GameType.QUADWORDS, true));
        }
        return results;
    }

    private static StreakCalculator.PlayerResult result(
            long player, LocalDate date, GameType type, boolean solved) {
        int maximum = type == GameType.GRIDWORDS ? 6 : 9;
        ShareOutcome outcome = solved
                ? new ShareOutcome.Solved(1, maximum)
                : new ShareOutcome.Unsolved(maximum);
        Optional<NormalizedBoard> board = type == GameType.GRIDWORDS
                ? Optional.of(new NormalizedBoard(
                        java.util.Collections.nCopies(solved ? 1 : 6, "⬜⬜⬜⬜⬜")))
                : Optional.empty();
        return new StreakCalculator.PlayerResult(
                player,
                new ParsedGameResult(
                        type,
                        date,
                        outcome,
                        Duration.ofSeconds(1),
                        OptionalInt.empty(),
                        board));
    }
}
