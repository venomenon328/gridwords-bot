package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryExpiration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryScope;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationAction;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationCandidate;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlanner;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/** Reconciles planned report deliveries for one exact report type without scheduler concerns. */
public final class PeriodicReportReconciliationService {
    private final PeriodicReportDeliveryStore store;
    private final PeriodicReportReconciliationPlanner planner;
    private final PeriodicReportUseCase reportUseCase;
    private final PeriodicReportDeliveryService deliveryService;
    private final Clock clock;

    public PeriodicReportReconciliationService(
            PeriodicReportDeliveryStore store,
            PeriodicReportReconciliationPlanner planner,
            PeriodicReportUseCase reportUseCase,
            PeriodicReportDeliveryService deliveryService,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.reportUseCase = Objects.requireNonNull(reportUseCase, "reportUseCase");
        this.deliveryService = Objects.requireNonNull(deliveryService, "deliveryService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Reconciles one exact delivery scope in the planner's chronological candidate order. */
    public void reconcile(long guildId, long channelId, ReportType reportType, LocalTime reportTime, ZoneId zone) {
        Objects.requireNonNull(reportType, "reportType");
        Objects.requireNonNull(reportTime, "reportTime");
        Objects.requireNonNull(zone, "zone");

        Instant now = clock.instant();
        PeriodicReportDeliveryScope scope = new PeriodicReportDeliveryScope(guildId, channelId, reportType);
        var plan = planner.plan(reportType, now, reportTime, zone, store.findLatestPeriodStart(scope));

        for (PeriodicReportReconciliationCandidate candidate : plan.candidates()) {
            PeriodicReportDeliveryKey key = new PeriodicReportDeliveryKey(
                    guildId, channelId, reportType, candidate.period().startDate());
            PeriodicReportDeliveryMetadata metadata = new PeriodicReportDeliveryMetadata(
                    candidate.period(), candidate.dueAt(), candidate.catchUpEndsAt());
            if (candidate.action() == PeriodicReportReconciliationAction.EXPIRE) {
                store.expire(new PeriodicReportDeliveryExpiration(key, metadata), now);
            } else {
                deliveryService.deliver(key, metadata, reportUseCase.generate(reportType, candidate.period()));
            }
        }
    }
}
