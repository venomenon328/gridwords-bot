package de.venomenon.gridwordsbot.config;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Frequent trigger over durable idempotent use cases; host timezone and cron offsets are irrelevant. */
@Component
@Profile("database")
@ConditionalOnBean(DailyStatusRefreshService.class)
final class DailyStatusReminderScheduler {
    private final DailyStatusRefreshService status;
    private final ReminderDeliveryService reminders;
    private final DailyStatusStore deliveries;
    private final Clock clock;
    private final GridwordsBotProperties properties;

    DailyStatusReminderScheduler(DailyStatusRefreshService status, ReminderDeliveryService reminders,
            DailyStatusStore deliveries, Clock clock, GridwordsBotProperties properties) {
        this.status = status;
        this.reminders = reminders;
        this.deliveries = deliveries;
        this.clock = clock;
        this.properties = properties;
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
        long guildId = properties.discord().guildId();
        long channelId = properties.discord().channelId();
        LocalTime first = properties.schedule().firstReminder();
        LocalTime second = properties.schedule().secondReminder();

        deliveries.expireOpenRemindersBefore(guildId, channelId, today);
        status.reconcile(today.minusDays(1), true);
        boolean firstDue = !localTime.isBefore(first);
        boolean secondDue = !localTime.isBefore(second);
        status.reconcile(today, firstDue);

        if (secondDue) {
            deliveries.supersedeReminder(guildId, channelId, today, 1, first);
            reminders.deliver(today, 2, second);
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
