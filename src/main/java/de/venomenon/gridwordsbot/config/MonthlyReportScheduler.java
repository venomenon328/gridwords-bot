package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.reporting.MonthlyReportReconciliationService;
import java.util.Objects;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Triggers durable monthly reconciliation at startup and on a fixed cadence without owning report decisions. */
@Component
@Profile("database")
@Conditional(PeriodicReportActivationCondition.class)
final class MonthlyReportScheduler {
    private final MonthlyReportReconciliationService reconciliation;
    private final GridwordsBotProperties properties;

    MonthlyReportScheduler(
            MonthlyReportReconciliationService reconciliation,
            GridwordsBotProperties properties) {
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @EventListener(ApplicationReadyEvent.class)
    void startupReconciliation() {
        reconcile();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void reconcile() {
        reconciliation.reconcile(
                properties.discord().guildId(), properties.discord().channelId());
    }
}
