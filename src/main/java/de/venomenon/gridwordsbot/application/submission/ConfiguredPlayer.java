package de.venomenon.gridwordsbot.application.submission;

import java.util.Objects;

/** Pure application input representing one of the two configured players. */
public record ConfiguredPlayer(long discordUserId, String displayName, boolean administrator) {

    public ConfiguredPlayer {
        if (discordUserId <= 0) {
            throw new IllegalArgumentException("configured player ID must be positive");
        }
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("configured player display name must not be blank");
        }
    }
}
