package de.venomenon.gridwordsbot.domain.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Plans due report periods without performing persistence, delivery, or scheduling work. */
public final class PeriodicReportReconciliationPlanner {

    public PeriodicReportReconciliationPlan plan(
            ReportType type,
            Instant now,
            LocalTime reportTime,
            ZoneId zone,
            Optional<LocalDate> latestPersistedPeriodStart) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(reportTime, "reportTime");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(latestPersistedPeriodStart, "latestPersistedPeriodStart");

        ReportPeriod latestDuePeriod = latestDuePeriod(type, now, reportTime, zone);
        latestPersistedPeriodStart.ifPresent(anchor -> validateAnchor(type, anchor, latestDuePeriod));

        LocalDate firstPlannedPeriodStart = latestPersistedPeriodStart
                .filter(anchor -> anchor.isBefore(latestDuePeriod.startDate()))
                .map(type::nextPeriodStartAfter)
                .orElse(latestDuePeriod.startDate());

        List<PeriodicReportReconciliationCandidate> candidates = new ArrayList<>();
        for (LocalDate periodStart = firstPlannedPeriodStart;
                !periodStart.isAfter(latestDuePeriod.startDate());
                periodStart = type.nextPeriodStartAfter(periodStart)) {
            ReportPeriod period = type.periodStartingOn(periodStart);
            candidates.add(candidateFor(type, period, now, reportTime, zone,
                    period.equals(latestDuePeriod)));
        }
        return new PeriodicReportReconciliationPlan(candidates);
    }

    private ReportPeriod latestDuePeriod(ReportType type, Instant now, LocalTime reportTime, ZoneId zone) {
        ReportPeriod previousCompletedPeriod = type.previousCompletedPeriod(now, zone);
        ReportDueAt dueAt = type.dueAt(previousCompletedPeriod, reportTime, zone);
        if (!dueAt.instant().isAfter(now)) {
            return previousCompletedPeriod;
        }
        LocalDate precedingStart = switch (type) {
            case WEEKLY -> previousCompletedPeriod.startDate().minusWeeks(1);
            case MONTHLY -> previousCompletedPeriod.startDate().minusMonths(1);
        };
        return type.periodStartingOn(precedingStart);
    }

    private void validateAnchor(ReportType type, LocalDate anchor, ReportPeriod latestDuePeriod) {
        type.periodStartingOn(anchor);
        if (anchor.isAfter(latestDuePeriod.startDate())) {
            throw new IllegalArgumentException("latest persisted period start must not follow the latest due period");
        }
    }

    private PeriodicReportReconciliationCandidate candidateFor(
            ReportType type,
            ReportPeriod period,
            Instant now,
            LocalTime reportTime,
            ZoneId zone,
            boolean isLatestDuePeriod) {
        ReportDueAt dueAt = type.dueAt(period, reportTime, zone);
        Instant catchUpEndsAt = dueAt.instant().plus(type.catchUpDuration());
        PeriodicReportReconciliationAction action = isLatestDuePeriod && type.catchUpWindow(dueAt).isOpenAt(now)
                ? PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE
                : PeriodicReportReconciliationAction.EXPIRE;
        return new PeriodicReportReconciliationCandidate(type, period, dueAt, catchUpEndsAt, action);
    }
}
