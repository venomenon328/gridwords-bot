package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.reminder.ReminderDeliveryService;
import de.venomenon.gridwordsbot.application.status.DailyStatusRefreshService;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;

/** Reconciliation is intentionally a frequent trigger over idempotent use cases, never a host-time-zone cron. */
@Component
@Profile("database")
@ConditionalOnBean(DailyStatusRefreshService.class)
final class DailyStatusReminderScheduler {
    private final DailyStatusRefreshService status;
    private final ReminderDeliveryService reminders;
    private final DailyStatusStore deliveries;
    private final Clock clock;
    private final GridwordsBotProperties properties;
    DailyStatusReminderScheduler(DailyStatusRefreshService status, ReminderDeliveryService reminders, DailyStatusStore deliveries,
            Clock clock, GridwordsBotProperties properties) {
        this.status = status; this.reminders = reminders; this.deliveries = deliveries; this.clock = clock; this.properties = properties;
    }
    @Scheduled(fixedDelay = 60_000, initialDelay = 2_000)
    void reconcile() {
        ZoneId zone = properties.schedule().timeZone();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), zone);
        LocalDate today = now.toLocalDate();
        deliveries.expireOpenRemindersBefore(properties.discord().guildId(), properties.discord().channelId(), today);
        status.refresh(today.minusDays(1));
        if (!now.toLocalTime().isBefore(properties.schedule().firstReminder())) status.refresh(today);
        if (!now.toLocalTime().isBefore(properties.schedule().secondReminder())) {
            reminders.deliver(today, 2, properties.schedule().secondReminder());
        } else if (!now.toLocalTime().isBefore(properties.schedule().firstReminder())) {
            reminders.deliver(today, 1, properties.schedule().firstReminder());
        }
    }
}
