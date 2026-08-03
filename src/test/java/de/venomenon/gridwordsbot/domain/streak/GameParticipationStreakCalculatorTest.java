package de.venomenon.gridwordsbot.domain.streak;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class GameParticipationStreakCalculatorTest {
    private static final long FIRST = 1L;
    private static final long SECOND = 2L;
    private static final long THIRD = 3L;
    private static final long NONE = 4L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 3);

    private final StreakCalculator calculator = new StreakCalculator();

    @Test
    void fourParticipationStatesUseOnlyTheirApplicablePersonalConditions() {
        List<GameParticipationPeriod> periods = new ArrayList<>();
        periods.add(period(FIRST, GameType.GRIDWORDS, TODAY.minusDays(3), null));
        periods.add(period(SECOND, GameType.QUADWORDS, TODAY.minusDays(3), null));
        periods.addAll(both(THIRD, TODAY.minusDays(3), null));
        List<StreakCalculator.PlayerResult> results = List.of(
                result(FIRST, TODAY, GameType.GRIDWORDS, true),
                result(SECOND, TODAY, GameType.QUADWORDS, true),
                result(THIRD, TODAY, GameType.GRIDWORDS, true),
                result(THIRD, TODAY, GameType.QUADWORDS, true));

        StreakSummary gridOnly = calculate(FIRST, results, periods);
        StreakSummary quadOnly = calculate(SECOND, results, periods);
        StreakSummary both = calculate(THIRD, results, periods);
        StreakSummary inactive = calculate(NONE, results, periods);

        assertThat(gridOnly).extracting(
                        StreakSummary::personalActivity,
                        StreakSummary::personalComplete,
                        StreakSummary::personalGridWordsSolved,
                        StreakSummary::personalQuadWordsSolved,
                        StreakSummary::personalPerfect)
                .containsExactly(1, 0, 1, 0, 0);
        assertThat(quadOnly).extracting(
                        StreakSummary::personalActivity,
                        StreakSummary::personalComplete,
                        StreakSummary::personalGridWordsSolved,
                        StreakSummary::personalQuadWordsSolved,
                        StreakSummary::personalPerfect)
                .containsExactly(1, 0, 0, 1, 0);
        assertThat(both).extracting(
                        StreakSummary::personalActivity,
                        StreakSummary::personalComplete,
                        StreakSummary::personalGridWordsSolved,
                        StreakSummary::personalQuadWordsSolved,
                        StreakSummary::personalPerfect)
                .containsExactly(1, 1, 1, 1, 1);
        assertThat(inactive).extracting(
                        StreakSummary::personalActivity,
                        StreakSummary::personalComplete,
                        StreakSummary::personalGridWordsSolved,
                        StreakSummary::personalQuadWordsSolved,
                        StreakSummary::personalPerfect)
                .containsExactly(0, 0, 0, 0, 0);
    }

    @Test
    void missingTodayIsProvisionalOnlyForApplicableConditions() {
        List<GameParticipationPeriod> periods = List.of(
                period(FIRST, GameType.GRIDWORDS, TODAY.minusDays(2), null),
                period(SECOND, GameType.GRIDWORDS, TODAY.minusDays(2), null));
        List<StreakCalculator.PlayerResult> results = List.of(
                result(FIRST, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(SECOND, TODAY.minusDays(1), GameType.GRIDWORDS, true),
                result(FIRST, TODAY, GameType.GRIDWORDS, true));

        StreakSummary provisional = calculator.calculateWithGameParticipation(
                results, periods, SECOND, TODAY, true);
        StreakSummary historical = calculator.calculateWithGameParticipation(
                results, periods, SECOND, TODAY, false);

        assertThat(provisional.personalGridWordsSolved()).isEqualTo(1);
        assertThat(provisional.sharedGridWordsSolved()).isEqualTo(1);
        assertThat(provisional.personalQuadWordsSolved()).isZero();
        assertThat(provisional.personalComplete()).isZero();
        assertThat(historical.personalGridWordsSolved()).isZero();
        assertThat(historical.sharedGridWordsSolved()).isZero();
    }

    @Test
    void nonParticipationTodayIsAnImmediateBoundaryEvenWhileTodayIsProvisional() {
        List<GameParticipationPeriod> periods = both(FIRST, TODAY.minusDays(5), TODAY);
        List<StreakCalculator.PlayerResult> results = complete(FIRST, TODAY.minusDays(1), true);

        StreakSummary streaks = calculate(FIRST, results, periods);

        assertThat(streaks).extracting(
                        StreakSummary::personalActivity,
                        StreakSummary::personalComplete,
                        StreakSummary::personalGridWordsSolved,
                        StreakSummary::personalQuadWordsSolved,
                        StreakSummary::personalPerfect)
                .containsExactly(0, 0, 0, 0, 0);
    }

    @Test
    void exitGapAndReentryDoNotBridgeAnyPersonalSeries() {
        List<GameParticipationPeriod> periods = new ArrayList<>();
        periods.addAll(both(FIRST, TODAY.minusDays(5), TODAY.minusDays(2)));
        periods.addAll(both(FIRST, TODAY, null));
        List<StreakCalculator.PlayerResult> results = new ArrayList<>();
        results.addAll(complete(FIRST, TODAY.minusDays(3), true));
        results.addAll(complete(FIRST, TODAY, true));

        StreakSummary streaks = calculate(FIRST, results, periods);

        assertThat(streaks).extracting(
                        StreakSummary::personalActivity,
                        StreakSummary::personalComplete,
                        StreakSummary::personalGridWordsSolved,
                        StreakSummary::personalQuadWordsSolved,
                        StreakSummary::personalPerfect)
                .containsExactly(1, 1, 1, 1, 1);
    }

    @Test
    void sharedGameSeriesUseDifferentPopulationsWhileCompleteAndPerfectUseOnlyIntersection() {
        List<GameParticipationPeriod> periods = List.of(
                period(FIRST, GameType.GRIDWORDS, TODAY, null),
                period(FIRST, GameType.QUADWORDS, TODAY, null),
                period(SECOND, GameType.GRIDWORDS, TODAY, null),
                period(SECOND, GameType.QUADWORDS, TODAY, null),
                period(THIRD, GameType.GRIDWORDS, TODAY, null),
                period(NONE, GameType.QUADWORDS, TODAY, null));
        List<StreakCalculator.PlayerResult> solved = new ArrayList<>();
        solved.addAll(complete(FIRST, TODAY, true));
        solved.addAll(complete(SECOND, TODAY, true));
        solved.add(result(THIRD, TODAY, GameType.GRIDWORDS, true));
        solved.add(result(NONE, TODAY, GameType.QUADWORDS, true));

        StreakSummary allSolved = calculate(FIRST, solved, periods);
        List<StreakCalculator.PlayerResult> corrected = new ArrayList<>(solved);
        corrected.removeIf(result -> result.playerId() == THIRD);
        corrected.add(result(THIRD, TODAY, GameType.GRIDWORDS, false));
        StreakSummary afterSingleGameCorrection = calculate(FIRST, corrected, periods);

        assertThat(allSolved).extracting(
                        StreakSummary::sharedGridWordsSolved,
                        StreakSummary::sharedQuadWordsSolved,
                        StreakSummary::sharedComplete,
                        StreakSummary::sharedPerfect)
                .containsExactly(1, 1, 1, 1);
        assertThat(afterSingleGameCorrection).extracting(
                        StreakSummary::sharedGridWordsSolved,
                        StreakSummary::sharedQuadWordsSolved,
                        StreakSummary::sharedComplete,
                        StreakSummary::sharedPerfect)
                .containsExactly(0, 1, 1, 1);
    }

    @Test
    void everySharedConditionAppliesItsOwnMinimumPopulation() {
        List<GameParticipationPeriod> periods = List.of(
                period(FIRST, GameType.GRIDWORDS, TODAY, null),
                period(SECOND, GameType.QUADWORDS, TODAY, null),
                period(THIRD, GameType.QUADWORDS, TODAY, null));
        List<StreakCalculator.PlayerResult> results = List.of(
                result(FIRST, TODAY, GameType.GRIDWORDS, true),
                result(SECOND, TODAY, GameType.QUADWORDS, true),
                result(THIRD, TODAY, GameType.QUADWORDS, true));

        StreakSummary streaks = calculate(FIRST, results, periods);

        assertThat(streaks.sharedGridWordsSolved()).isZero();
        assertThat(streaks.sharedQuadWordsSolved()).isEqualTo(1);
        assertThat(streaks.sharedComplete()).isZero();
        assertThat(streaks.sharedPerfect()).isZero();
    }

    @Test
    void yesterdayBackfillAndCurrentCorrectionRecalculateEveryAffectedSeries() {
        List<GameParticipationPeriod> periods = new ArrayList<>();
        periods.addAll(both(FIRST, TODAY.minusDays(5), null));
        periods.addAll(both(SECOND, TODAY.minusDays(5), null));
        List<StreakCalculator.PlayerResult> beforeBackfill = new ArrayList<>();
        beforeBackfill.addAll(complete(FIRST, TODAY.minusDays(1), true));
        beforeBackfill.add(result(SECOND, TODAY.minusDays(1), GameType.QUADWORDS, true));
        beforeBackfill.addAll(complete(FIRST, TODAY, true));
        beforeBackfill.addAll(complete(SECOND, TODAY, true));

        StreakSummary before = calculate(FIRST, beforeBackfill, periods);
        List<StreakCalculator.PlayerResult> afterBackfill = new ArrayList<>(beforeBackfill);
        afterBackfill.add(result(SECOND, TODAY.minusDays(1), GameType.GRIDWORDS, true));
        StreakSummary after = calculate(FIRST, afterBackfill, periods);
        List<StreakCalculator.PlayerResult> afterCorrection = new ArrayList<>(afterBackfill);
        afterCorrection.removeIf(result -> result.playerId() == SECOND
                && result.result().gameDate().equals(TODAY)
                && result.result().gameType() == GameType.GRIDWORDS);
        afterCorrection.add(result(SECOND, TODAY, GameType.GRIDWORDS, false));
        StreakSummary corrected = calculate(FIRST, afterCorrection, periods);

        assertThat(before.sharedGridWordsSolved()).isEqualTo(1);
        assertThat(after.sharedGridWordsSolved()).isEqualTo(2);
        assertThat(after.sharedComplete()).isEqualTo(2);
        assertThat(after.sharedPerfect()).isEqualTo(2);
        assertThat(corrected.sharedGridWordsSolved()).isZero();
        assertThat(corrected.sharedQuadWordsSolved()).isEqualTo(2);
        assertThat(corrected.sharedComplete()).isEqualTo(2);
        assertThat(corrected.sharedPerfect()).isZero();
    }

    @Test
    void globalCompatibilityApiStillProjectsEveryPeriodToBothGames() {
        List<ParticipationPeriod> global = List.of(
                new ParticipationPeriod(FIRST, TODAY.minusDays(2), null),
                new ParticipationPeriod(SECOND, TODAY.minusDays(2), null));
        List<GameParticipationPeriod> typed = new ArrayList<>();
        typed.addAll(both(FIRST, TODAY.minusDays(2), null));
        typed.addAll(both(SECOND, TODAY.minusDays(2), null));
        List<StreakCalculator.PlayerResult> results = new ArrayList<>();
        results.addAll(complete(FIRST, TODAY, true));
        results.addAll(complete(SECOND, TODAY, true));

        assertThat(calculator.calculateWithParticipation(results, global, FIRST, TODAY, false))
                .isEqualTo(calculator.calculateWithGameParticipation(results, typed, FIRST, TODAY, false));
    }

    private StreakSummary calculate(
            long playerId,
            List<StreakCalculator.PlayerResult> results,
            List<GameParticipationPeriod> periods) {
        return calculator.calculateWithGameParticipation(results, periods, playerId, TODAY, true);
    }

    private static List<GameParticipationPeriod> both(
            long playerId, LocalDate activeFrom, LocalDate inactiveFrom) {
        return List.of(
                period(playerId, GameType.GRIDWORDS, activeFrom, inactiveFrom),
                period(playerId, GameType.QUADWORDS, activeFrom, inactiveFrom));
    }

    private static GameParticipationPeriod period(
            long playerId, GameType gameType, LocalDate activeFrom, LocalDate inactiveFrom) {
        return new GameParticipationPeriod(playerId, gameType, activeFrom, inactiveFrom);
    }

    private static List<StreakCalculator.PlayerResult> complete(
            long playerId, LocalDate date, boolean solved) {
        return List.of(
                result(playerId, date, GameType.GRIDWORDS, solved),
                result(playerId, date, GameType.QUADWORDS, solved));
    }

    private static StreakCalculator.PlayerResult result(
            long playerId, LocalDate date, GameType gameType, boolean solved) {
        int maximum = gameType == GameType.GRIDWORDS ? 6 : 9;
        ShareOutcome outcome = solved
                ? new ShareOutcome.Solved(1, maximum)
                : new ShareOutcome.Unsolved(maximum);
        Optional<NormalizedBoard> board = gameType == GameType.GRIDWORDS
                ? Optional.of(new NormalizedBoard(
                        Collections.nCopies(solved ? 1 : maximum, "⬜⬜⬜⬜⬜")))
                : Optional.empty();
        return new StreakCalculator.PlayerResult(
                playerId,
                new ParsedGameResult(
                        gameType,
                        date,
                        outcome,
                        Duration.ofSeconds(1),
                        OptionalInt.empty(),
                        board));
    }
}
