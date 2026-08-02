package de.venomenon.gridwordsbot.domain.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MonthlyReportReconciliationPlannerTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalTime REPORT_TIME = LocalTime.of(8, 15);
    private final PeriodicReportReconciliationPlanner planner = new PeriodicReportReconciliationPlanner();

    @Test
    void keepsTheJustCompletedMonthNotDueBeforeTheConfiguredTimeOnTheFirstDay() {
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(plan("2026-03-01T07:14:59Z"));

        assertThat(candidate.period()).isEqualTo(month("2026-01-01"));
        assertThat(candidate.action()).isEqualTo(PeriodicReportReconciliationAction.EXPIRE);
    }

    @Test
    void choosesTheJustCompletedMonthExactlyAtTheConfiguredDueTime() {
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(plan("2026-03-01T07:15:00Z"));

        assertThat(candidate.period()).isEqualTo(month("2026-02-01"));
        assertThat(candidate.dueAt().instant()).isEqualTo(Instant.parse("2026-03-01T07:15:00Z"));
        assertThat(candidate.action()).isEqualTo(PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
    }

    @Test
    void keepsTheMonthlyCatchUpWindowHalfOpen() {
        assertThat(onlyCandidate(plan("2026-03-05T12:00:00Z")).action())
                .isEqualTo(PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
        assertThat(onlyCandidate(plan("2026-03-08T07:14:59.999999999Z")).action())
                .isEqualTo(PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);

        PeriodicReportReconciliationCandidate expired = onlyCandidate(plan("2026-03-08T07:15:00Z"));
        assertThat(expired.catchUpEndsAt()).isEqualTo(Instant.parse("2026-03-08T07:15:00Z"));
        assertThat(expired.action()).isEqualTo(PeriodicReportReconciliationAction.EXPIRE);
        assertThat(onlyCandidate(plan("2026-03-08T07:15:01Z")).action())
                .isEqualTo(PeriodicReportReconciliationAction.EXPIRE);
    }

    @Test
    void plansCompletedMonthsWithEverySupportedCalendarLength() {
        assertThat(onlyCandidate(plan("2026-03-01T07:15:00Z")).period()).isEqualTo(month("2026-02-01"));
        assertThat(onlyCandidate(plan("2024-03-01T07:15:00Z")).period()).isEqualTo(month("2024-02-01"));
        assertThat(onlyCandidate(plan("2026-05-01T06:15:00Z")).period()).isEqualTo(month("2026-04-01"));
        assertThat(onlyCandidate(plan("2026-02-01T07:15:00Z")).period()).isEqualTo(month("2026-01-01"));
    }

    @Test
    void plansDecemberOfThePreviousYearWhenDueInJanuary() {
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(plan("2026-01-01T07:15:00Z"));

        assertThat(candidate.period()).isEqualTo(month("2025-12-01"));
        assertThat(candidate.dueAt().instant()).isEqualTo(Instant.parse("2026-01-01T07:15:00Z"));
    }

    @Test
    void resolvesMonthlyDueInstantsUsingBerlinOffsetsAfterBothDstTransitions() {
        PeriodicReportReconciliationCandidate spring = onlyCandidate(plan("2026-04-01T06:15:00Z"));
        PeriodicReportReconciliationCandidate autumn = onlyCandidate(plan("2026-11-01T07:15:00Z"));

        assertThat(spring.dueAt().instant()).isEqualTo(Instant.parse("2026-04-01T06:15:00Z"));
        assertThat(spring.dueAt().instant().atZone(BERLIN).getOffset()).isEqualTo(ZoneOffset.ofHours(2));
        assertThat(autumn.dueAt().instant()).isEqualTo(Instant.parse("2026-11-01T07:15:00Z"));
        assertThat(autumn.dueAt().instant().atZone(BERLIN).getOffset()).isEqualTo(ZoneOffset.ofHours(1));
    }

    @Test
    void plansOnlyTheLatestDueMonthWithoutAnAnchorAndReconcilesItWhenTheAnchorMatches() {
        assertThat(plan("2026-05-01T06:15:00Z").candidates()).extracting(candidate -> candidate.period().startDate())
                .containsExactly(LocalDate.of(2026, 4, 1));

        PeriodicReportReconciliationCandidate candidate = onlyCandidate(plan("2026-05-01T06:15:00Z", "2026-04-01"));
        assertThat(candidate.action()).isEqualTo(PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
    }

    @Test
    void plansMissingMonthsInAscendingOrderAndOnlyDeliversTheLatestOne() {
        PeriodicReportReconciliationPlan plan = plan("2026-05-01T06:15:00Z", "2026-01-01");

        assertThat(plan.candidates()).extracting(candidate -> candidate.period().startDate())
                .containsExactly(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));
        assertThat(plan.candidates()).extracting(PeriodicReportReconciliationCandidate::action)
                .containsExactly(PeriodicReportReconciliationAction.EXPIRE,
                        PeriodicReportReconciliationAction.EXPIRE,
                        PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
    }

    @Test
    void expiresEveryMonthlyCandidateOutsideTheCatchUpWindow() {
        PeriodicReportReconciliationPlan plan = plan("2026-05-08T06:15:00Z", "2026-01-01");

        assertThat(plan.candidates()).allMatch(candidate -> candidate.action() == PeriodicReportReconciliationAction.EXPIRE);
    }

    @Test
    void rejectsMonthlyAnchorsThatAreNotFirstDaysOrFollowTheLatestDueMonth() {
        assertThatIllegalArgumentException().isThrownBy(() -> plan("2026-05-01T06:15:00Z", "2026-04-02"));
        assertThatIllegalArgumentException().isThrownBy(() -> plan("2026-05-01T06:15:00Z", "2026-05-01"));
    }

    @Test
    void producesTheSameMonthlyPlanForRepeatedInputs() {
        Instant now = Instant.parse("2026-05-01T06:15:00Z");
        Optional<LocalDate> anchor = Optional.of(LocalDate.of(2026, 1, 1));

        assertThat(planner.plan(ReportType.MONTHLY, now, REPORT_TIME, BERLIN, anchor))
                .isEqualTo(planner.plan(ReportType.MONTHLY, now, REPORT_TIME, BERLIN, anchor));
    }

    private PeriodicReportReconciliationPlan plan(String now) {
        return planner.plan(ReportType.MONTHLY, Instant.parse(now), REPORT_TIME, BERLIN, Optional.empty());
    }

    private PeriodicReportReconciliationPlan plan(String now, String anchor) {
        return planner.plan(ReportType.MONTHLY, Instant.parse(now), REPORT_TIME, BERLIN, Optional.of(LocalDate.parse(anchor)));
    }

    private static PeriodicReportReconciliationCandidate onlyCandidate(PeriodicReportReconciliationPlan plan) {
        assertThat(plan.candidates()).hasSize(1);
        return plan.candidates().getFirst();
    }

    private static ReportPeriod month(String start) {
        LocalDate startDate = LocalDate.parse(start);
        return new ReportPeriod(startDate, startDate.withDayOfMonth(startDate.lengthOfMonth()));
    }
}
