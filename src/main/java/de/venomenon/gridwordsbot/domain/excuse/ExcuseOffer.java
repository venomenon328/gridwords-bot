package de.venomenon.gridwordsbot.domain.excuse;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.Objects;

/** A positive first offer decision, including the player/game key used for the cooldown lock. */
public record ExcuseOffer(
        long gameResultId,
        long playerId,
        GameType gameType,
        ExcuseOfferMetadata metadata) {

    public ExcuseOffer {
        if (gameResultId <= 0 || playerId <= 0) {
            throw new IllegalArgumentException("IDs must be positive");
        }
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(metadata, "metadata");
    }
}
