package de.venomenon.gridwordsbot.application.submission;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.util.List;
import java.util.Objects;

/** Synchronizes the two intentionally fixed configured players before inbound processing starts. */
public final class ConfiguredPlayerSynchronizer {

    private final GridwordsBotProperties properties;
    private final PlayerStore playerStore;

    public ConfiguredPlayerSynchronizer(GridwordsBotProperties properties, PlayerStore playerStore) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.playerStore = Objects.requireNonNull(playerStore, "playerStore");
        validateConfiguredPlayers(properties.players());
    }

    public void synchronize() {
        GridwordsBotProperties.Players players = properties.players();
        List<Long> administrators = properties.discord().adminUserIds();
        upsert(players.first(), administrators);
        upsert(players.second(), administrators);
    }

    private void upsert(GridwordsBotProperties.Player player, List<Long> administrators) {
        playerStore.upsert(new PlayerStore.PlayerUpsert(
                player.userId(), player.displayName(), true, administrators.contains(player.userId())));
    }

    private static void validateConfiguredPlayers(GridwordsBotProperties.Players players) {
        Objects.requireNonNull(players, "players");
        GridwordsBotProperties.Player first = Objects.requireNonNull(players.first(), "first player");
        GridwordsBotProperties.Player second = Objects.requireNonNull(players.second(), "second player");
        if (first.userId() <= 0 || second.userId() <= 0) {
            throw new IllegalArgumentException("configured player IDs must be positive");
        }
        if (first.userId() == second.userId()) {
            throw new IllegalArgumentException("configured player IDs must be distinct");
        }
    }
}
