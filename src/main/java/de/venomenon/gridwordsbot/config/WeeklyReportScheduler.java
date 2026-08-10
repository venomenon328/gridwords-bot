package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.reporting.WeeklyReportReconciliationService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.Objects;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Triggers durable weekly reconciliation at startup and on a fixed cadence without owning report decisions. */
@Component
@Profile("database")
@Conditional(PeriodicReportActivationCondition.class)
final class WeeklyReportScheduler {
    private final WeeklyReportReconciliationService reconciliation;
    private final GridwordsBotProperties properties;
    private final Clock clock;

    WeeklyReportScheduler(
            WeeklyReportReconciliationService reconciliation,
            GridwordsBotProperties properties,
            Clock clock) {
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventListener(ApplicationReadyEvent.class)
    void startupReconciliation() {
        if (clock.instant().atZone(properties.schedule().timeZone()).getDayOfWeek() == DayOfWeek.MONDAY) {
            reconciliation.reconcileRefreshingSucceededContent(
                    properties.discord().guildId(), properties.discord().channelId());
        } else {
            reconcile();
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void reconcile() {
        reconciliation.reconcile(
                properties.discord().guildId(), properties.discord().channelId());
    }
}
