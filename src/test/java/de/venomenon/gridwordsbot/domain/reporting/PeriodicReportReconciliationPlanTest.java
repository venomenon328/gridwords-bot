package de.venomenon.gridwordsbot.domain.reporting;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeriodicReportReconciliationPlanTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalTime REPORT_TIME = LocalTime.of(8, 0);

    @Test
    void rejectsACandidateWhosePeriodDoesNotMatchItsType() {
        ReportPeriod weeklyPeriod = new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
        ReportDueAt dueAt = ReportType.MONTHLY.dueAt(weeklyPeriod, REPORT_TIME, BERLIN);

        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportReconciliationCandidate(
                ReportType.MONTHLY,
                weeklyPeriod,
                dueAt,
                dueAt.instant().plus(ReportType.MONTHLY.catchUpDuration()),
                PeriodicReportReconciliationAction.EXPIRE));
    }

    @Test
    void rejectsPlansWithMixedTypesOrNonConsecutivePeriods() {
        PeriodicReportReconciliationCandidate weekly = candidate(ReportType.WEEKLY, LocalDate.of(2026, 7, 20));
        PeriodicReportReconciliationCandidate monthly = candidate(ReportType.MONTHLY, LocalDate.of(2026, 8, 1));
        PeriodicReportReconciliationCandidate skippedWeek = candidate(ReportType.WEEKLY, LocalDate.of(2026, 8, 3));

        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportReconciliationPlan(List.of(weekly, monthly)));
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportReconciliationPlan(List.of(weekly, skippedWeek)));
    }

    @Test
    void rejectsADeliverActionBeforeTheLatestCandidate() {
        PeriodicReportReconciliationCandidate older = candidate(ReportType.MONTHLY, LocalDate.of(2026, 1, 1));
        PeriodicReportReconciliationCandidate latest = candidate(ReportType.MONTHLY, LocalDate.of(2026, 2, 1));

        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportReconciliationPlan(List.of(older, latest)));
    }

    private static PeriodicReportReconciliationCandidate candidate(ReportType type, LocalDate start) {
        ReportPeriod period = type.periodStartingOn(start);
        ReportDueAt dueAt = type.dueAt(period, REPORT_TIME, BERLIN);
        return new PeriodicReportReconciliationCandidate(
                type,
                period,
                dueAt,
                dueAt.instant().plus(type.catchUpDuration()),
                PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
    }
}
