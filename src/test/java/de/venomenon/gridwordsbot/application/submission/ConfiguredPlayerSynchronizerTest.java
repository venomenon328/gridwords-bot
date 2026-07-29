package de.venomenon.gridwordsbot.application.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfiguredPlayerSynchronizerTest {

    @Test
    void synchronizesBothConfiguredPlayersIdempotently() {
        RecordingPlayerStore store = new RecordingPlayerStore();
        ConfiguredPlayerSynchronizer synchronizer = new ConfiguredPlayerSynchronizer(properties(101L, 102L), store);

        synchronizer.synchronize();
        synchronizer.synchronize();

        assertThat(store.players).hasSize(2);
        assertThat(store.players.get(101L).administrator()).isTrue();
        assertThat(store.players.get(102L).administrator()).isFalse();
        assertThat(store.upserts).isEqualTo(4);
    }

    @Test
    void rejectsEqualConfiguredPlayerIdsBeforeSynchronization() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ConfiguredPlayerSynchronizer(properties(101L, 101L), new RecordingPlayerStore()))
                .withMessage("configured player IDs must be distinct");
    }

    @Test
    void rejectsNonPositiveConfiguredPlayerIdsBeforeSynchronization() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ConfiguredPlayerSynchronizer(properties(0L, 102L), new RecordingPlayerStore()))
                .withMessage("configured player IDs must be positive");
    }

    private GridwordsBotProperties properties(long firstId, long secondId) {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(false, "", 11L, 12L, List.of(firstId)),
                new GridwordsBotProperties.Players(
                        new GridwordsBotProperties.Player(firstId, "First"),
                        new GridwordsBotProperties.Player(secondId, "Second")),
                null,
                null);
    }

    private static final class RecordingPlayerStore implements PlayerStore {
        private final Map<Long, StoredPlayer> players = new HashMap<>();
        private int upserts;

        @Override
        public StoredPlayer upsert(PlayerUpsert request) {
            upserts++;
            StoredPlayer player = new StoredPlayer(request.discordUserId(), request.displayName(), request.active(),
                    request.administrator(), Instant.EPOCH, Instant.EPOCH);
            players.put(player.discordUserId(), player);
            return player;
        }

        @Override
        public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) {
            return Optional.ofNullable(players.get(discordUserId));
        }
    }
}
