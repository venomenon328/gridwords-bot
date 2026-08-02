package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.venomenon.gridwordsbot.application.reporting.WeeklyReportReconciliationService;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyReportSchedulerTest {

    @Test
    void startupEventDelegatesExactlyOnceToTheConfiguredDiscordScope() {
        Fixture fixture = fixture();

        fixture.scheduler.startupReconciliation();

        verify(fixture.reconciliation).reconcile(11L, 12L);
    }

    @Test
    void everyTickDelegatesExactlyOnceWithoutItsOwnDueCheck() {
        Fixture fixture = fixture();

        fixture.scheduler.reconcile();

        verify(fixture.reconciliation).reconcile(11L, 12L);
    }

    @Test
    void directFailuresRemainVisibleAndDoNotPreventALaterTrigger() {
        Fixture fixture = fixture();
        doThrow(new IllegalStateException("persistence unavailable"))
                .doNothing()
                .when(fixture.reconciliation).reconcile(11L, 12L);

        assertThatThrownBy(fixture.scheduler::reconcile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("persistence unavailable");
        fixture.scheduler.reconcile();

        verify(fixture.reconciliation, times(2)).reconcile(11L, 12L);
    }

    private static Fixture fixture() {
        WeeklyReportReconciliationService reconciliation = mock(WeeklyReportReconciliationService.class);
        GridwordsBotProperties properties = new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(false, "", 11L, 12L, List.of()),
                new GridwordsBotProperties.Schedule(
                        LocalTime.of(18, 0),
                        LocalTime.of(23, 0),
                        LocalTime.of(8, 0),
                        LocalTime.of(8, 15),
                        ZoneId.of("Europe/Berlin")),
                new GridwordsBotProperties.Storage(48));
        return new Fixture(reconciliation, new WeeklyReportScheduler(reconciliation, properties));
    }

    private record Fixture(WeeklyReportReconciliationService reconciliation, WeeklyReportScheduler scheduler) { }
}
