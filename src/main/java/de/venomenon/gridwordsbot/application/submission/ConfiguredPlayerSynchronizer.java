package de.venomenon.gridwordsbot.application.submission;

import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.util.List;
import java.util.Objects;

/** Synchronizes the two intentionally fixed configured players before inbound processing starts. */
public final class ConfiguredPlayerSynchronizer {

    private final List<ConfiguredPlayer> players;
    private final PlayerStore playerStore;

    public ConfiguredPlayerSynchronizer(List<ConfiguredPlayer> players, PlayerStore playerStore) {
        this.players = List.copyOf(Objects.requireNonNull(players, "players"));
        this.playerStore = Objects.requireNonNull(playerStore, "playerStore");
        validateConfiguredPlayers(this.players);
    }

    public void synchronize() {
        players.forEach(this::upsert);
    }

    private void upsert(ConfiguredPlayer player) {
        playerStore.upsert(new PlayerStore.PlayerUpsert(
                player.discordUserId(), player.displayName(), true, player.administrator()));
    }

    private static void validateConfiguredPlayers(List<ConfiguredPlayer> players) {
        if (players.size() != 2) {
            throw new IllegalArgumentException("exactly two configured players are required");
        }
        if (players.getFirst().discordUserId() == players.getLast().discordUserId()) {
            throw new IllegalArgumentException("configured player IDs must be distinct");
        }
    }
}
