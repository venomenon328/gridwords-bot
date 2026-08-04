package de.venomenon.gridwordsbot.domain.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecordComparatorsTest {

    @Test
    void fewestAttemptsAlwaysPrefersFewerAttemptsBeforeDuration() {
        var comparator = RecordComparators.fewestAttempts();

        assertThat(comparator.compare(value(2, 600), value(3, 30))).isEqualTo(RecordComparison.BETTER);
        assertThat(comparator.compare(value(4, 10), value(3, 600))).isEqualTo(RecordComparison.WORSE);
        assertThat(comparator.compare(value(3, 40), value(3, 45))).isEqualTo(RecordComparison.BETTER);
        assertThat(comparator.compare(value(3, 45), value(3, 45))).isEqualTo(RecordComparison.EQUAL);
    }

    @Test
    void fastestDurationIgnoresEveryFactExceptDuration() {
        var comparator = RecordComparators.fastestDuration();

        assertThat(comparator.compare(duration(40), duration(45))).isEqualTo(RecordComparison.BETTER);
        assertThat(comparator.compare(duration(50), duration(45))).isEqualTo(RecordComparison.WORSE);
        assertThat(comparator.compare(duration(45), duration(45))).isEqualTo(RecordComparison.EQUAL);
    }

    @Test
    void slowestDurationUsesTheInverseDurationOrder() {
        var comparator = RecordComparators.slowestDuration();

        assertThat(comparator.compare(duration(50), duration(45))).isEqualTo(RecordComparison.BETTER);
        assertThat(comparator.compare(duration(40), duration(45))).isEqualTo(RecordComparison.WORSE);
        assertThat(comparator.compare(duration(45), duration(45))).isEqualTo(RecordComparison.EQUAL);
    }

    @Test
    void longestStreakOnlyComparesLength() {
        var comparator = RecordComparators.longestStreak();

        assertThat(comparator.compare(streak(8), streak(7))).isEqualTo(RecordComparison.BETTER);
        assertThat(comparator.compare(streak(6), streak(7))).isEqualTo(RecordComparison.WORSE);
        assertThat(comparator.compare(streak(7), streak(7))).isEqualTo(RecordComparison.EQUAL);
    }

    private static AttemptsDurationRecordValue value(int attempts, long seconds) {
        return new AttemptsDurationRecordValue(attempts, Duration.ofSeconds(seconds));
    }

    private static DurationRecordValue duration(long seconds) {
        return new DurationRecordValue(Duration.ofSeconds(seconds));
    }

    private static StreakRecordValue streak(int length) {
        LocalDate start = LocalDate.of(2026, 1, 1);
        return new StreakRecordValue(length, start, start.plusDays(length - 1L));
    }
}
