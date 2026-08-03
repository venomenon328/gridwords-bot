package de.venomenon.gridwordsbot.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GameParticipationPeriodTest {
    private static final LocalDate START = LocalDate.of(2026, 8, 3);

    @Test
    void containsItsInclusiveStartAndExcludesItsExclusiveEnd() {
        GameParticipationPeriod period = new GameParticipationPeriod(7L, GameType.GRIDWORDS, START, START.plusDays(2));

        assertThat(period.contains(START)).isTrue();
        assertThat(period.contains(START.plusDays(1))).isTrue();
        assertThat(period.contains(START.plusDays(2))).isFalse();
    }

    @Test
    void rejectsMissingGameTypeAndInvalidBounds() {
        assertThatNullPointerException().isThrownBy(
                () -> new GameParticipationPeriod(7L, null, START, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new GameParticipationPeriod(7L, GameType.QUADWORDS, START, START));
    }
}
