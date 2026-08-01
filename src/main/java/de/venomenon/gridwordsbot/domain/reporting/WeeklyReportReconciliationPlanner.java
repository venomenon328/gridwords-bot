package de.venomenon.gridwordsbot.domain.reporting;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Plans the due weekly periods without performing persistence, delivery, or scheduling work. */
public final class WeeklyReportReconciliationPlanner {

    public WeeklyReportReconciliationPlan plan(
            Instant now,
            LocalTime weeklyReportTime,
            ZoneId zone,
            Optional<LocalDate> latestPersistedPeriodStart) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(weeklyReportTime, "weeklyReportTime");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(latestPersistedPeriodStart, "latestPersistedPeriodStart");

        ReportPeriod latestDuePeriod = latestDuePeriod(now, weeklyReportTime, zone);
        latestPersistedPeriodStart.ifPresent(anchor -> validateAnchor(anchor, latestDuePeriod));

        LocalDate firstPlannedPeriodStart = latestPersistedPeriodStart
                .filter(anchor -> anchor.isBefore(latestDuePeriod.startDate()))
                .map(anchor -> anchor.plusWeeks(1))
                .orElse(latestDuePeriod.startDate());

        List<WeeklyReportReconciliationCandidate> candidates = new ArrayList<>();
        for (LocalDate periodStart = firstPlannedPeriodStart;
                !periodStart.isAfter(latestDuePeriod.startDate());
                periodStart = periodStart.plusWeeks(1)) {
            ReportPeriod period = weeklyPeriodStartingOn(periodStart);
            candidates.add(candidateFor(period, now, weeklyReportTime, zone,
                    period.equals(latestDuePeriod)));
        }
        return new WeeklyReportReconciliationPlan(candidates);
    }

    private ReportPeriod latestDuePeriod(Instant now, LocalTime weeklyReportTime, ZoneId zone) {
        ReportPeriod previousCompletedPeriod = ReportType.WEEKLY.previousCompletedPeriod(now, zone);
        ReportDueAt dueAt = ReportType.WEEKLY.dueAt(previousCompletedPeriod, weeklyReportTime, zone);
        if (!dueAt.instant().isAfter(now)) {
            return previousCompletedPeriod;
        }
        return weeklyPeriodStartingOn(previousCompletedPeriod.startDate().minusWeeks(1));
    }

    private void validateAnchor(LocalDate anchor, ReportPeriod latestDuePeriod) {
        if (anchor.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("latest persisted weekly period start must be a Monday");
        }
        if (anchor.isAfter(latestDuePeriod.startDate())) {
            throw new IllegalArgumentException("latest persisted weekly period start must not follow the latest due period");
        }
    }

    private ReportPeriod weeklyPeriodStartingOn(LocalDate periodStart) {
        return new ReportPeriod(periodStart, periodStart.plusDays(6));
    }

    private WeeklyReportReconciliationCandidate candidateFor(
            ReportPeriod period,
            Instant now,
            LocalTime weeklyReportTime,
            ZoneId zone,
            boolean isLatestDuePeriod) {
        ReportDueAt dueAt = ReportType.WEEKLY.dueAt(period, weeklyReportTime, zone);
        Instant catchUpEndsAt = dueAt.instant().plus(ReportType.WEEKLY.catchUpDuration());
        WeeklyReportReconciliationAction action = isLatestDuePeriod
                && ReportType.WEEKLY.catchUpWindow(dueAt).isOpenAt(now)
                ? WeeklyReportReconciliationAction.DELIVER_OR_RECONCILE
                : WeeklyReportReconciliationAction.EXPIRE;
        return new WeeklyReportReconciliationCandidate(period, dueAt, catchUpEndsAt, action);
    }
}
