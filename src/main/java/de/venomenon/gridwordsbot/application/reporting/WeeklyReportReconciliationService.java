package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlanner;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/** Thin weekly facade over the shared periodic report reconciliation workflow. */
public final class WeeklyReportReconciliationService {
    private final PeriodicReportReconciliationService reconciliationService;
    private final LocalTime weeklyReportTime;
    private final ZoneId zone;

    public WeeklyReportReconciliationService(
            PeriodicReportDeliveryStore store,
            PeriodicReportReconciliationPlanner planner,
            PeriodicReportUseCase reportUseCase,
            PeriodicReportDeliveryService deliveryService,
            Clock clock,
            LocalTime weeklyReportTime,
            ZoneId zone) {
        reconciliationService = new PeriodicReportReconciliationService(store, planner, reportUseCase, deliveryService, clock);
        this.weeklyReportTime = Objects.requireNonNull(weeklyReportTime, "weeklyReportTime");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** Reconciles one exact weekly delivery scope in chronological candidate order. */
    public void reconcile(long guildId, long channelId) {
        reconciliationService.reconcile(guildId, channelId, ReportType.WEEKLY, weeklyReportTime, zone);
    }

    /** Allows the latest due succeeded weekly report to adopt a changed visible fingerprint. */
    public void reconcileRefreshingSucceededContent(long guildId, long channelId) {
        reconciliationService.reconcileRefreshingSucceededContent(
                guildId, channelId, ReportType.WEEKLY, weeklyReportTime, zone);
    }
}
