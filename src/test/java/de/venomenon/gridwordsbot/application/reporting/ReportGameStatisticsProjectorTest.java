package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameResult;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantBasis;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportPlayerGameStatistics;
import de.venomenon.gridwordsbot.port.out.ReportGameResultQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReportGameStatisticsProjectorTest {
    private static final ReportPeriod PERIOD = new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2));

    @Test
    void calculatesFullIndependentStatisticsForGridWordsAndQuadWords() {
        ReportPlayerGameStatistics statistics = project(basis(player(1, date(2026, 7, 27), date(2026, 7, 28))), List.of(
                solved(1, GameType.GRIDWORDS, date(2026, 7, 27), 3, 90),
                solved(1, GameType.GRIDWORDS, date(2026, 7, 28), 4, 75),
                solved(1, GameType.QUADWORDS, date(2026, 7, 27), 7, 210),
                solved(1, GameType.QUADWORDS, date(2026, 7, 28), 5, 195))).getFirst();

        assertGameStatistics(statistics.gridWords(), 2, 2, 2, 0, 0, 2, 2, 7, 165, 75);
        assertGameStatistics(statistics.quadWords(), 2, 2, 2, 0, 0, 2, 2, 12, 405, 195);
    }

    @Test
    void excludesUnsolvedAttemptsAndDurationsFromSolvedAggregates() {
        ReportGameStatistics statistics = project(basis(player(1, date(2026, 7, 27), date(2026, 7, 28), date(2026, 7, 29))), List.of(
                solved(1, GameType.GRIDWORDS, date(2026, 7, 27), 2, 100),
                unsolved(1, GameType.GRIDWORDS, date(2026, 7, 28), 999))).getFirst().gridWords();

        assertThat(statistics.possibleDays()).isEqualTo(3);
        assertThat(statistics.submitted()).isEqualTo(2);
        assertThat(statistics.solved()).isEqualTo(1);
        assertThat(statistics.unsolved()).isEqualTo(1);
        assertThat(statistics.missing()).isEqualTo(1);
        assertThat(statistics.solutionRate()).hasValueSatisfying(rate -> {
            assertThat(rate.numerator()).isEqualTo(1);
            assertThat(rate.denominator()).isEqualTo(2);
        });
        assertThat(statistics.solvedAttemptsTotal()).isEqualTo(2);
        assertThat(statistics.solvedAttemptsCount()).isEqualTo(1);
        assertThat(statistics.solvedDurationTotal()).isEqualTo(Duration.ofSeconds(100));
        assertThat(statistics.solvedDurationCount()).isEqualTo(1);
        assertThat(statistics.bestSolvedDuration()).contains(Duration.ofSeconds(100));
    }

    @Test
    void representsNoSubmissionsWithAnUndefinedSolutionRate() {
        ReportPlayerGameStatistics statistics = project(basis(player(1, date(2026, 7, 27), date(2026, 7, 28))), List.of()).getFirst();

        assertThat(statistics.gridWords().possibleDays()).isEqualTo(2);
        assertThat(statistics.gridWords().missing()).isEqualTo(2);
        assertThat(statistics.gridWords().solutionRate()).isEmpty();
        assertThat(statistics.gridWords().solvedAttemptsTotal()).isZero();
        assertThat(statistics.gridWords().solvedDurationTotal()).isZero();
        assertThat(statistics.gridWords().bestSolvedDuration()).isEmpty();
        assertThat(statistics.quadWords().solutionRate()).isEmpty();
    }

    @Test
    void countsOnlyParticipationDaysIncludingAReentryGap() {
        ReportParticipant participant = player(1, date(2026, 7, 27), date(2026, 7, 28), date(2026, 8, 1), date(2026, 8, 2));

        ReportGameStatistics statistics = project(basis(participant), List.of(
                solved(1, GameType.GRIDWORDS, date(2026, 7, 27), 4, 80),
                solved(1, GameType.GRIDWORDS, date(2026, 7, 29), 2, 60),
                solved(1, GameType.GRIDWORDS, date(2026, 8, 2), 5, 70))).getFirst().gridWords();

        assertGameStatistics(statistics, 4, 2, 2, 0, 2, 2, 2, 9, 150, 70);
    }

    @Test
    void usesInclusivePeriodBoundariesAndRequestsOnlyKnownParticipants() {
        AtomicReference<ReportPeriod> requestedPeriod = new AtomicReference<>();
        AtomicReference<Set<Long>> requestedIds = new AtomicReference<>();
        ReportGameResultQuery query = (period, participantIds) -> {
            requestedPeriod.set(period);
            requestedIds.set(participantIds);
            return List.of(
                    solved(1, GameType.QUADWORDS, date(2026, 7, 27), 4, 120),
                    solved(1, GameType.QUADWORDS, date(2026, 8, 2), 6, 110),
                    solved(1, GameType.QUADWORDS, date(2026, 7, 26), 5, 90),
                    solved(99, GameType.QUADWORDS, date(2026, 7, 27), 5, 90));
        };
        ReportParticipantBasis basis = basis(player(1, date(2026, 7, 27), date(2026, 8, 2)));

        ReportPlayerGameStatistics statistics = new ReportGameStatisticsProjector(query).project(basis).getFirst();

        assertThat(requestedPeriod).hasValue(PERIOD);
        assertThat(requestedIds).hasValue(Set.of(1L));
        assertGameStatistics(statistics.quadWords(), 2, 2, 2, 0, 0, 2, 2, 10, 230, 110);
    }

    @Test
    void keepsPlayersAndGamesSeparateAndIgnoresUnknownResults() {
        List<ReportPlayerGameStatistics> statistics = project(basis(
                player(1, date(2026, 7, 27)), player(2, date(2026, 7, 27))), List.of(
                solved(1, GameType.GRIDWORDS, date(2026, 7, 27), 1, 50),
                unsolved(2, GameType.QUADWORDS, date(2026, 7, 27), 30),
                solved(99, GameType.GRIDWORDS, date(2026, 7, 27), 1, 10)));

        assertThat(statistics).extracting(ReportPlayerGameStatistics::discordUserId).containsExactly(1L, 2L);
        assertThat(statistics.get(0).gridWords().solved()).isEqualTo(1);
        assertThat(statistics.get(0).quadWords().submitted()).isZero();
        assertThat(statistics.get(1).gridWords().submitted()).isZero();
        assertThat(statistics.get(1).quadWords().unsolved()).isEqualTo(1);
    }

    @Test
    void usesSeparateGameDenominatorsAndKeepsANonParticipatedGameAtZero() {
        ReportParticipant participant = new ReportParticipant(
                1L,
                "Grid only",
                date(2026, 7, 27),
                List.of(date(2026, 7, 27), date(2026, 7, 28)),
                List.of(date(2026, 7, 27), date(2026, 7, 28)),
                List.of(),
                List.of());

        ReportPlayerGameStatistics statistics = project(basis(participant), List.of(
                solved(1, GameType.GRIDWORDS, date(2026, 7, 27), 3, 70))).getFirst();

        assertThat(statistics.gridWords().possibleDays()).isEqualTo(2);
        assertThat(statistics.gridWords().submitted()).isEqualTo(1);
        assertThat(statistics.gridWords().missing()).isEqualTo(1);
        assertThat(statistics.quadWords().possibleDays()).isZero();
        assertThat(statistics.quadWords().submitted()).isZero();
        assertThat(statistics.quadWords().missing()).isZero();
        assertThat(statistics.quadWords().solutionRate()).isEmpty();
    }

    @Test
    void rejectsInconsistentDerivedStatistics() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReportGameStatistics(
                GameType.GRIDWORDS, 2, 1, 1, 0, 1, java.util.Optional.empty(),
                1, 1, Duration.ofSeconds(10), 1, java.util.Optional.of(Duration.ofSeconds(10))));
    }

    private static List<ReportPlayerGameStatistics> project(ReportParticipantBasis basis, List<ReportGameResult> results) {
        return new ReportGameStatisticsProjector((period, participantIds) -> results).project(basis);
    }

    private static ReportParticipantBasis basis(ReportParticipant... participants) {
        Map<LocalDate, Set<Long>> activeByDay = new LinkedHashMap<>();
        for (ReportParticipant participant : participants) {
            participant.unionParticipationDays().forEach(day -> activeByDay.merge(
                    day, Set.of(participant.discordUserId()), (left, right) -> {
                        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>(left);
                        ids.addAll(right);
                        return Set.copyOf(ids);
                    }));
        }
        activePeriodDays().forEach(day -> activeByDay.putIfAbsent(day, Set.of()));
        Map<LocalDate, Set<Long>> gridByDay = dailyParticipants(participants, true);
        Map<LocalDate, Set<Long>> quadByDay = dailyParticipants(participants, false);
        Map<LocalDate, Set<Long>> bothByDay = dailyParticipants(participants, null);
        return new ReportParticipantBasis(PERIOD, List.of(participants), activeByDay, gridByDay, quadByDay, bothByDay);
    }

    private static ReportParticipant player(long id, LocalDate... participationDays) {
        List<LocalDate> days = List.of(participationDays);
        return new ReportParticipant(id, "Player " + id, participationDays[0], days, days, days, days);
    }

    private static Map<LocalDate, Set<Long>> dailyParticipants(
            ReportParticipant[] participants, Boolean gridWords) {
        Map<LocalDate, Set<Long>> byDay = new LinkedHashMap<>();
        for (ReportParticipant participant : participants) {
            List<LocalDate> days = gridWords == null
                    ? participant.bothGamesParticipationDays()
                    : gridWords ? participant.gridWordsParticipationDays() : participant.quadWordsParticipationDays();
            days.forEach(day -> byDay.merge(day, Set.of(participant.discordUserId()), (left, right) -> {
                java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>(left);
                ids.addAll(right);
                return Set.copyOf(ids);
            }));
        }
        activePeriodDays().forEach(day -> byDay.putIfAbsent(day, Set.of()));
        return byDay;
    }

    private static List<LocalDate> activePeriodDays() {
        return PERIOD.startDate().datesUntil(PERIOD.endDate().plusDays(1)).toList();
    }

    private static ReportGameResult solved(long playerId, GameType type, LocalDate date, int attempts, long seconds) {
        return new ReportGameResult(playerId, type, date,
                new ShareOutcome.Solved(attempts, type == GameType.GRIDWORDS ? 6 : 9), Duration.ofSeconds(seconds));
    }

    private static ReportGameResult unsolved(long playerId, GameType type, LocalDate date, long seconds) {
        return new ReportGameResult(playerId, type, date,
                new ShareOutcome.Unsolved(type == GameType.GRIDWORDS ? 6 : 9), Duration.ofSeconds(seconds));
    }

    private static void assertGameStatistics(ReportGameStatistics statistics,
            int possible, int submitted, int solved, int unsolved, int missing,
            int rateNumerator, int rateDenominator, long attempts, long durationSeconds, long bestSeconds) {
        assertThat(statistics.possibleDays()).isEqualTo(possible);
        assertThat(statistics.submitted()).isEqualTo(submitted);
        assertThat(statistics.solved()).isEqualTo(solved);
        assertThat(statistics.unsolved()).isEqualTo(unsolved);
        assertThat(statistics.missing()).isEqualTo(missing);
        assertThat(statistics.solutionRate()).hasValueSatisfying(rate -> {
            assertThat(rate.numerator()).isEqualTo(rateNumerator);
            assertThat(rate.denominator()).isEqualTo(rateDenominator);
        });
        assertThat(statistics.solvedAttemptsTotal()).isEqualTo(attempts);
        assertThat(statistics.solvedAttemptsCount()).isEqualTo(solved);
        assertThat(statistics.solvedDurationTotal()).isEqualTo(Duration.ofSeconds(durationSeconds));
        assertThat(statistics.solvedDurationCount()).isEqualTo(solved);
        assertThat(statistics.bestSolvedDuration()).contains(Duration.ofSeconds(bestSeconds));
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}

