package de.venomenon.gridwordsbot.port.in;

/** Transport-neutral commands for player participation and reminder preferences. */
public interface PlayerParticipationUseCase {
    PlayerStatus join(PlayerIdentity actor);
    PlayerStatus leave(PlayerIdentity actor);
    PlayerStatus activate(PlayerIdentity actor, PlayerIdentity target);
    PlayerStatus deactivate(PlayerIdentity actor, PlayerIdentity target);
    PlayerStatus status(PlayerIdentity actor, PlayerIdentity target);
    PlayerStatus enableReminders(PlayerIdentity actor);
    PlayerStatus disableReminders(PlayerIdentity actor);
    PlayerStatus reminderStatus(PlayerIdentity actor);

    record PlayerIdentity(long discordUserId, String displayName) {
        public PlayerIdentity {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        }
    }

    record PlayerStatus(boolean authorized, boolean known, boolean active, boolean reminderOptIn, String message) {
        public PlayerStatus {
            if (message == null || message.isBlank()) throw new IllegalArgumentException("message must not be blank");
        }
    }
}
