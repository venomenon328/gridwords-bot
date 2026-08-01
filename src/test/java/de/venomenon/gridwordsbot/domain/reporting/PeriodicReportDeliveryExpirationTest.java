package de.venomenon.gridwordsbot.domain.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class PeriodicReportDeliveryExpirationTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void keepsTheImmutableFactsRequiredForAnInhaltsloserExpiration() {
        PeriodicReportDeliveryKey key = key(LocalDate.of(2026, 7, 27));
        PeriodicReportDeliveryMetadata metadata = metadata(LocalDate.of(2026, 7, 27));

        assertThat(new PeriodicReportDeliveryExpiration(key, metadata))
                .isEqualTo(new PeriodicReportDeliveryExpiration(key, metadata));
    }

    @Test
    void rejectsMismatchedOrMissingExpirationFacts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportDeliveryExpiration(
                key(LocalDate.of(2026, 7, 27)), metadata(LocalDate.of(2026, 8, 3))));
        assertThatNullPointerException().isThrownBy(() -> new PeriodicReportDeliveryExpiration(
                key(LocalDate.of(2026, 7, 27)), null));
    }

    @Test
    void validatesTheExactScopeForTheLatestPersistedPeriodQuery() {
        assertThat(new PeriodicReportDeliveryScope(1, 2, ReportType.MONTHLY))
                .isEqualTo(new PeriodicReportDeliveryScope(1, 2, ReportType.MONTHLY));
        assertThatIllegalArgumentException().isThrownBy(() -> new PeriodicReportDeliveryScope(0, 2, ReportType.WEEKLY));
        assertThatNullPointerException().isThrownBy(() -> new PeriodicReportDeliveryScope(1, 2, null));
    }

    private static PeriodicReportDeliveryKey key(LocalDate start) {
        return new PeriodicReportDeliveryKey(1, 2, ReportType.WEEKLY, start);
    }

    private static PeriodicReportDeliveryMetadata metadata(LocalDate start) {
        ReportPeriod period = new ReportPeriod(start, start.plusDays(6));
        ReportDueAt dueAt = new ReportDueAt(start.plusDays(7), LocalTime.of(8, 0), BERLIN);
        return new PeriodicReportDeliveryMetadata(period, dueAt, dueAt.instant().plus(ReportType.WEEKLY.catchUpDuration()));
    }
}
