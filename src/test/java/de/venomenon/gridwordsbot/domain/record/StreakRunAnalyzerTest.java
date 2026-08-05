package de.venomenon.gridwordsbot.domain.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.streak.StreakGameResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreakRunAnalyzerTest {
    private static final LocalDate START = LocalDate.of(2026, 3, 27);

    @Test
    void derivesPositiveAndNegativeRunsWithExplicitEndReasons() {
        List<GameParticipationPeriod> periods = bothGames(1, START, START.plusDays(10));
        List<StreakGameResult> results = new ArrayList<>();
        for (int offset = 0; offset < 3; offset++) {
            results.add(result(1, offset, GameType.GRIDWORDS, false));
            results.add(result(1, offset, GameType.QUADWORDS, true));
        }

        StreakRunAnalysis analysis = new StreakRunAnalyzer().analyze(results, periods,
                new StreakRunAnalysisWindow(START, START.plusDays(3), true));

        StreakRun drought = run(analysis, StreakRecordMetric.GRIDWORDS_DROUGHT, new RecordScope.Personal(1));
        assertEquals(3, drought.length());
        assertEquals(StreakRunStatus.ENDED_BY_DAY_CLOSE, drought.status());
        StreakRun withoutPerfect = run(analysis, StreakRecordMetric.WITHOUT_PERFECT_DAY,
                new RecordScope.Personal(1));
        assertEquals(4, withoutPerfect.length());
        assertEquals(StreakRunStatus.RUNNING, withoutPerfect.status());
        assertTrue(analysis.runs().stream().anyMatch(candidate ->
                candidate.identity().metric() == StreakRecordMetric.ACTIVITY && candidate.length() == 3));
    }

    @Test
    void anOpenMissingFinalDayKeepsThePreviousSolvedRunRunning() {
        List<GameParticipationPeriod> periods = bothGames(1, START, null);
        List<StreakGameResult> results = List.of(
                result(1, 0, GameType.GRIDWORDS, true), result(1, 0, GameType.QUADWORDS, true),
                result(1, 1, GameType.GRIDWORDS, true), result(1, 1, GameType.QUADWORDS, true));

        StreakRunAnalysis analysis = new StreakRunAnalyzer().analyze(results, periods,
                new StreakRunAnalysisWindow(START, START.plusDays(2), false));

        StreakRun run = run(analysis, StreakRecordMetric.GRIDWORDS_SOLVED, new RecordScope.Personal(1));
        assertEquals(2, run.length());
        assertEquals(StreakRunStatus.RUNNING, run.status());
    }

    @Test
    void sharedRunSurvivesChangingParticipantMembershipAndDstCalendarDate() {
        LocalDate day1 = LocalDate.of(2026, 3, 28);
        List<GameParticipationPeriod> periods = new ArrayList<>();
        periods.addAll(bothGames(1, day1, null));
        periods.addAll(bothGames(2, day1, day1.plusDays(2)));
        periods.addAll(bothGames(3, day1.plusDays(2), null));
        List<StreakGameResult> results = new ArrayList<>();
        for (int offset = 0; offset < 3; offset++) {
            List<Long> active = offset < 2 ? List.of(1L, 2L) : List.of(1L, 3L);
            for (long player : active) {
                results.add(new StreakGameResult(player, day1.plusDays(offset), GameType.GRIDWORDS, true));
                results.add(new StreakGameResult(player, day1.plusDays(offset), GameType.QUADWORDS, true));
            }
        }

        StreakRunAnalysis analysis = new StreakRunAnalyzer().analyze(results, periods,
                new StreakRunAnalysisWindow(day1, day1.plusDays(2), true));

        StreakRun shared = run(analysis, StreakRecordMetric.PERFECT, new RecordScope.Shared());
        assertEquals(3, shared.length());
        assertEquals(day1.plusDays(2), shared.endDate());
        assertEquals(StreakRunStatus.RUNNING, shared.status());
    }

    @Test
    void participationEndClosesWithoutPerfectRunInsteadOfPausingIt() {
        List<GameParticipationPeriod> periods = bothGames(1, START, START.plusDays(2));
        List<StreakGameResult> results = List.of(
                result(1, 0, GameType.GRIDWORDS, false), result(1, 0, GameType.QUADWORDS, true),
                result(1, 1, GameType.GRIDWORDS, false), result(1, 1, GameType.QUADWORDS, true));

        StreakRun run = run(new StreakRunAnalyzer().analyze(results, periods,
                        new StreakRunAnalysisWindow(START, START.plusDays(2), true)),
                StreakRecordMetric.WITHOUT_PERFECT_DAY, new RecordScope.Personal(1));
        assertEquals(2, run.length());
        assertEquals(StreakRunStatus.ENDED_BY_PARTICIPATION, run.status());
    }

    private static StreakRun run(StreakRunAnalysis analysis, StreakRecordMetric metric, RecordScope owner) {
        return analysis.runs().stream().filter(candidate -> candidate.identity().metric() == metric
                && candidate.identity().ownerScope().equals(owner)).findFirst().orElseThrow();
    }

    private static List<GameParticipationPeriod> bothGames(long player, LocalDate start, LocalDate end) {
        return List.of(new GameParticipationPeriod(player, GameType.GRIDWORDS, start, end),
                new GameParticipationPeriod(player, GameType.QUADWORDS, start, end));
    }

    private static StreakGameResult result(long player, int offset, GameType game, boolean solved) {
        return new StreakGameResult(player, START.plusDays(offset), game, solved);
    }
}
