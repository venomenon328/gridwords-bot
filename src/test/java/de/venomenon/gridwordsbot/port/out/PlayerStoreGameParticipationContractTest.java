package de.venomenon.gridwordsbot.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.model.GameParticipationSelection;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlayerStoreGameParticipationContractTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);

    @Test
    void legacyPeriodsAreExposedForBothGamesUntilTheMigration() {
        PlayerStore store = legacyStore();

        assertThat(store.findGameParticipationPeriods()).extracting(period -> period.gameType())
                .containsExactly(GameType.GRIDWORDS, GameType.QUADWORDS);
        assertThat(store.findGameParticipationPeriod(5L, GameType.QUADWORDS, DATE)).isPresent();
    }

    @Test
    void legacyWriteCompatibilityOnlyAcceptsBoth() {
        PlayerStore store = legacyStore();
        PlayerStore.ProfileUpdate profile = new PlayerStore.ProfileUpdate(5L, "Player", false);

        assertThatThrownBy(() -> store.activateGames(new PlayerStore.GameParticipationChange(
                profile, GameParticipationSelection.GRIDWORDS, DATE)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static PlayerStore legacyStore() {
        return new PlayerStore() {
            @Override public StoredPlayer upsert(PlayerUpsert request) { throw new UnsupportedOperationException(); }
            @Override public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) { return Optional.empty(); }
            @Override public List<ParticipationPeriod> findParticipationPeriods() {
                return List.of(new ParticipationPeriod(5L, DATE.minusDays(1), null));
            }
        };
    }
}
