package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticipationPeriodCompatibilityTest {
    @Test
    void derivesGlobalUnionWithoutDuplicatingOrSplittingAdjacentGamePeriods() {
        List<ParticipationPeriod> periods = ParticipationPeriodCompatibility.union(List.of(
                period(1L, GameType.GRIDWORDS, 1, 5),
                period(1L, GameType.QUADWORDS, 3, 7),
                period(2L, GameType.GRIDWORDS, 1, 2),
                period(2L, GameType.QUADWORDS, 2, 3),
                period(3L, GameType.GRIDWORDS, 1, 2),
                period(3L, GameType.QUADWORDS, 3, null)));

        assertThat(periods).containsExactly(
                new ParticipationPeriod(1L, date(1), date(7)),
                new ParticipationPeriod(2L, date(1), date(3)),
                new ParticipationPeriod(3L, date(1), date(2)),
                new ParticipationPeriod(3L, date(3), null));
    }

    private static GameParticipationPeriod period(long playerId, GameType gameType, int from, Integer until) {
        return new GameParticipationPeriod(
                playerId, gameType, date(from), until == null ? null : date(until));
    }

    private static LocalDate date(int day) {
        return LocalDate.of(2026, 7, day);
    }
}
