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
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportDayAndStreakProjectorTest {
    private static final long ONE = 1L;
    private static final long TWO = 2L;
    private static final long THREE = 3L;
    private static final ReportPeriod PERIOD = new ReportPeriod(date(2026, 7, 27), date(2026, 7, 29));

    @Test
    void derivesFinalPersonalAndSharedDayFeaturesAndAllSevenStreakSnapshots() {
        ReportDayAndStreakProjection projection = project(
                basis(participant(ONE, 27, 28, 29), participant(TWO, 27, 28, 29)),
                history(
                        participation(ONE, 24, null), participation(TWO, 24, null),
                        perfect(ONE, 24), perfect(ONE, 25), perfect(ONE, 26), perfect(ONE, 27),
                        complete(ONE, 28, true, false), complete(ONE, 29, true, false),
                        perfect(TWO, 24), perfect(TWO, 25), perfect(TWO, 26), perfect(TWO, 27),
                        perfect(TWO, 28), perfect(TWO, 29)));

        assertThat(projection.participants()).hasSize(2);
        var first = projection.participants().getFirst();
        assertThat(first.dayCounts().participationDays()).isEqualTo(3);
        assertThat(first.dayCounts().activityDays()).isEqualTo(3);
        assertThat(first.dayCounts().completeDays()).isEqualTo(3);
        assertThat(first.dayCounts().perfectDays()).isEqualTo(1);
        assertThat(first.streaks().activity()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(6, 6);
        assertThat(first.streaks().complete()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(6, 6);
        assertThat(first.streaks().gridWordsSolved()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(6, 6);
        assertThat(first.streaks().quadWordsSolved()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(0, 4);
        assertThat(first.streaks().perfect()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(0, 4);
        assertThat(projection.sharedDayCounts()).extracting("sharedPossibleDays", "completeDays", "perfectDays")
                .containsExactly(3, 3, 1);
        assertThat(projection.sharedStreaks().complete()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(6, 6);
        assertThat(projection.sharedStreaks().perfect()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(0, 4);
    }

    @Test
    void retainsARecordBeforeThePeriodAndExcludesFutureResultsAndParticipation() {
        ReportDayAndStreakProjection projection = project(
                basis(participant(ONE, 27, 28, 29)),
                history(
                        participation(ONE, 20, null), participation(TWO, 20, 27), participation(THREE, 30, null),
                        perfect(ONE, 20), perfect(ONE, 21), perfect(ONE, 22), perfect(ONE, 23), perfect(ONE, 24),
                        perfect(TWO, 20), perfect(TWO, 21), perfect(TWO, 22), perfect(TWO, 23), perfect(TWO, 24),
                        perfect(ONE, 30), perfect(TWO, 30), perfect(THREE, 30)));

        var participant = projection.participants().getFirst();
        assertThat(participant.dayCounts()).extracting("activityDays", "completeDays", "perfectDays")
                .containsExactly(0, 0, 0);
        assertThat(participant.streaks().perfect()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(0, 5);
        assertThat(projection.sharedStreaks().perfect()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(0, 5);
    }

    @Test
    void ignoresResultsOutsideParticipationAndBreaksSharedStreaksBelowTwoActivePlayers() {
        ReportDayAndStreakProjection projection = project(
                basis(participant(ONE, 27, 28, 29), participant(TWO, 27)),
                history(
                        participation(ONE, 27, null), participation(TWO, 27, 28),
                        perfect(ONE, 27), perfect(TWO, 27), perfect(ONE, 28), perfect(ONE, 29),
                        perfect(TWO, 28)));

        assertThat(projection.participants().get(1).dayCounts()).extracting("activityDays", "completeDays", "perfectDays")
                .containsExactly(1, 1, 1);
        assertThat(projection.sharedDayCounts()).extracting("sharedPossibleDays", "completeDays", "perfectDays")
                .containsExactly(1, 1, 1);
        assertThat(projection.sharedStreaks().complete()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(0, 1);
        assertThat(projection.sharedStreaks().perfect()).extracting("currentAtPeriodEnd", "allTimeRecordThroughPeriodEnd")
                .containsExactly(0, 1);
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

    private static ReportGameResult result(long playerId, GameType type, int day, boolean solved) {
        int maximum = type == GameType.GRIDWORDS ? 6 : 9;
        ShareOutcome outcome = solved ? new ShareOutcome.Solved(1, maximum) : new ShareOutcome.Unsolved(maximum);
        return new ReportGameResult(playerId, type, date(2026, 7, day), outcome, Duration.ofSeconds(30));
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
