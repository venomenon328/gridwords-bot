package de.venomenon.gridwordsbot.application.cleanup;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.application.status.DailyStatusRefreshService;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DailyChannelCleanupServiceTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 31);

    @Test
    void waitsUntilConfiguredBerlinCleanupTime() {
        Fixture fixture = fixture("2026-07-31T03:59:00Z");

        fixture.service.reconcile();

        verify(fixture.status, never()).reconcile(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(fixture.retirement, never()).retireResultMessagesBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void stopsBeforeVisibleDeletionWhenYesterdayStatusIsNotDurablyFinalised() {
        Fixture fixture = fixture("2026-07-31T04:00:00Z");
        when(fixture.status.reconcile(TODAY.minusDays(1), true)).thenReturn(false);

        fixture.service.reconcile();

        verify(fixture.status).reconcile(TODAY.minusDays(1), true);
        verify(fixture.retirement, never()).retireResultMessagesBefore(TODAY);
        verify(fixture.deliveries, never()).expireOpenRemindersBefore(11L, 12L, TODAY);
    }

    @Test
    void ordersYesterdayStatusResultsRemindersAndTodayStatus() {
        Fixture fixture = fixture("2026-07-31T04:00:00Z");
        when(fixture.status.reconcile(TODAY.minusDays(1), true)).thenReturn(true);
        when(fixture.retirement.retireResultMessagesBefore(TODAY)).thenReturn(true);
        when(fixture.retirement.retireReminderMessagesBefore(TODAY)).thenReturn(true);

        fixture.service.reconcile();

        InOrder order = inOrder(fixture.status, fixture.retirement, fixture.deliveries);
        order.verify(fixture.status).reconcile(TODAY.minusDays(1), true);
        order.verify(fixture.retirement).retireResultMessagesBefore(TODAY);
        order.verify(fixture.deliveries).expireOpenRemindersBefore(11L, 12L, TODAY);
        order.verify(fixture.retirement).retireReminderMessagesBefore(TODAY);
        order.verify(fixture.status).reconcile(TODAY, true);
    }

    private static Fixture fixture(String instant) {
        DailyStatusRefreshService status = mock(DailyStatusRefreshService.class);
        ChannelMessageRetirementService retirement = mock(ChannelMessageRetirementService.class);
        DailyStatusStore deliveries = mock(DailyStatusStore.class);
        DailyChannelCleanupService service = new DailyChannelCleanupService(status, retirement, deliveries,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), BERLIN, LocalTime.of(6, 0), 11L, 12L);
        return new Fixture(status, retirement, deliveries, service);
    }

    private record Fixture(DailyStatusRefreshService status, ChannelMessageRetirementService retirement,
                           DailyStatusStore deliveries, DailyChannelCleanupService service) {
    }
}
