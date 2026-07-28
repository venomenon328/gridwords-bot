package de.venomenon.gridwordsbot.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Persistence boundary for configured players. */
public interface PlayerStore {
    StoredPlayer upsert(PlayerUpsert request);
    Optional<StoredPlayer> findByDiscordUserId(long discordUserId);

    record PlayerUpsert(long discordUserId, String displayName, boolean active, boolean administrator) {
        public PlayerUpsert {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
            if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        }
    }
    record StoredPlayer(long discordUserId, String displayName, boolean active, boolean administrator, Instant createdAt, Instant updatedAt) {
        public StoredPlayer {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }
}
