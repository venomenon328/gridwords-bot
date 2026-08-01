package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.reporting.ReportDayAndStreakProjection;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameResult;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantBasis;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakHistory;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakSnapshot;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportDayAndStreakProjectorStreakMatrixTest {
    private static final long ONE = 1L;
    private static final long TWO = 2L;
    private static final long THREE = 3L;
    private static final ReportPeriod PERIOD = new ReportPeriod(date(2026, 7, 27), date(2026, 7, 29));

    @Test
    void calculatesPersonalActivityCurrentAndRecordIndependently() {
        ReportStreakSnapshot snapshot = project(singleParticipantBasis(), history(
                participation(ONE, 24, null),
                gridSolved(ONE, 24), gridSolved(ONE, 25), gridSolved(ONE, 27),
                gridSolved(ONE, 28), gridSolved(ONE, 29)))
                .participants().getFirst().streaks().activity();

        assertSnapshot(snapshot, 3, 3);
    }

    @Test
    void calculatesPersonalCompleteCurrentAndRecordIndependently() {
        ReportStreakSnapshot snapshot = project(singleParticipantBasis(), history(
                participation(ONE, 24, null),
                perfect(ONE, 24), perfect(ONE, 25), gridSolved(ONE, 26),
                complete(ONE, 27, true, false), complete(ONE, 28, false, true), perfect(ONE, 29)))
                .participants().getFirst().streaks().complete();

        assertSnapshot(snapshot, 3, 3);
    }

    @Test
    void calculatesPersonalGridWordsSolvedCurrentAndRecordIndependently() {
        ReportStreakSnapshot snapshot = project(singleParticipantBasis(), history(
                participation(ONE, 24, null),
                gridSolved(ONE, 24), gridSolved(ONE, 25), gridUnsolved(ONE, 26),
                gridSolved(ONE, 27), gridSolved(ONE, 28), gridSolved(ONE, 29)))
                .participants().getFirst().streaks().gridWordsSolved();

        assertSnapshot(snapshot, 3, 3);
    }

    @Test
    void calculatesPersonalQuadWordsSolvedCurrentAndRecordIndependently() {
        ReportStreakSnapshot snapshot = project(singleParticipantBasis(), history(
                participation(ONE, 24, null),
                quadSolved(ONE, 24), quadSolved(ONE, 25), quadSolved(ONE, 26), quadUnsolved(ONE, 27),
                quadSolved(ONE, 28), quadSolved(ONE, 29)))
                .participants().getFirst().streaks().quadWordsSolved();

        assertSnapshot(snapshot, 2, 3);
    }

    @Test
    void calculatesPersonalPerfectCurrentAndRecordIndependently() {
        ReportStreakSnapshot snapshot = project(singleParticipantBasis(), history(
                participation(ONE, 24, null),
                perfect(ONE, 24), perfect(ONE, 25), gridSolved(ONE, 26),
                perfect(ONE, 27), perfect(ONE, 28), perfect(ONE, 29)))
                .participants().getFirst().streaks().perfect();

        assertSnapshot(snapshot, 3, 3);
    }

    @Test
    void calculatesSharedCompleteCurrentAndRecordIndependently() {
        ReportStreakSnapshot snapshot = project(twoParticipantsBasis(), history(
                participation(ONE, 24, null), participation(TWO, 24, null),
                perfect(ONE, 24), perfect(TWO, 24), perfect(ONE, 25), perfect(TWO, 25),
                gridSolved(ONE, 26), gridSolved(TWO, 26),
                complete(ONE, 27, true, false), complete(TWO, 27, false, true),
                complete(ONE, 28, false, true), complete(TWO, 28, true, false),
                perfect(ONE, 29), perfect(TWO, 29)))
                .sharedStreaks().complete();

        assertSnapshot(snapshot, 3, 3);
    }

    @Test
    void calculatesSharedPerfectCurrentAndRecordIndependently() {
        ReportStreakSnapshot snapshot = project(twoParticipantsBasis(), history(
                participation(ONE, 24, null), participation(TWO, 24, null),
                perfect(ONE, 24), perfect(TWO, 24), perfect(ONE, 25), perfect(TWO, 25), perfect(ONE, 26), perfect(TWO, 26),
                complete(ONE, 27, false, true), perfect(TWO, 27),
                perfect(ONE, 28), perfect(TWO, 28), perfect(ONE, 29), perfect(TWO, 29)))
                .sharedStreaks().perfect();

        assertSnapshot(snapshot, 2, 3);
    }

    @Test
    void projectsJoinLeaveAndReentryWithoutBridgingPersonalGaps() {
        ReportDayAndStreakProjection projection = project(
                basis(participant(ONE, 27, 28, 29), participant(TWO, 27, 29), participant(THREE, 28)),
                history(
                        participation(ONE, 27, null),
                        participation(TWO, 27, 28), participation(TWO, 29, null),
                        participation(THREE, 28, 29),
                        perfect(ONE, 27), perfect(TWO, 27), perfect(ONE, 28), perfect(THREE, 28), perfect(ONE, 29), perfect(TWO, 29)));

        assertThat(projection.participants()).extracting(participant -> participant.discordUserId())
                .containsExactly(ONE, TWO, THREE);
        assertThat(projection.participants().get(1).dayCounts().participationDays()).isEqualTo(2);
        assertSnapshot(projection.participants().get(1).streaks().activity(), 1, 1);
        assertThat(projection.sharedDayCounts()).extracting("sharedPossibleDays", "completeDays", "perfectDays")
                .containsExactly(3, 3, 3);
    }

    @Test
    void missingActivePlayerResultAtPeriodEndFinallyPreventsSharedComplete() {
        ReportStreakSnapshot snapshot = project(twoParticipantsBasis(), history(
                participation(ONE, 27, null), participation(TWO, 27, null),
                perfect(ONE, 27), perfect(TWO, 27), perfect(ONE, 28), perfect(TWO, 28), perfect(ONE, 29)))
                .sharedStreaks().complete();

        assertSnapshot(snapshot, 0, 2);
    }

    @Test
    void retainsParticipantWithoutAnyResultWithAllZeroDayAndStreakValues() {
        ReportDayAndStreakProjection projection = project(twoParticipantsBasis(), history(
                participation(ONE, 27, null), participation(TWO, 27, null),
                perfect(ONE, 27), perfect(ONE, 28), perfect(ONE, 29)));

        assertThat(projection.participants()).extracting(participant -> participant.discordUserId()).containsExactly(ONE, TWO);
        var withoutResults = projection.participants().get(1);
        assertThat(withoutResults.dayCounts()).extracting("participationDays", "activityDays", "completeDays", "perfectDays")
                .containsExactly(3, 0, 0, 0);
        assertSnapshot(withoutResults.streaks().activity(), 0, 0);
        assertSnapshot(withoutResults.streaks().complete(), 0, 0);
        assertSnapshot(withoutResults.streaks().gridWordsSolved(), 0, 0);
        assertSnapshot(withoutResults.streaks().quadWordsSolved(), 0, 0);
        assertSnapshot(withoutResults.streaks().perfect(), 0, 0);
    }

    @Test
    void fewerThanTwoActivePlayersExplicitlyBreaksASharedSeries() {
        ReportStreakSnapshot snapshot = project(
                basis(participant(ONE, 27, 28, 29), participant(TWO, 27, 29)),
                history(
                        participation(ONE, 27, null), participation(TWO, 27, 28), participation(TWO, 29, null),
                        perfect(ONE, 27), perfect(TWO, 27), perfect(ONE, 28), perfect(ONE, 29), perfect(TWO, 29)))
                .sharedStreaks().perfect();

        assertSnapshot(snapshot, 1, 1);
    }

    private static ReportParticipantBasis singleParticipantBasis() {
        return basis(participant(ONE, 27, 28, 29));
    }

    private static ReportParticipantBasis twoParticipantsBasis() {
        return basis(participant(ONE, 27, 28, 29), participant(TWO, 27, 28, 29));
    }

    private static ReportDayAndStreakProjection project(ReportParticipantBasis basis, ReportStreakHistory history) {
        return new ReportDayAndStreakProjector(cutoff -> history).project(basis);
    }

    private static ReportParticipantBasis basis(ReportParticipant... participants) {
        Map<LocalDate, Set<Long>> activeByDay = new LinkedHashMap<>();
        for (LocalDate day = PERIOD.startDate(); !day.isAfter(PERIOD.endDate()); day = day.plusDays(1)) {
            java.util.LinkedHashSet<Long> active = new java.util.LinkedHashSet<>();
            for (ReportParticipant participant : participants) {
                if (participant.participationDays().contains(day)) active.add(participant.discordUserId());
            }
            activeByDay.put(day, Set.copyOf(active));
        }
        Set<LocalDate> sharedDays = activeByDay.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new ReportParticipantBasis(PERIOD, List.of(participants), activeByDay, sharedDays);
    }

    private static ReportParticipant participant(long id, int... days) {
        List<LocalDate> participationDays = java.util.Arrays.stream(days).mapToObj(day -> date(2026, 7, day)).toList();
        return new ReportParticipant(id, "Player " + id, participationDays.getFirst(), participationDays);
    }

    private static ReportStreakHistory history(Object... facts) {
        List<ParticipationPeriod> periods = new java.util.ArrayList<>();
        List<ReportGameResult> results = new java.util.ArrayList<>();
        for (Object fact : facts) {
            if (fact instanceof ParticipationPeriod period) periods.add(period);
            else results.addAll((List<ReportGameResult>) fact);
        }
        return new ReportStreakHistory(periods, results);
    }

    private static ParticipationPeriod participation(long playerId, int startDay, Integer inactiveDay) {
        return new ParticipationPeriod(playerId, date(2026, 7, startDay),
                inactiveDay == null ? null : date(2026, 7, inactiveDay));
    }

    private static List<ReportGameResult> perfect(long playerId, int day) {
        return List.of(result(playerId, GameType.GRIDWORDS, day, true), result(playerId, GameType.QUADWORDS, day, true));
    }

    private static List<ReportGameResult> complete(long playerId, int day, boolean gridSolved, boolean quadSolved) {
        return List.of(result(playerId, GameType.GRIDWORDS, day, gridSolved), result(playerId, GameType.QUADWORDS, day, quadSolved));
    }

    private static List<ReportGameResult> gridSolved(long playerId, int day) {
        return List.of(result(playerId, GameType.GRIDWORDS, day, true));
    }

    private static List<ReportGameResult> gridUnsolved(long playerId, int day) {
        return List.of(result(playerId, GameType.GRIDWORDS, day, false));
    }

    private static List<ReportGameResult> quadSolved(long playerId, int day) {
        return List.of(result(playerId, GameType.QUADWORDS, day, true));
    }

    private static List<ReportGameResult> quadUnsolved(long playerId, int day) {
        return List.of(result(playerId, GameType.QUADWORDS, day, false));
    }

    private static ReportGameResult result(long playerId, GameType type, int day, boolean solved) {
        int maximum = type == GameType.GRIDWORDS ? 6 : 9;
        ShareOutcome outcome = solved ? new ShareOutcome.Solved(1, maximum) : new ShareOutcome.Unsolved(maximum);
        return new ReportGameResult(playerId, type, date(2026, 7, day), outcome, Duration.ofSeconds(30));
    }

    private static void assertSnapshot(ReportStreakSnapshot snapshot, int current, int record) {
        assertThat(snapshot).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(current, record);
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
