package de.venomenon.gridwordsbot.domain.achievement;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class AchievementEvaluatorTest {
    private static final long PLAYER = 7L;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private final AchievementEvaluator evaluator = new AchievementEvaluator();

    @Test
    void firstPerfectGridResultUnlocksParticipationSuccessAndExactOneOnly() {
        AchievementHistorySnapshot snapshot = snapshot(
                List.of(solved(1, GameType.GRIDWORDS, START, 1)),
                active(GameType.GRIDWORDS, START, null));

        AchievementEvaluation result = evaluator.evaluate(snapshot);

        assertThat(result.find(key("participation.1.gridwords"))).isPresent();
        assertThat(result.find(key("streak.success.1.gridwords"))).isPresent();
        assertThat(result.find(key("performance.solve.1.gridwords"))).isPresent();
        assertThat(result.find(key("performance.solve.2.gridwords"))).isEmpty();
        assertThat(result.find(key("performance.solve.3.gridwords"))).isEmpty();
    }

    @Test
    void progressionFamiliesUseExactThresholdDatesAcrossBothGames() {
        List<AchievementHistorySnapshot.Result> results = new ArrayList<>();
        for (int offset = 0; offset < 100; offset++) {
            LocalDate day = START.plusDays(offset);
            results.add(solved(1000 + offset * 2L, GameType.GRIDWORDS, day, 4));
            results.add(solved(1001 + offset * 2L, GameType.QUADWORDS, day, 7));
        }
        AchievementHistorySnapshot snapshot = snapshot(
                results,
                active(GameType.GRIDWORDS, START, null),
                active(GameType.QUADWORDS, START, null));

        AchievementEvaluation evaluation = evaluator.evaluate(snapshot);

        assertEarnedOn(evaluation, "participation.10.gridwords", START.plusDays(9));
        assertEarnedOn(evaluation, "participation.25.gridwords", START.plusDays(24));
        assertEarnedOn(evaluation, "participation.50.gridwords", START.plusDays(49));
        assertEarnedOn(evaluation, "participation.100.gridwords", START.plusDays(99));
        assertEarnedOn(evaluation, "streak.participation.10.gridwords", START.plusDays(9));
        assertEarnedOn(evaluation, "streak.participation.25.gridwords", START.plusDays(24));
        assertEarnedOn(evaluation, "streak.participation.50.gridwords", START.plusDays(49));
        assertEarnedOn(evaluation, "streak.participation.100.gridwords", START.plusDays(99));
        assertEarnedOn(evaluation, "streak.success.10.quadwords", START.plusDays(9));
        assertEarnedOn(evaluation, "streak.success.100.quadwords", START.plusDays(99));
        assertEarnedOn(evaluation, "crossgame.participation.100", START.plusDays(99));
        assertEarnedOn(evaluation, "crossgame.success.100", START.plusDays(99));
        assertEarnedOn(evaluation, "experience.total.100", START.plusDays(49));
        assertEarnedOn(evaluation, "experience.total.200", START.plusDays(99));
        assertThat(evaluation.find(key("experience.total.300"))).isEmpty();
    }

    @Test
    void canonicalQuadFinalValueDrivesNormalResultAchievementsEvenWhenBoardsDiffer() {
        QuadWordsBoards details = boards(1, 2, 3, 4);
        AchievementHistorySnapshot.Result result = solvedWithBoards(1, START, 5, details);
        AchievementHistorySnapshot snapshot = snapshot(
                List.of(result), active(GameType.QUADWORDS, START, null));

        AchievementEvaluation evaluation = evaluator.evaluate(snapshot);

        assertThat(evaluation.find(key("performance.solve.5.quadwords"))).isPresent();
        assertThat(evaluation.find(key("performance.solve.4.quadwords"))).isEmpty();
    }

    @Test
    void participationStreakDoesNotBridgeDeactivationAndReactivation() {
        List<AchievementHistorySnapshot.Result> firstNineAcrossTwoRuns = new ArrayList<>();
        for (int offset = 0; offset < 5; offset++) {
            firstNineAcrossTwoRuns.add(solved(10 + offset, GameType.GRIDWORDS, START.plusDays(offset), 4));
        }
        for (int offset = 6; offset < 11; offset++) {
            firstNineAcrossTwoRuns.add(solved(20 + offset, GameType.GRIDWORDS, START.plusDays(offset), 4));
        }
        AchievementHistorySnapshot beforeThreshold = snapshot(
                firstNineAcrossTwoRuns,
                active(GameType.GRIDWORDS, START, START.plusDays(5)),
                active(GameType.GRIDWORDS, START.plusDays(6), null));

        assertThat(evaluator.evaluate(beforeThreshold).find(key("streak.participation.10.gridwords"))).isEmpty();

        List<AchievementHistorySnapshot.Result> completedSecondRun = new ArrayList<>(firstNineAcrossTwoRuns);
        for (int offset = 11; offset <= 15; offset++) {
            completedSecondRun.add(solved(40 + offset, GameType.GRIDWORDS, START.plusDays(offset), 4));
        }
        AchievementEvaluation evaluation = evaluator.evaluate(snapshot(
                completedSecondRun,
                active(GameType.GRIDWORDS, START, START.plusDays(5)),
                active(GameType.GRIDWORDS, START.plusDays(6), null)));

        assertEarnedOn(evaluation, "streak.participation.10.gridwords", START.plusDays(15));
    }

    @Test
    void missingActiveDayBreaksParticipationAndFailureBreaksSuccessStreak() {
        List<AchievementHistorySnapshot.Result> missingDay = new ArrayList<>();
        for (int offset = 0; offset < 5; offset++) {
            missingDay.add(solved(100 + offset, GameType.GRIDWORDS, START.plusDays(offset), 4));
        }
        for (int offset = 6; offset <= 10; offset++) {
            missingDay.add(solved(100 + offset, GameType.GRIDWORDS, START.plusDays(offset), 4));
        }
        AchievementEvaluation participation = evaluator.evaluate(snapshot(
                missingDay, active(GameType.GRIDWORDS, START, null)));
        assertThat(participation.find(key("streak.participation.10.gridwords"))).isEmpty();

        List<AchievementHistorySnapshot.Result> success = new ArrayList<>();
        for (int offset = 0; offset < 5; offset++) {
            success.add(solved(200 + offset, GameType.QUADWORDS, START.plusDays(offset), 7));
        }
        success.add(failed(205, GameType.QUADWORDS, START.plusDays(5)));
        for (int offset = 6; offset <= 15; offset++) {
            success.add(solved(200 + offset, GameType.QUADWORDS, START.plusDays(offset), 7));
        }
        AchievementEvaluation successfulRun = evaluator.evaluate(snapshot(
                success, active(GameType.QUADWORDS, START, null)));
        assertEarnedOn(successfulRun, "streak.success.10.quadwords", START.plusDays(15));
    }

    @Test
    void crossGameSpecialsUseSameBusinessDayAndCanonicalFinalScores() {
        List<AchievementHistorySnapshot.Result> results = List.of(
                solved(1, GameType.GRIDWORDS, START, 6),
                solved(2, GameType.QUADWORDS, START, 9),
                solved(3, GameType.GRIDWORDS, START.plusDays(1), 5),
                solved(4, GameType.QUADWORDS, START.plusDays(1), 5),
                solved(5, GameType.GRIDWORDS, START.plusDays(2), 1),
                solved(6, GameType.QUADWORDS, START.plusDays(2), 4));
        AchievementEvaluation evaluation = evaluator.evaluate(snapshot(
                results,
                active(GameType.GRIDWORDS, START, null),
                active(GameType.QUADWORDS, START, null)));

        assertEarnedOn(evaluation, "situational.last_chance.gridwords", START);
        assertEarnedOn(evaluation, "situational.last_chance.quadwords", START);
        assertEarnedOn(evaluation, "situational.crossgame.double_last_chance", START);
        assertEarnedOn(evaluation, "situational.crossgame.equal_final_score", START.plusDays(1));
        assertEarnedOn(evaluation, "situational.crossgame.perfect_double", START.plusDays(2));
    }

    @Test
    void boardSpecialsRequireCanonicalBoardsAndEvaluateOnlyTheirFourSolutionAttempts() {
        AchievementHistorySnapshot.Result boardless = solved(1, GameType.QUADWORDS, START, 7);
        AchievementHistorySnapshot.Result consecutive = solvedWithBoards(
                2, START.plusDays(1), 7, boards(2, 3, 4, 5));
        AchievementHistorySnapshot.Result outlier = solvedWithBoards(
                3, START.plusDays(2), 8, boards(1, 2, 3, 7));
        AchievementEvaluation evaluation = evaluator.evaluate(snapshot(
                List.of(boardless, consecutive, outlier), active(GameType.QUADWORDS, START, null)));

        assertEarnedOn(evaluation, "situational.quadwords.consecutive_board_attempts", START.plusDays(1));
        assertEarnedOn(evaluation, "situational.quadwords.outlier_board", START.plusDays(2));

        AchievementEvaluation onlyBoardless = evaluator.evaluate(snapshot(
                List.of(boardless), active(GameType.QUADWORDS, START, null)));
        assertThat(onlyBoardless.find(key("situational.quadwords.consecutive_board_attempts"))).isEmpty();
        assertThat(onlyBoardless.find(key("situational.quadwords.outlier_board"))).isEmpty();
    }

    @Test
    void dejaVuUsesThreeConsecutiveSuccessfulCanonicalEndValuesAndIgnoresCalendarGaps() {
        List<AchievementHistorySnapshot.Result> grid = List.of(
                solved(1, GameType.GRIDWORDS, START, 5),
                solved(2, GameType.GRIDWORDS, START.plusDays(3), 5),
                solved(3, GameType.GRIDWORDS, START.plusDays(8), 5));
        AchievementEvaluation evaluation = evaluator.evaluate(snapshot(
                grid, active(GameType.GRIDWORDS, START, null)));
        assertEarnedOn(evaluation, "situational.deja_vu.gridwords", START.plusDays(8));

        List<AchievementHistorySnapshot.Result> interrupted = List.of(
                solved(11, GameType.QUADWORDS, START, 7),
                solved(12, GameType.QUADWORDS, START.plusDays(1), 7),
                failed(13, GameType.QUADWORDS, START.plusDays(2)),
                solved(14, GameType.QUADWORDS, START.plusDays(3), 7));
        assertThat(evaluator.evaluate(snapshot(interrupted, active(GameType.QUADWORDS, START, null)))
                .find(key("situational.deja_vu.quadwords"))).isEmpty();
    }

    @Test
    void threeFailuresAreSeparateAchievementsAndCalendarGapsDoNotInterruptThem() {
        List<AchievementHistorySnapshot.Result> failures = List.of(
                failed(1, GameType.GRIDWORDS, START),
                failed(2, GameType.GRIDWORDS, START.plusDays(4)),
                failed(3, GameType.GRIDWORDS, START.plusDays(9)));
        AchievementEvaluation evaluation = evaluator.evaluate(snapshot(
                failures, active(GameType.GRIDWORDS, START, null)));

        assertEarnedOn(evaluation, "situational.failure_run.3.gridwords", START.plusDays(9));
        assertThat(evaluation.find(key("situational.deja_vu.gridwords"))).isEmpty();

        List<AchievementHistorySnapshot.Result> interrupted = List.of(
                failed(11, GameType.QUADWORDS, START),
                failed(12, GameType.QUADWORDS, START.plusDays(1)),
                solved(13, GameType.QUADWORDS, START.plusDays(2), 7),
                failed(14, GameType.QUADWORDS, START.plusDays(3)));
        assertThat(evaluator.evaluate(snapshot(interrupted, active(GameType.QUADWORDS, START, null)))
                .find(key("situational.failure_run.3.quadwords"))).isEmpty();
    }

    @Test
    void timingBoundariesUseOriginalShareInstantInEuropeBerlinIncludingDst() {
        LocalDate summerDay = LocalDate.of(2026, 7, 1);
        AchievementHistorySnapshot.Result summerEarly = timedSolved(
                1, GameType.GRIDWORDS, summerDay, 4, LocalTime.of(6, 59, 59));
        AchievementEvaluation early = evaluator.evaluate(snapshot(
                List.of(summerEarly), active(GameType.GRIDWORDS, summerDay, null)));
        assertThat(early.find(key("timing.before_0700"))).isPresent();

        AchievementHistorySnapshot.Result exactlySeven = timedSolved(
                2, GameType.GRIDWORDS, summerDay, 4, LocalTime.of(7, 0));
        assertThat(evaluator.evaluate(snapshot(
                        List.of(exactlySeven), active(GameType.GRIDWORDS, summerDay, null)))
                .find(key("timing.before_0700"))).isEmpty();

        LocalDate winterDay = LocalDate.of(2026, 1, 15);
        AchievementHistorySnapshot.Result beforeNight = timedSolved(
                3, GameType.QUADWORDS, winterDay, 7, LocalTime.of(22, 59, 59));
        assertThat(evaluator.evaluate(snapshot(
                        List.of(beforeNight), active(GameType.QUADWORDS, winterDay, null)))
                .find(key("timing.after_2300"))).isEmpty();

        AchievementHistorySnapshot.Result exactlyNight = timedSolved(
                4, GameType.QUADWORDS, winterDay, 7, LocalTime.of(23, 0));
        AchievementEvaluation night = evaluator.evaluate(snapshot(
                List.of(exactlyNight), active(GameType.QUADWORDS, winterDay, null)));
        assertThat(night.find(key("timing.after_2300"))).isPresent();

        assertThat(summerEarly.receivedAt()).isEqualTo(
                ZonedDateTime.of(summerDay, LocalTime.of(6, 59, 59), BERLIN).toInstant());
        assertThat(exactlyNight.receivedAt()).isEqualTo(
                ZonedDateTime.of(winterDay, LocalTime.of(23, 0), BERLIN).toInstant());
    }

    @Test
    void emptyHistoryEvaluatesEveryCatalogRuleWithoutInfrastructureOrSpecialCases() {
        AchievementEvaluation evaluation = evaluator.evaluate(new AchievementHistorySnapshot(PLAYER, List.of(), List.of()));
        assertThat(evaluation.achievements()).isEmpty();
    }

    private static void assertEarnedOn(AchievementEvaluation evaluation, String key, LocalDate expected) {
        assertThat(evaluation.find(key(key))).get().extracting(AchievementEvidence::earnedOn).isEqualTo(expected);
    }

    private static AchievementKey key(String value) {
        return new AchievementKey(value);
    }

    private static AchievementHistorySnapshot snapshot(
            List<AchievementHistorySnapshot.Result> results, GameParticipationPeriod... periods) {
        return new AchievementHistorySnapshot(PLAYER, results, List.of(periods));
    }

    private static GameParticipationPeriod active(GameType game, LocalDate from, LocalDate until) {
        return new GameParticipationPeriod(PLAYER, game, from, until);
    }

    private static AchievementHistorySnapshot.Result solved(long id, GameType game, LocalDate day, int attempts) {
        return new AchievementHistorySnapshot.Result(
                id,
                game,
                day,
                true,
                OptionalInt.of(attempts),
                ZonedDateTime.of(day, LocalTime.NOON, BERLIN).toInstant(),
                Optional.empty());
    }

    private static AchievementHistorySnapshot.Result failed(long id, GameType game, LocalDate day) {
        return new AchievementHistorySnapshot.Result(
                id,
                game,
                day,
                false,
                OptionalInt.empty(),
                ZonedDateTime.of(day, LocalTime.NOON, BERLIN).toInstant(),
                Optional.empty());
    }

    private static AchievementHistorySnapshot.Result timedSolved(
            long id, GameType game, LocalDate day, int attempts, LocalTime localTime) {
        return new AchievementHistorySnapshot.Result(
                id,
                game,
                day,
                true,
                OptionalInt.of(attempts),
                ZonedDateTime.of(day, localTime, BERLIN).toInstant(),
                Optional.empty());
    }

    private static AchievementHistorySnapshot.Result solvedWithBoards(
            long id, LocalDate day, int attempts, QuadWordsBoards boards) {
        return new AchievementHistorySnapshot.Result(
                id,
                GameType.QUADWORDS,
                day,
                true,
                OptionalInt.of(attempts),
                ZonedDateTime.of(day, LocalTime.NOON, BERLIN).toInstant(),
                Optional.of(boards));
    }

    private static QuadWordsBoards boards(int topLeft, int topRight, int bottomLeft, int bottomRight) {
        return new QuadWordsBoards(
                board(topLeft), board(topRight), board(bottomLeft), board(bottomRight));
    }

    private static QuadWordsBoard board(int solvedAt) {
        List<String> rows = new ArrayList<>();
        for (int row = 1; row < solvedAt; row++) {
            rows.add("⬜⬜⬜⬜⬜");
        }
        rows.add("🟩🟩🟩🟩🟩");
        return new QuadWordsBoard(rows);
    }
}
