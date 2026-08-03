package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.cleanup.ChannelMessageRetirementService;
import de.venomenon.gridwordsbot.application.cleanup.DailyChannelCleanupService;
import de.venomenon.gridwordsbot.application.reminder.ReminderDeliveryService;
import de.venomenon.gridwordsbot.application.status.DailyStatusRefreshService;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Frequent trigger over durable idempotent use cases; host timezone and cron offsets are irrelevant. */
@Component
@Profile("database")
@ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
final class DailyStatusReminderScheduler {
    private final DailyStatusRefreshService status;
    private final ReminderDeliveryService reminders;
    private final DailyStatusStore deliveries;
    private final ChannelMessageRetirementService retirement;
    private final DailyChannelCleanupService cleanup;
    private final Clock clock;
    private final GridwordsBotProperties properties;

    @Autowired
    DailyStatusReminderScheduler(
            DailyStatusRefreshService status,
            ReminderDeliveryService reminders,
            DailyStatusStore deliveries,
            ChannelMessageRetirementService retirement,
            DailyChannelCleanupService cleanup,
            Clock clock,
            GridwordsBotProperties properties) {
        this.status = status;
        this.reminders = reminders;
        this.deliveries = deliveries;
        this.retirement = retirement;
        this.cleanup = cleanup;
        this.clock = clock;
        this.properties = properties;
    }

    DailyStatusReminderScheduler(
            DailyStatusRefreshService status,
            ReminderDeliveryService reminders,
            DailyStatusStore deliveries,
            Clock clock,
            GridwordsBotProperties properties) {
        this(status, reminders, deliveries, null, null, clock, properties);
    }

    @EventListener(ApplicationReadyEvent.class)
    void startupReconciliation() {
        reconcile();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void reconcile() {
        ZoneId zone = properties.schedule().timeZone();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), zone);
        LocalDate today = now.toLocalDate();
        LocalTime localTime = now.toLocalTime();
        LocalTime first = properties.schedule().firstReminder();
        LocalTime second = properties.schedule().secondReminder();

        if (cleanup != null) {
            cleanup.reconcile();
        }
        boolean firstDue = !localTime.isBefore(first);
        boolean secondDue = !localTime.isBefore(second);
        status.reconcile(today, firstDue);

        if (secondDue) {
            reminders.deliver(today, 2, second);
            if (retirement != null) {
                retirement.reconcileFirstReminderRetention(today);
            }
        } else if (firstDue) {
            reminders.deliver(today, 1, first);
        }
    }

    static ZonedDateTime nextOccurrence(Instant now, LocalTime localTime, ZoneId zone) {
        ZonedDateTime current = now.atZone(zone);
        ZonedDateTime candidate = current.toLocalDate().atTime(localTime).atZone(zone);
        return candidate.toInstant().isAfter(now) ? candidate : candidate.plusDays(1);
    }
}
