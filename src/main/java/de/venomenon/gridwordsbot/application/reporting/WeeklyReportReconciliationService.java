package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryExpiration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryScope;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.domain.reporting.WeeklyReportReconciliationAction;
import de.venomenon.gridwordsbot.domain.reporting.WeeklyReportReconciliationCandidate;
import de.venomenon.gridwordsbot.domain.reporting.WeeklyReportReconciliationPlanner;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/** Reconciles the planned weekly report deliveries for one guild and channel without scheduler concerns. */
public final class WeeklyReportReconciliationService {
    private final PeriodicReportDeliveryStore store;
    private final WeeklyReportReconciliationPlanner planner;
    private final PeriodicReportUseCase reportUseCase;
    private final PeriodicReportDeliveryService deliveryService;
    private final Clock clock;
    private final LocalTime weeklyReportTime;
    private final ZoneId zone;

    public WeeklyReportReconciliationService(
            PeriodicReportDeliveryStore store,
            WeeklyReportReconciliationPlanner planner,
            PeriodicReportUseCase reportUseCase,
            PeriodicReportDeliveryService deliveryService,
            Clock clock,
            LocalTime weeklyReportTime,
            ZoneId zone) {
        this.store = Objects.requireNonNull(store, "store");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.reportUseCase = Objects.requireNonNull(reportUseCase, "reportUseCase");
        this.deliveryService = Objects.requireNonNull(deliveryService, "deliveryService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.weeklyReportTime = Objects.requireNonNull(weeklyReportTime, "weeklyReportTime");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** Reconciles one exact weekly delivery scope in chronological candidate order. */
    public void reconcile(long guildId, long channelId) {
        Instant now = clock.instant();
        PeriodicReportDeliveryScope scope = new PeriodicReportDeliveryScope(guildId, channelId, ReportType.WEEKLY);
        var plan = planner.plan(now, weeklyReportTime, zone, store.findLatestPeriodStart(scope));

        for (WeeklyReportReconciliationCandidate candidate : plan.candidates()) {
            PeriodicReportDeliveryKey key = new PeriodicReportDeliveryKey(
                    guildId, channelId, ReportType.WEEKLY, candidate.period().startDate());
            PeriodicReportDeliveryMetadata metadata = new PeriodicReportDeliveryMetadata(
                    candidate.period(), candidate.dueAt(), candidate.catchUpEndsAt());
            if (candidate.action() == WeeklyReportReconciliationAction.EXPIRE) {
                store.expire(new PeriodicReportDeliveryExpiration(key, metadata), now);
            } else {
                deliveryService.deliver(key, metadata, reportUseCase.generate(ReportType.WEEKLY, candidate.period()));
            }
        }
    }
}
