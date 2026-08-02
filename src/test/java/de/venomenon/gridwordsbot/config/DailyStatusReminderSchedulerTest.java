package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.venomenon.gridwordsbot.application.reminder.ReminderDeliveryService;
import de.venomenon.gridwordsbot.application.status.DailyStatusRefreshService;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyStatusReminderSchedulerTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);

    @Test
    void startupBeforeFirstReminderRebuildsYesterdayButDoesNotCreateTodayPrematurely() {
        Fixture fixture = fixture("2026-07-30T15:00:00Z");
        fixture.scheduler.startupReconciliation();
        verify(fixture.status).reconcile(DATE.minusDays(1), true);
        verify(fixture.status).reconcile(DATE, false);
        verify(fixture.reminders, never()).deliver(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void betweenStagesCreatesStatusAndRunsOnlyFirstReminder() {
        Fixture fixture = fixture("2026-07-30T17:00:00Z");
        fixture.scheduler.reconcile();
        verify(fixture.status).reconcile(DATE, true);
        verify(fixture.reminders).deliver(DATE, 1, LocalTime.of(18, 0));
        verify(fixture.reminders, never()).deliver(DATE, 2, LocalTime.of(23, 0));
    }

    @Test
    void afterSecondStageSupersedesFirstAndSendsOnlyLatestCatchUp() {
        Fixture fixture = fixture("2026-07-30T21:30:00Z");
        fixture.scheduler.reconcile();
        verify(fixture.store, never()).supersedeReminder(11L, 12L, DATE, 1, LocalTime.of(18, 0));
        verify(fixture.reminders).deliver(DATE, 2, LocalTime.of(23, 0));
        verify(fixture.reminders, never()).deliver(DATE, 1, LocalTime.of(18, 0));
    }

    @Test
    void expiresPastOpenDeliveriesOnEveryReconciliation() {
        Fixture fixture = fixture("2026-07-30T15:00:00Z");
        fixture.scheduler.reconcile();
        verify(fixture.store).expireOpenRemindersBefore(11L, 12L, DATE);
    }

    @Test
    void springGapUsesBerlinCalendarRules() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        ZonedDateTime next = DailyStatusReminderScheduler.nextOccurrence(
                Instant.parse("2026-03-29T00:30:00Z"), LocalTime.of(2, 30), berlin);
        assertThat(next.toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 29));
        assertThat(next.toLocalTime()).isEqualTo(LocalTime.of(3, 30));
        assertThat(next.getOffset()).isEqualTo(ZoneOffset.ofHours(2));
    }

    @Test
    void fallOverlapPlansAmbiguousLocalTimeOnlyOnce() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        ZonedDateTime next = DailyStatusReminderScheduler.nextOccurrence(
                Instant.parse("2026-10-25T01:00:00Z"), LocalTime.of(2, 45), berlin);
        assertThat(next.toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(next.toLocalTime()).isEqualTo(LocalTime.of(2, 45));
        assertThat(next.getOffset()).isEqualTo(ZoneOffset.ofHours(1));
    }

    private static Fixture fixture(String instant) {
        DailyStatusRefreshService status = mock(DailyStatusRefreshService.class);
        ReminderDeliveryService reminders = mock(ReminderDeliveryService.class);
        DailyStatusStore store = mock(DailyStatusStore.class);
        GridwordsBotProperties properties = new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(false, "unused", 11L, 12L, List.of()),
                new GridwordsBotProperties.Schedule(LocalTime.of(18, 0), LocalTime.of(23, 0),
                        LocalTime.of(8, 0), LocalTime.of(8, 15), ZoneId.of("Europe/Berlin")),
                new GridwordsBotProperties.Storage(24));
        return new Fixture(status, reminders, store, new DailyStatusReminderScheduler(status, reminders, store,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), properties));
    }

    private record Fixture(DailyStatusRefreshService status, ReminderDeliveryService reminders,
                           DailyStatusStore store, DailyStatusReminderScheduler scheduler) { }
}
