package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.venomenon.gridwordsbot.application.reporting.WeeklyReportReconciliationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyReportSchedulerTest {

    @Test
    void mondayStartupAllowsOneChangedSucceededReportRefresh() {
        Fixture fixture = fixture(Instant.parse("2026-08-10T07:25:00Z"));

        fixture.scheduler.startupReconciliation();

        verify(fixture.reconciliation).reconcileRefreshingSucceededContent(11L, 12L);
        verify(fixture.reconciliation, never()).reconcile(11L, 12L);
    }

    @Test
    void nonMondayStartupUsesOrdinaryFrozenSnapshotReconciliation() {
        Fixture fixture = fixture(Instant.parse("2026-08-11T07:25:00Z"));

        fixture.scheduler.startupReconciliation();

        verify(fixture.reconciliation).reconcile(11L, 12L);
        verify(fixture.reconciliation, never()).reconcileRefreshingSucceededContent(11L, 12L);
    }

    @Test
    void everyTickDelegatesExactlyOnceWithoutRefreshMode() {
        Fixture fixture = fixture(Instant.parse("2026-08-10T07:25:00Z"));

        fixture.scheduler.reconcile();

        verify(fixture.reconciliation).reconcile(11L, 12L);
        verify(fixture.reconciliation, never()).reconcileRefreshingSucceededContent(11L, 12L);
    }

    @Test
    void directFailuresRemainVisibleAndDoNotPreventALaterTrigger() {
        Fixture fixture = fixture(Instant.parse("2026-08-11T07:25:00Z"));
        doThrow(new IllegalStateException("persistence unavailable"))
                .doNothing()
                .when(fixture.reconciliation).reconcile(11L, 12L);

        assertThatThrownBy(fixture.scheduler::reconcile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("persistence unavailable");
        fixture.scheduler.reconcile();

        verify(fixture.reconciliation, times(2)).reconcile(11L, 12L);
    }

    private static Fixture fixture(Instant now) {
        WeeklyReportReconciliationService reconciliation = mock(WeeklyReportReconciliationService.class);
        ZoneId zone = ZoneId.of("Europe/Berlin");
        GridwordsBotProperties properties = new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(false, "", 11L, 12L, List.of()),
                new GridwordsBotProperties.Schedule(
                        LocalTime.of(18, 0),
                        LocalTime.of(23, 0),
                        LocalTime.of(8, 0),
                        LocalTime.of(8, 15),
                        zone),
                new GridwordsBotProperties.Storage(48));
        return new Fixture(reconciliation, new WeeklyReportScheduler(
                reconciliation, properties, Clock.fixed(now, zone)));
    }

    private record Fixture(WeeklyReportReconciliationService reconciliation, WeeklyReportScheduler scheduler) { }
}
