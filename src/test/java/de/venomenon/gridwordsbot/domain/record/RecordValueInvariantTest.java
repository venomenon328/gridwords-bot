package de.venomenon.gridwordsbot.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecordValueInvariantTest {

    @Test
    void acceptsValidTypedValuesAndReportsTheirKinds() {
        LocalDate start = LocalDate.of(2026, 8, 1);

        assertThat(new AttemptsDurationRecordValue(2, Duration.ofSeconds(30)).kind())
                .isEqualTo(RecordValueKind.ATTEMPTS_AND_DURATION);
        assertThat(new DurationRecordValue(Duration.ZERO).kind()).isEqualTo(RecordValueKind.DURATION);
        assertThat(new StreakRecordValue(3, start, start.plusDays(2)).kind())
                .isEqualTo(RecordValueKind.STREAK);
    }

    @Test
    void rejectsInvalidAttemptsAndDurations() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AttemptsDurationRecordValue(0, Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AttemptsDurationRecordValue(1, Duration.ofSeconds(-1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DurationRecordValue(Duration.ofSeconds(-1)));
    }

    @Test
    void requiresStreakLengthToMatchTheInclusiveCalendarPeriod() {
        LocalDate start = LocalDate.of(2026, 8, 1);

        assertThatCode(() -> new StreakRecordValue(3, start, start.plusDays(2))).doesNotThrowAnyException();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StreakRecordValue(2, start, start.plusDays(2)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StreakRecordValue(1, start, start.minusDays(1)));
    }

    @Test
    void keepsDefinitionAndLockVersionsAsDifferentTypes() {
        assertThat(RecordDefinitionVersion.RECORDS_V1.value()).isEqualTo("records-v1");
        assertThat(RecordDefinitionVersion.RECORDS_V2.value()).isEqualTo("records-v2");
        assertThat(RecordLockVersion.initial().next()).isEqualTo(new RecordLockVersion(1));
        assertThatIllegalArgumentException().isThrownBy(() -> new RecordLockVersion(-1));
    }
}
