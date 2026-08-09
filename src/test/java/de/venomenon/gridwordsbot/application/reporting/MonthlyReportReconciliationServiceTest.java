package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryScope;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportNoOp;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationAction;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationCandidate;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlan;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlanner;
import de.venomenon.gridwordsbot.domain.reporting.ReportDueAt;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MonthlyReportReconciliationServiceTest {
    private static final long GUILD_ID = 41L;
    private static final long CHANNEL_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-08-03T06:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalTime MONTHLY_TIME = LocalTime.of(8, 15);

    @Test
    void reconcilesOnlyTheMonthlyScopeAndGeneratesAMonthlyReport() {
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportReconciliationPlanner planner = mock(PeriodicReportReconciliationPlanner.class);
        PeriodicReportUseCase reports = mock(PeriodicReportUseCase.class);
        PeriodicReportDeliveryService delivery = mock(PeriodicReportDeliveryService.class);
        PeriodicReportDeliveryScope scope = new PeriodicReportDeliveryScope(GUILD_ID, CHANNEL_ID, ReportType.MONTHLY);
        ReportPeriod period = ReportType.MONTHLY.periodStartingOn(LocalDate.of(2026, 7, 1));
        ReportDueAt dueAt = ReportType.MONTHLY.dueAt(period, MONTHLY_TIME, BERLIN);
        PeriodicReportReconciliationCandidate candidate = new PeriodicReportReconciliationCandidate(
                ReportType.MONTHLY,
                period,
                dueAt,
                dueAt.instant().plus(ReportType.MONTHLY.catchUpDuration()),
                PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
        when(store.findLatestPeriodStart(scope)).thenReturn(Optional.empty());
        when(planner.plan(ReportType.MONTHLY, NOW, MONTHLY_TIME, BERLIN, Optional.empty()))
                .thenReturn(new PeriodicReportReconciliationPlan(List.of(candidate)));
        when(reports.generate(GUILD_ID, ReportType.MONTHLY, period)).thenReturn(new PeriodicReportNoOp(ReportType.MONTHLY, period));
        MonthlyReportReconciliationService service = new MonthlyReportReconciliationService(
                store, planner, reports, delivery, Clock.fixed(NOW, ZoneOffset.UTC), MONTHLY_TIME, BERLIN);

        service.reconcile(GUILD_ID, CHANNEL_ID);

        verify(store).findLatestPeriodStart(scope);
        verify(planner).plan(ReportType.MONTHLY, NOW, MONTHLY_TIME, BERLIN, Optional.empty());
        verify(reports).generate(GUILD_ID, ReportType.MONTHLY, period);

        assertThatNullPointerException().isThrownBy(() -> new MonthlyReportReconciliationService(
                store, planner, reports, delivery, Clock.fixed(NOW, ZoneOffset.UTC), null, BERLIN));
        assertThatNullPointerException().isThrownBy(() -> new MonthlyReportReconciliationService(
                store, planner, reports, delivery, Clock.fixed(NOW, ZoneOffset.UTC), MONTHLY_TIME, null));
    }
}
