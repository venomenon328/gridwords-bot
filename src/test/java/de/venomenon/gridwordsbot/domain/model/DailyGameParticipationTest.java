package de.venomenon.gridwordsbot.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DailyGameParticipationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);

    @Test
    void derivesAllFourDailySetsWithoutDuplicates() {
        DailyGameParticipation participation = DailyGameParticipation.fromPeriods(DATE, List.of(
                period(1L, GameType.GRIDWORDS), period(1L, GameType.QUADWORDS),
                period(2L, GameType.GRIDWORDS), period(2L, GameType.GRIDWORDS),
                period(3L, GameType.QUADWORDS),
                new GameParticipationPeriod(4L, GameType.GRIDWORDS, DATE.plusDays(1), null)));

        assertThat(participation.gridWordsPlayers()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(participation.quadWordsPlayers()).containsExactlyInAnyOrder(1L, 3L);
        assertThat(participation.participatingPlayers()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(participation.bothGamesPlayers()).containsExactly(1L);
        assertThat(participation.playersFor(GameType.GRIDWORDS)).isEqualTo(participation.gridWordsPlayers());
    }

    @Test
    void supportsNoParticipation() {
        DailyGameParticipation participation = DailyGameParticipation.fromPeriods(DATE, List.of());

        assertThat(participation.gridWordsPlayers()).isEmpty();
        assertThat(participation.quadWordsPlayers()).isEmpty();
        assertThat(participation.participatingPlayers()).isEmpty();
        assertThat(participation.bothGamesPlayers()).isEmpty();
    }

    @Test
    void rejectsInconsistentOrNullSets() {
        Set<Long> nullContainingSet = new HashSet<>();
        nullContainingSet.add(null);
        assertThatIllegalArgumentException().isThrownBy(() -> new DailyGameParticipation(
                DATE, Set.of(1L), Set.of(), Set.of(), Set.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new DailyGameParticipation(
                DATE, Set.of(), Set.of(), Set.of(), nullContainingSet));
    }

    @Test
    void expandsBothInTheStableSupportedGameOrder() {
        assertThat(GameParticipationSelection.BOTH.gameTypes())
                .containsExactly(GameType.GRIDWORDS, GameType.QUADWORDS);
        assertThat(GameParticipationSelection.GRIDWORDS.gameTypes()).containsExactly(GameType.GRIDWORDS);
        assertThat(GameParticipationSelection.QUADWORDS.gameTypes()).containsExactly(GameType.QUADWORDS);
    }

    private static GameParticipationPeriod period(long playerId, GameType gameType) {
        return new GameParticipationPeriod(playerId, gameType, DATE, null);
    }
}
