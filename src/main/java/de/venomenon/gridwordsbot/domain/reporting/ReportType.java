package de.venomenon.gridwordsbot.domain.reporting;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/** The fixed calendar and catch-up rules for a periodic report. */
public enum ReportType {
    WEEKLY(Duration.ofHours(72)),
    MONTHLY(Duration.ofDays(7));

    private final Duration catchUpDuration;

    ReportType(Duration catchUpDuration) {
        this.catchUpDuration = catchUpDuration;
    }

    public Duration catchUpDuration() {
        return catchUpDuration;
    }

    public ReportPeriod previousCompletedPeriod(Clock clock, ZoneId zone) {
        Objects.requireNonNull(clock, "clock");
        return previousCompletedPeriod(clock.instant(), zone);
    }

    public ReportPeriod previousCompletedPeriod(Instant referenceInstant, ZoneId zone) {
        Objects.requireNonNull(referenceInstant, "referenceInstant");
        Objects.requireNonNull(zone, "zone");
        LocalDate localDate = referenceInstant.atZone(zone).toLocalDate();
        return switch (this) {
            case WEEKLY -> previousWeek(localDate);
            case MONTHLY -> previousMonth(localDate);
        };
    }

    public ReportDueAt dueAt(ReportPeriod period, LocalTime localTime, ZoneId zone) {
        Objects.requireNonNull(period, "period");
        return new ReportDueAt(period.endDate().plusDays(1), localTime, zone);
    }

    public ReportCatchUpWindow catchUpWindow(ReportDueAt dueAt) {
        return new ReportCatchUpWindow(dueAt, catchUpDuration);
    }

    private static ReportPeriod previousWeek(LocalDate localDate) {
        LocalDate currentWeekStart = localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new ReportPeriod(currentWeekStart.minusWeeks(1), currentWeekStart.minusDays(1));
    }

    private static ReportPeriod previousMonth(LocalDate localDate) {
        LocalDate currentMonthStart = localDate.withDayOfMonth(1);
        return new ReportPeriod(currentMonthStart.minusMonths(1), currentMonthStart.minusDays(1));
    }
}
