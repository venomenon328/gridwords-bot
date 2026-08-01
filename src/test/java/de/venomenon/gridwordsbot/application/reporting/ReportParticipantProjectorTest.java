package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantBasis;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.port.out.ReportParticipantQuery;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportParticipantProjectorTest {
    private static final ReportPeriod WEEK = period(2026, 7, 27, 2026, 8, 2);

    @Test
    void includesAPlayerJoiningMidPeriodOnlyFromTheirJoinDay() {
        ReportParticipantBasis basis = project(WEEK, player(1, "Joined", date(2026, 7, 30),
                participation(1, date(2026, 7, 30), null)));

        assertThat(basis.participants().getFirst().participationDays())
                .containsExactly(date(2026, 7, 30), date(2026, 7, 31), date(2026, 8, 1), date(2026, 8, 2));
    }

    @Test
    void includesBoundaryJoinsAtTheStartAndEndOfThePeriod() {
        ReportParticipantBasis basis = project(WEEK,
                player(1, "First", date(2026, 7, 27), participation(1, date(2026, 7, 27), null)),
                player(2, "Last", date(2026, 8, 2), participation(2, date(2026, 8, 2), null)));

        assertThat(basis.participants()).extracting(ReportParticipant::discordUserId).containsExactly(1L, 2L);
        assertThat(basis.participants().get(1).participationDays()).containsExactly(date(2026, 8, 2));
    }

    @Test
    void treatsInactiveFromAsExclusiveAndExcludesAPeriodEndingAtReportStart() {
        ReportParticipantBasis basis = project(WEEK,
                player(1, "Leaving", date(2026, 7, 20), participation(1, date(2026, 7, 20), date(2026, 7, 30))),
                player(2, "Before", date(2026, 7, 20), participation(2, date(2026, 7, 20), date(2026, 7, 27))));

        assertThat(basis.participants()).extracting(ReportParticipant::discordUserId).containsExactly(1L);
        assertThat(basis.participants().getFirst().participationDays())
                .containsExactly(date(2026, 7, 27), date(2026, 7, 28), date(2026, 7, 29));
    }

    @Test
    void ignoresAProspectiveLeaveBeyondThePeriodAndClipsOpenParticipationAtPeriodEnd() {
        ReportParticipantBasis basis = project(WEEK,
                player(1, "Future leave", date(2026, 7, 1), participation(1, date(2026, 7, 1), date(2026, 8, 10))),
                player(2, "Open", date(2026, 7, 1), participation(2, date(2026, 7, 1), null)));

        assertThat(basis.participants()).allSatisfy(player -> assertThat(player.participationDays()).hasSize(7));
    }

    @Test
    void combinesReentriesWithoutDuplicatingParticipationDays() {
        ReportParticipantBasis basis = project(WEEK, player(1, "Returning", date(2026, 7, 1),
                participation(1, date(2026, 7, 25), date(2026, 7, 30)),
                participation(1, date(2026, 8, 1), null),
                participation(1, date(2026, 8, 1), null)));

        assertThat(basis.participants().getFirst().participationDays()).containsExactly(
                date(2026, 7, 27), date(2026, 7, 28), date(2026, 7, 29), date(2026, 8, 1), date(2026, 8, 2));
    }

    @Test
    void projectsChangingDailyParticipantsAndSharedPossibleDays() {
        ReportParticipantBasis basis = project(WEEK,
                player(1, "One", date(2026, 7, 1), participation(1, date(2026, 7, 1), null)),
                player(2, "Two", date(2026, 7, 28), participation(2, date(2026, 7, 28), date(2026, 8, 1))),
                player(3, "Three", date(2026, 8, 1), participation(3, date(2026, 8, 1), null)));

        assertThat(basis.activeParticipantIdsByDay().get(date(2026, 7, 27))).containsExactly(1L);
        assertThat(basis.activeParticipantIdsByDay().get(date(2026, 7, 28))).containsExactly(1L, 2L);
        assertThat(basis.activeParticipantIdsByDay().get(date(2026, 8, 1))).containsExactly(1L, 3L);
        assertThat(basis.sharedPossibleDays()).containsExactly(date(2026, 7, 28), date(2026, 7, 29), date(2026, 7, 30), date(2026, 7, 31), date(2026, 8, 1), date(2026, 8, 2));
    }

    @Test
    void hasNoSharedPossibleDaysForOneParticipantAndDoesNotReadResults() {
        ReportParticipantBasis basis = project(WEEK, player(1, "Only", date(2026, 7, 1), participation(1, date(2026, 7, 1), null)));

        assertThat(basis.sharedPossibleDays()).isEmpty();
        assertThat(basis.participants()).hasSize(1);
    }

    @Test
    void keepsCurrentNameButSortsByFirstParticipationThenDiscordId() {
        ReportParticipantBasis basis = project(WEEK,
                player(4, "Renamed", date(2026, 7, 1), participation(4, date(2026, 7, 1), null)),
                player(2, "Same start", date(2026, 7, 1), participation(2, date(2026, 7, 1), null)),
                player(3, "Later", date(2026, 7, 2), participation(3, date(2026, 7, 2), null)));

        assertThat(basis.participants()).extracting(ReportParticipant::discordUserId).containsExactly(2L, 4L, 3L);
        assertThat(basis.participants().get(1).displayName()).isEqualTo("Renamed");
    }

    @Test
    void excludesAProfileWhosePeriodsDoNotProvideAParticipationDay() {
        ReportParticipantBasis basis = project(WEEK, player(1, "Before", date(2026, 7, 1),
                participation(1, date(2026, 7, 1), date(2026, 7, 27))));

        assertThat(basis.participants()).isEmpty();
        assertThat(basis.activeParticipantIdsByDay().values()).allSatisfy(ids -> assertThat(ids).isEmpty());
    }

    @Test
    void includesAPlayerJoiningMidMonthOnlyFromTheirJoinDay() {
        ReportPeriod month = period(2026, 7, 1, 2026, 7, 31);
        ReportParticipantBasis basis = project(month, player(1, "Mid-month", date(2026, 7, 15),
                participation(1, date(2026, 7, 15), null)));

        assertThat(basis.participants().getFirst().participationDays()).startsWith(date(2026, 7, 15));
        assertThat(basis.participants().getFirst().participationDays()).endsWith(date(2026, 7, 31));
        assertThat(basis.participants().getFirst().participationDays()).hasSize(17);
    }

    @Test
    void doesNotDuplicateDaysAcrossAdjacentParticipationPeriods() {
        ReportParticipantBasis basis = project(WEEK, player(1, "Adjacent", date(2026, 7, 1),
                participation(1, date(2026, 7, 1), date(2026, 7, 30)),
                participation(1, date(2026, 7, 30), null)));

        assertThat(basis.participants().getFirst().participationDays()).hasSize(7);
        assertThat(basis.activeParticipantIdsByDay().values()).allSatisfy(ids -> assertThat(ids).containsExactly(1L));
    }
    private static ReportParticipantBasis project(ReportPeriod period, ReportParticipantQuery.ParticipantProfile... profiles) {
        return new ReportParticipantProjector(ignored -> List.of(profiles)).project(period);
    }

    private static ReportParticipantQuery.ParticipantProfile player(
            long id, String name, LocalDate firstParticipation, ParticipationPeriod... periods) {
        return new ReportParticipantQuery.ParticipantProfile(id, name, firstParticipation, List.of(periods));
    }

    private static ParticipationPeriod participation(long playerId, LocalDate activeFrom, LocalDate inactiveFrom) {
        return new ParticipationPeriod(playerId, activeFrom, inactiveFrom);
    }

    private static ReportPeriod period(int startYear, int startMonth, int startDay, int endYear, int endMonth, int endDay) {
        return new ReportPeriod(date(startYear, startMonth, startDay), date(endYear, endMonth, endDay));
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
