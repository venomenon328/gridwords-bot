package de.venomenon.gridwordsbot.domain.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ReportDomainTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void calculatesThePreviousCompleteWeek() {
        assertThat(ReportType.WEEKLY.previousCompletedPeriod(clock("2026-08-05T10:00:00Z"), BERLIN))
                .isEqualTo(new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2)));
    }

    @Test
    void calculatesThePreviousWeekAcrossAMonthBoundary() {
        assertThat(ReportType.WEEKLY.previousCompletedPeriod(clock("2026-03-02T08:00:00Z"), BERLIN))
                .isEqualTo(new ReportPeriod(LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 1)));
    }

    @Test
    void calculatesThePreviousWeekAcrossAYearBoundary() {
        assertThat(ReportType.WEEKLY.previousCompletedPeriod(clock("2026-01-05T08:00:00Z"), BERLIN))
                .isEqualTo(new ReportPeriod(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4)));
    }

    @Test
    void calculatesThePreviousCompleteMonth() {
        assertThat(ReportType.MONTHLY.previousCompletedPeriod(clock("2026-08-01T10:00:00Z"), BERLIN))
                .isEqualTo(new ReportPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)));
    }

    @Test
    void calculatesDecemberOfThePreviousYearForJanuary() {
        assertThat(ReportType.MONTHLY.previousCompletedPeriod(clock("2026-01-01T10:00:00Z"), BERLIN))
                .isEqualTo(new ReportPeriod(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)));
    }

    @Test
    void calculatesFebruaryForLeapAndNonLeapYears() {
        assertThat(ReportType.MONTHLY.previousCompletedPeriod(clock("2024-03-01T10:00:00Z"), BERLIN))
                .isEqualTo(new ReportPeriod(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)));
        assertThat(ReportType.MONTHLY.previousCompletedPeriod(clock("2025-03-01T10:00:00Z"), BERLIN))
                .isEqualTo(new ReportPeriod(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28)));
    }

    @Test
    void buildsDueAtFromBusinessLocalDateTimeAndZone() {
        ReportDueAt dueAt = ReportType.WEEKLY.dueAt(
                new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2)),
                LocalTime.of(8, 0), BERLIN);

        assertThat(dueAt).isEqualTo(new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.of(8, 0), BERLIN));
        assertThat(dueAt.instant()).isEqualTo(Instant.parse("2026-08-03T06:00:00Z"));
    }

    @Test
    void resolvesSpringDaylightSavingGapUsingBerlinRules() {
        ReportDueAt dueAt = new ReportDueAt(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), BERLIN);

        assertThat(dueAt.instant()).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
    }

    @Test
    void resolvesFallDaylightSavingOverlapToTheEarlierOffset() {
        ReportDueAt dueAt = new ReportDueAt(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), BERLIN);

        assertThat(dueAt.instant()).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"));
    }

    @Test
    void catchUpWindowIsHalfOpen() {
        ReportDueAt dueAt = new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.of(8, 0), BERLIN);
        ReportCatchUpWindow weekly = ReportType.WEEKLY.catchUpWindow(dueAt);
        Instant due = dueAt.instant();
        Instant end = due.plus(ReportType.WEEKLY.catchUpDuration());

        assertThat(weekly.isOpenAt(due.minusNanos(1))).isFalse();
        assertThat(weekly.isOpenAt(due)).isTrue();
        assertThat(weekly.isOpenAt(end.minusNanos(1))).isTrue();
        assertThat(weekly.isOpenAt(end)).isFalse();
        assertThat(ReportType.WEEKLY.catchUpDuration()).hasHours(72);
        assertThat(ReportType.MONTHLY.catchUpDuration()).hasDays(7);
    }

    @Test
    void rejectsInvalidPeriodAndTimeInvariants() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReportPeriod(
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 2)));
        assertThatNullPointerException().isThrownBy(() -> new ReportPeriod(null, LocalDate.of(2026, 8, 2)));
        assertThatIllegalArgumentException().isThrownBy(() -> new ReportCatchUpWindow(
                new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.NOON, BERLIN), java.time.Duration.ZERO));
    }

    @Test
    void exposesThePeriodEndAsAnInclusiveStatisticsAndStreakCutoff() {
        ReportPeriod period = new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));

        assertThat(period.statisticsAndStreakCutoff()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(period.contains(LocalDate.of(2026, 8, 2))).isTrue();
        assertThat(period.contains(LocalDate.of(2026, 8, 3))).isFalse();
    }

    private static Clock clock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }
}
