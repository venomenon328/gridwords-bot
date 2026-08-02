package de.venomenon.gridwordsbot.domain.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PeriodicReportReconciliationPlannerTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalTime REPORT_TIME = LocalTime.of(8, 0);
    private final PeriodicReportReconciliationPlanner planner = new PeriodicReportReconciliationPlanner();

    @Test
    void choosesTheWeekBeforeLastOnMondayBeforeTheConfiguredDueTime() {
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(plan("2026-08-03T05:59:59Z"));

        assertThat(candidate.period()).isEqualTo(period("2026-07-20"));
        assertThat(candidate.action()).isEqualTo(PeriodicReportReconciliationAction.EXPIRE);
    }

    @Test
    void choosesTheJustCompletedWeekExactlyAtTheConfiguredDueTime() {
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(plan("2026-08-03T06:00:00Z"));

        assertThat(candidate.period()).isEqualTo(period("2026-07-27"));
        assertThat(candidate.dueAt().instant()).isEqualTo(Instant.parse("2026-08-03T06:00:00Z"));
        assertThat(candidate.action()).isEqualTo(PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
    }

    @Test
    void deliversInsideTheSeventyTwoHourCatchUpWindow() {
        assertThat(onlyCandidate(plan("2026-08-04T12:00:00Z")).action())
                .isEqualTo(PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
    }

    @Test
    void deliversImmediatelyBeforeTheCatchUpWindowEnds() {
        assertThat(onlyCandidate(plan("2026-08-06T05:59:59.999999999Z")).action())
                .isEqualTo(PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
    }

    @Test
    void expiresExactlyAtTheCatchUpWindowEnd() {
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(plan("2026-08-06T06:00:00Z"));

        assertThat(candidate.action()).isEqualTo(PeriodicReportReconciliationAction.EXPIRE);
        assertThat(candidate.catchUpEndsAt()).isEqualTo(Instant.parse("2026-08-06T06:00:00Z"));
    }

    @Test
    void hasNoDeliveryCandidateAfterTheCatchUpWindowEnd() {
        assertThat(plan("2026-08-06T06:00:01Z").candidates())
                .allMatch(candidate -> candidate.action() == PeriodicReportReconciliationAction.EXPIRE);
    }

    @Test
    void plansAWeeklyPeriodAcrossAMonthBoundary() {
        assertThat(onlyCandidate(plan("2026-03-02T07:00:00Z")).period())
                .isEqualTo(new ReportPeriod(LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 1)));
    }

    @Test
    void plansAWeeklyPeriodAcrossAYearBoundary() {
        assertThat(onlyCandidate(plan("2026-01-05T07:00:00Z")).period())
                .isEqualTo(new ReportPeriod(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4)));
    }

    @Test
    void usesTheSummerTimeOffsetOnTheMondayAfterTheSpringTransition() {
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(plan("2026-03-30T06:00:00Z"));

        assertThat(candidate.dueAt().localDate()).isEqualTo(LocalDate.of(2026, 3, 30));
        assertThat(candidate.dueAt().instant()).isEqualTo(Instant.parse("2026-03-30T06:00:00Z"));
        assertThat(candidate.dueAt().instant().atZone(BERLIN).getOffset()).isEqualTo(ZoneOffset.ofHours(2));
    }

    @Test
    void usesTheStandardTimeOffsetOnTheMondayAfterTheFallTransition() {
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(plan("2026-10-26T07:00:00Z"));

        assertThat(candidate.dueAt().localDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(candidate.dueAt().instant()).isEqualTo(Instant.parse("2026-10-26T07:00:00Z"));
        assertThat(candidate.dueAt().instant().atZone(BERLIN).getOffset()).isEqualTo(ZoneOffset.ofHours(1));
    }

    @Test
    void plansOnlyTheLatestDuePeriodWithoutAnAnchor() {
        PeriodicReportReconciliationPlan plan = plan("2026-08-03T06:00:00Z");

        assertThat(plan.candidates()).extracting(candidate -> candidate.period().startDate())
                .containsExactly(LocalDate.of(2026, 7, 27));
    }

    @Test
    void keepsTheLatestPeriodAsTheReconciliationTargetWhenItEqualsTheAnchor() {
        PeriodicReportReconciliationPlan plan = plan("2026-08-03T06:00:00Z", "2026-07-27");

        assertThat(plan.candidates()).hasSize(1);
        assertThat(onlyCandidate(plan).action()).isEqualTo(PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
    }

    @Test
    void plansMissingWeeksInAscendingOrderAndOnlyDeliversTheLatestOne() {
        PeriodicReportReconciliationPlan plan = plan("2026-08-03T06:00:00Z", "2026-07-06");

        assertThat(plan.candidates()).extracting(candidate -> candidate.period().startDate())
                .containsExactly(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27));
        assertThat(plan.candidates()).extracting(PeriodicReportReconciliationCandidate::action)
                .containsExactly(PeriodicReportReconciliationAction.EXPIRE,
                        PeriodicReportReconciliationAction.EXPIRE,
                        PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
    }

    @Test
    void plansOnlyExpirationActionsWhenEveryCandidateIsOutsideTheCatchUpWindow() {
        PeriodicReportReconciliationPlan plan = plan("2026-08-06T06:00:00Z", "2026-07-06");

        assertThat(plan.candidates()).extracting(PeriodicReportReconciliationCandidate::action)
                .containsOnly(PeriodicReportReconciliationAction.EXPIRE);
    }

    @Test
    void rejectsAnAnchorThatIsNotAMonday() {
        assertThatIllegalArgumentException().isThrownBy(() -> plan("2026-08-03T06:00:00Z", "2026-07-28"));
    }

    @Test
    void rejectsAnAnchorAfterTheLatestDuePeriod() {
        assertThatIllegalArgumentException().isThrownBy(() -> plan("2026-08-03T06:00:00Z", "2026-08-03"));
    }

    @Test
    void producesTheSamePlanForRepeatedInputs() {
        Instant now = Instant.parse("2026-08-03T06:00:00Z");
        Optional<LocalDate> anchor = Optional.of(LocalDate.of(2026, 7, 6));

        assertThat(planner.plan(ReportType.WEEKLY, now, REPORT_TIME, BERLIN, anchor))
                .isEqualTo(planner.plan(ReportType.WEEKLY, now, REPORT_TIME, BERLIN, anchor));
    }

    private PeriodicReportReconciliationPlan plan(String now) {
        return planner.plan(ReportType.WEEKLY, Instant.parse(now), REPORT_TIME, BERLIN, Optional.empty());
    }

    private PeriodicReportReconciliationPlan plan(String now, String anchor) {
        return planner.plan(ReportType.WEEKLY, Instant.parse(now), REPORT_TIME, BERLIN, Optional.of(LocalDate.parse(anchor)));
    }

    private static PeriodicReportReconciliationCandidate onlyCandidate(PeriodicReportReconciliationPlan plan) {
        return onlyCandidate(plan.candidates());
    }

    private static PeriodicReportReconciliationCandidate onlyCandidate(List<PeriodicReportReconciliationCandidate> candidates) {
        assertThat(candidates).hasSize(1);
        return candidates.getFirst();
    }

    private static ReportPeriod period(String start) {
        LocalDate startDate = LocalDate.parse(start);
        return new ReportPeriod(startDate, startDate.plusDays(6));
    }
}
