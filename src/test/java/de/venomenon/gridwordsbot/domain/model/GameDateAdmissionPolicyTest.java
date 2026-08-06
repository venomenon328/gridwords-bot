package de.venomenon.gridwordsbot.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class GameDateAdmissionPolicyTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalTime DAY_CLOSE = LocalTime.of(6, 0);

    @Test
    void allowsYesterdayOnlyUntilTheSecondBeforeDayClose() {
        GameDateAdmissionPolicy before = policy("2026-07-29T03:59:59Z"); // 05:59:59 CEST
        GameDateAdmissionPolicy at = policy("2026-07-29T04:00:00Z"); // 06:00:00 CEST
        LocalDate yesterday = LocalDate.of(2026, 7, 28);

        assertThat(before.allows(yesterday)).isTrue();
        assertThat(at.allows(yesterday)).isFalse();
        assertThat(at.allows(LocalDate.of(2026, 7, 29))).isTrue();
        assertThat(at.allows(LocalDate.of(2026, 7, 27))).isFalse();
    }

    @Test
    void usesBerlinWallClockAcrossDaylightSavingTime() {
        GameDateAdmissionPolicy before = policy("2026-03-29T03:59:59Z"); // 05:59:59 CEST after spring change
        GameDateAdmissionPolicy at = policy("2026-03-29T04:00:00Z"); // 06:00:00 CEST
        LocalDate yesterday = LocalDate.of(2026, 3, 28);

        assertThat(before.allows(yesterday)).isTrue();
        assertThat(at.allows(yesterday)).isFalse();
    }

    private static GameDateAdmissionPolicy policy(String now) {
        return new GameDateAdmissionPolicy(Clock.fixed(Instant.parse(now), ZoneId.of("UTC")), BERLIN, DAY_CLOSE);
    }
}
