package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportNoOp;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportResult;
import de.venomenon.gridwordsbot.domain.reporting.ReportDayAndStreakProjection;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameResult;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantBasis;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantDayAndStreakSnapshot;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportPersonalDayCounts;
import de.venomenon.gridwordsbot.domain.reporting.ReportPersonalStreaks;
import de.venomenon.gridwordsbot.domain.reporting.ReportPlayerGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportSharedDayCounts;
import de.venomenon.gridwordsbot.domain.reporting.ReportSharedStreaks;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakHistory;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakSnapshot;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.port.out.ReportParticipantQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PeriodicReportUseCaseTest {
    private static final ReportPeriod PERIOD = new ReportPeriod(date(2026, 7, 27), date(2026, 7, 29));

    @Test
    void assemblesACompleteTwoParticipantReportFromTheExistingProjectors() {
        PeriodicReport report = complete(generate(
                List.of(profile(1, "One", 20, null), profile(2, "Two", 20, null)),
                List.of(perfect(1, 27), complete(1, 28, true, false), complete(1, 29, true, false),
                        perfect(2, 27), perfect(2, 28), perfect(2, 29)),
                history(participation(1, 20, null), participation(2, 20, null),
                        perfect(1, 27), complete(1, 28, true, false), complete(1, 29, true, false),
                        perfect(2, 27), perfect(2, 28), perfect(2, 29))));

        assertThat(report.reportType()).isEqualTo(ReportType.WEEKLY);
        assertThat(report.period()).isEqualTo(PERIOD);
        assertThat(report.participants()).extracting(section -> section.participant().discordUserId()).containsExactly(1L, 2L);
        assertThat(report.participants().getFirst().gameStatistics().gridWords()).extracting("possibleDays", "solved", "missing")
                .containsExactly(3, 3, 0);
        assertThat(report.participants().getFirst().gameStatistics().quadWords()).extracting("submitted", "solved", "unsolved")
                .containsExactly(3, 1, 2);
        assertThat(report.participants().getFirst().dayCounts()).extracting("participationDays", "activityDays", "completeDays", "perfectDays")
                .containsExactly(3, 3, 3, 1);
        assertThat(report.participants().getFirst().streaks().gridWordsSolved().currentAtPeriodEnd()).isEqualTo(3);
        assertThat(report.shared().dayCounts()).extracting("sharedPossibleDays", "completeDays", "perfectDays")
                .containsExactly(3, 3, 1);
        assertThat(report.shared().streaks().complete().currentAtPeriodEnd()).isEqualTo(3);
    }

    @Test
    void keepsTheParticipantBasisOrderAcrossThreeDynamicParticipants() {
        PeriodicReport report = complete(generate(
                List.of(profile(30, "Third", 20, null), profile(10, "First", 18, null), profile(20, "Second", 20, null)),
                List.of(), history(participation(10, 18, null), participation(20, 20, null), participation(30, 20, null))));

        assertThat(report.participants()).extracting(section -> section.participant().discordUserId()).containsExactly(10L, 20L, 30L);
        assertThat(report.participants()).extracting(section -> section.participant().displayName()).containsExactly("First", "Second", "Third");
    }

    @Test
    void representsOneParticipantWithZeroSharedValues() {
        PeriodicReport report = complete(generate(
                List.of(profile(1, "One", 27, null)), List.of(), history(participation(1, 27, null))));

        assertThat(report.participants()).hasSize(1);
        assertThat(report.shared().dayCounts()).isEqualTo(new ReportSharedDayCounts(0, 0, 0));
        assertThat(report.shared().streaks()).isEqualTo(zeroSharedStreaks());
    }

    @Test
    void returnsNoOpWithoutCallingResultOrStreakQueriesWhenThereAreNoParticipants() {
        ReportParticipantQuery participants = period -> List.of();
        var results = mock(de.venomenon.gridwordsbot.port.out.ReportGameResultQuery.class);
        var history = mock(de.venomenon.gridwordsbot.port.out.ReportStreakHistoryQuery.class);

        PeriodicReportResult result = new PeriodicReportUseCase(
                new ReportParticipantProjector(participants), new ReportGameStatisticsProjector(results),
                new ReportDayAndStreakProjector(history)).generate(ReportType.MONTHLY, PERIOD);

        assertThat(result).isEqualTo(new PeriodicReportNoOp(ReportType.MONTHLY, PERIOD));
        verify(results, never()).findResults(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anySet());
        verify(history, never()).findThrough(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retainsParticipantsWithoutResultsWithUndefinedAggregationsAndZeroStreaks() {
        PeriodicReport report = complete(generate(
                List.of(profile(1, "One", 27, null)), List.of(), history(participation(1, 27, null))));

        var personal = report.participants().getFirst();
        assertThat(personal.gameStatistics().gridWords()).extracting("submitted", "missing", "solutionRate", "bestSolvedDuration")
                .containsExactly(0, 3, Optional.empty(), Optional.empty());
        assertThat(personal.gameStatistics().quadWords().solutionRate()).isEmpty();
        assertThat(personal.streaks()).isEqualTo(zeroPersonalStreaks());
    }

    @Test
    void usesTheSameCoreForWeeklyAndMonthlyReports() {
        PeriodicReport weekly = complete(generateFor(ReportType.WEEKLY, PERIOD,
                List.of(profile(1, "One", 27, null)), List.of(), history(participation(1, 27, null))));
        ReportPeriod month = new ReportPeriod(date(2026, 7, 1), date(2026, 7, 31));
        PeriodicReport monthly = complete(generateFor(ReportType.MONTHLY, month,
                List.of(profile(1, "One", 1, null)), List.of(), history(participation(1, 1, null))));

        assertThat(weekly.reportType()).isEqualTo(ReportType.WEEKLY);
        assertThat(weekly.period()).isEqualTo(PERIOD);
        assertThat(monthly.reportType()).isEqualTo(ReportType.MONTHLY);
        assertThat(monthly.period()).isEqualTo(month);
        assertThat(weekly.participants()).hasSize(1);
        assertThat(monthly.participants()).hasSize(1);
    }

    @Test
    void mergesDeliberatelyDifferentProjectorOrdersByDiscordUserId() {
        ReportParticipant first = participant(1, "First");
        ReportParticipant second = participant(2, "Second");
        ReportParticipantBasis basis = basis(second, first);
        ReportParticipantProjector participants = mock(ReportParticipantProjector.class);
        ReportGameStatisticsProjector statistics = mock(ReportGameStatisticsProjector.class);
        ReportDayAndStreakProjector daysAndStreaks = mock(ReportDayAndStreakProjector.class);
        when(participants.project(PERIOD)).thenReturn(basis);
        when(statistics.project(basis)).thenReturn(List.of(playerStatistics(1), playerStatistics(2)));
        when(daysAndStreaks.project(basis)).thenReturn(new ReportDayAndStreakProjection(
                List.of(snapshot(1), snapshot(2)), new ReportSharedDayCounts(0, 0, 0), zeroSharedStreaks()));

        PeriodicReport report = complete(new PeriodicReportUseCase(participants, statistics, daysAndStreaks)
                .generate(ReportType.WEEKLY, PERIOD));

        assertThat(report.participants()).extracting(section -> section.participant().discordUserId()).containsExactly(2L, 1L);
        assertThat(report.participants()).extracting(section -> section.gameStatistics().discordUserId()).containsExactly(2L, 1L);
        assertThat(report.participants()).extracting(section -> section.dayCounts().participationDays()).containsExactly(1, 1);
    }

    @Test
    void rejectsMissingDuplicateAndForeignProjectorSections() {
        assertInvalidStatistics(List.of(), "missing participants");
        assertInvalidStatistics(List.of(playerStatistics(1), playerStatistics(1)), "duplicate Discord user ids");
        assertInvalidStatistics(List.of(playerStatistics(99)), "foreign Discord user id");
    }

    @Test
    void producesStructuralEqualResultsWithDefensiveCollections() {
        PeriodicReport first = complete(generate(
                List.of(profile(1, "One", 27, null)), List.of(), history(participation(1, 27, null))));
        PeriodicReport second = complete(generate(
                List.of(profile(1, "One", 27, null)), List.of(), history(participation(1, 27, null))));

        assertThat(first).isEqualTo(second);
        assertThatThrownBy(() -> first.participants().add(first.participants().getFirst())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void excludesFutureFactsFromTheAssembledReport() {
        PeriodicReport withoutFuture = complete(generate(
                List.of(profile(1, "One", 20, null)), List.of(perfect(1, 27)),
                history(participation(1, 20, null), perfect(1, 27))));
        PeriodicReport withFuture = complete(generate(
                List.of(profile(1, "One", 20, null)), List.of(perfect(1, 27), perfect(1, 30)),
                history(participation(1, 20, null), perfect(1, 27), perfect(1, 30))));

        assertThat(withFuture).isEqualTo(withoutFuture);
    }

    private static void assertInvalidStatistics(List<ReportPlayerGameStatistics> invalidStatistics, String message) {
        ReportParticipant participant = participant(1, "One");
        ReportParticipantBasis basis = basis(participant);
        ReportParticipantProjector participants = mock(ReportParticipantProjector.class);
        ReportGameStatisticsProjector statistics = mock(ReportGameStatisticsProjector.class);
        ReportDayAndStreakProjector daysAndStreaks = mock(ReportDayAndStreakProjector.class);
        when(participants.project(PERIOD)).thenReturn(basis);
        when(statistics.project(basis)).thenReturn(invalidStatistics);

        assertThatIllegalStateException().isThrownBy(() -> new PeriodicReportUseCase(participants, statistics, daysAndStreaks)
                .generate(ReportType.WEEKLY, PERIOD)).withMessageContaining(message);
        verify(daysAndStreaks, never()).project(basis);
    }

    private static PeriodicReportResult generate(
            List<ReportParticipantQuery.ParticipantProfile> profiles,
            List<List<ReportGameResult>> resultGroups,
            ReportStreakHistory history) {
        return generateWithResults(ReportType.WEEKLY, PERIOD, profiles, flatten(resultGroups), history);
    }

    private static PeriodicReportResult generateFor(
            ReportType type,
            ReportPeriod period,
            List<ReportParticipantQuery.ParticipantProfile> profiles,
            List<List<ReportGameResult>> resultGroups,
            ReportStreakHistory history) {
        return generateWithResults(type, period, profiles, flatten(resultGroups), history);
    }

    private static PeriodicReportResult generateWithResults(
            ReportType type,
            ReportPeriod period,
            List<ReportParticipantQuery.ParticipantProfile> profiles,
            List<ReportGameResult> results,
            ReportStreakHistory history) {
        return new PeriodicReportUseCase(
                new ReportParticipantProjector(requestedPeriod -> profiles),
                new ReportGameStatisticsProjector((requestedPeriod, participantIds) -> results),
                new ReportDayAndStreakProjector(cutoff -> history)).generate(type, period);
    }

    private static List<ReportGameResult> flatten(List<List<ReportGameResult>> resultGroups) {
        return resultGroups.stream().flatMap(List::stream).toList();
    }

    private static PeriodicReport complete(PeriodicReportResult result) {
        assertThat(result).isInstanceOf(PeriodicReport.class);
        return (PeriodicReport) result;
    }

    private static ReportParticipantQuery.ParticipantProfile profile(long id, String name, int startDay, Integer inactiveDay) {
        return new ReportParticipantQuery.ParticipantProfile(id, name, date(2026, 7, startDay),
                List.of(participation(id, startDay, inactiveDay)));
    }

    private static ParticipationPeriod participation(long id, int startDay, Integer inactiveDay) {
        return new ParticipationPeriod(id, date(2026, 7, startDay), inactiveDay == null ? null : date(2026, 7, inactiveDay));
    }

    private static List<ReportGameResult> perfect(long id, int day) {
        return List.of(result(id, GameType.GRIDWORDS, day, true), result(id, GameType.QUADWORDS, day, true));
    }

    private static List<ReportGameResult> complete(long id, int day, boolean gridSolved, boolean quadSolved) {
        return List.of(result(id, GameType.GRIDWORDS, day, gridSolved), result(id, GameType.QUADWORDS, day, quadSolved));
    }

    private static ReportGameResult result(long id, GameType gameType, int day, boolean solved) {
        int maximumAttempts = gameType == GameType.GRIDWORDS ? 6 : 9;
        ShareOutcome outcome = solved ? new ShareOutcome.Solved(1, maximumAttempts) : new ShareOutcome.Unsolved(maximumAttempts);
        return new ReportGameResult(id, gameType, date(2026, 7, day), outcome, Duration.ofSeconds(30));
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

    private static ReportParticipantBasis basis(ReportParticipant... participants) {
        Map<LocalDate, Set<Long>> active = new LinkedHashMap<>();
        for (LocalDate day = PERIOD.startDate(); !day.isAfter(PERIOD.endDate()); day = day.plusDays(1)) {
            java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
            for (ReportParticipant participant : participants) {
                if (participant.participationDays().contains(day)) ids.add(participant.discordUserId());
            }
            active.put(day, ids);
        }
        Set<LocalDate> shared = active.entrySet().stream().filter(entry -> entry.getValue().size() >= 2)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        return new ReportParticipantBasis(PERIOD, List.of(participants), active, shared);
    }

    private static ReportParticipant participant(long id, String name) {
        return new ReportParticipant(id, name, PERIOD.startDate(), List.of(PERIOD.startDate()));
    }

    private static ReportPlayerGameStatistics playerStatistics(long id) {
        return new ReportPlayerGameStatistics(id, zeroGameStatistics(GameType.GRIDWORDS), zeroGameStatistics(GameType.QUADWORDS));
    }

    private static ReportGameStatistics zeroGameStatistics(GameType gameType) {
        return new ReportGameStatistics(gameType, 1, 0, 0, 0, 1, Optional.empty(), 0, 0, Duration.ZERO, 0, Optional.empty());
    }

    private static ReportParticipantDayAndStreakSnapshot snapshot(long id) {
        return new ReportParticipantDayAndStreakSnapshot(id, new ReportPersonalDayCounts(1, 0, 0, 0), zeroPersonalStreaks());
    }

    private static ReportPersonalStreaks zeroPersonalStreaks() {
        ReportStreakSnapshot zero = new ReportStreakSnapshot(0, 0);
        return new ReportPersonalStreaks(zero, zero, zero, zero, zero);
    }

    private static ReportSharedStreaks zeroSharedStreaks() {
        ReportStreakSnapshot zero = new ReportStreakSnapshot(0, 0);
        return new ReportSharedStreaks(zero, zero);
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
