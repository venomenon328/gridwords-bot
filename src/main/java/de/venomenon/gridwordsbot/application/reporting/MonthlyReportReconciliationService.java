package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlanner;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/** Thin monthly facade over the shared periodic report reconciliation workflow. */
public final class MonthlyReportReconciliationService {
    private final PeriodicReportReconciliationService reconciliationService;
    private final LocalTime monthlyReportTime;
    private final ZoneId zone;

    public MonthlyReportReconciliationService(
            PeriodicReportDeliveryStore store,
            PeriodicReportReconciliationPlanner planner,
            PeriodicReportUseCase reportUseCase,
            PeriodicReportDeliveryService deliveryService,
            Clock clock,
            LocalTime monthlyReportTime,
            ZoneId zone) {
        reconciliationService = new PeriodicReportReconciliationService(
                store, planner, reportUseCase, deliveryService, clock);
        this.monthlyReportTime = Objects.requireNonNull(monthlyReportTime, "monthlyReportTime");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** Reconciles one exact monthly delivery scope. */
    public void reconcile(long guildId, long channelId) {
        reconciliationService.reconcile(guildId, channelId, ReportType.MONTHLY, monthlyReportTime, zone);
    }
}
