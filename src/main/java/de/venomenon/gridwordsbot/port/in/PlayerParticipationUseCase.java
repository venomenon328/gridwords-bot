package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.model.GameParticipationSelection;
import java.util.Objects;

/** Transport-neutral commands for player participation and reminder preferences. */
public interface PlayerParticipationUseCase {

    default PlayerStatus join(PlayerIdentity actor) { return join(actor, GameParticipationSelection.BOTH); }
    default PlayerStatus leave(PlayerIdentity actor) { return leave(actor, GameParticipationSelection.BOTH); }
    default PlayerStatus activate(PlayerIdentity actor, PlayerIdentity target) { return activate(actor, target, GameParticipationSelection.BOTH); }
    default PlayerStatus deactivate(PlayerIdentity actor, PlayerIdentity target) { return deactivate(actor, target, GameParticipationSelection.BOTH); }
    PlayerStatus join(PlayerIdentity actor, GameParticipationSelection selection);
    PlayerStatus leave(PlayerIdentity actor, GameParticipationSelection selection);
    PlayerStatus activate(PlayerIdentity actor, PlayerIdentity target, GameParticipationSelection selection);
    PlayerStatus deactivate(PlayerIdentity actor, PlayerIdentity target, GameParticipationSelection selection);
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

    record PlayerStatus(boolean authorized, boolean known, ParticipationStatus gridWordsParticipation,
            ParticipationStatus quadWordsParticipation, boolean reminderOptIn, String message) {
        public PlayerStatus {
            Objects.requireNonNull(gridWordsParticipation, "gridWordsParticipation");
            Objects.requireNonNull(quadWordsParticipation, "quadWordsParticipation");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("message must not be blank");
        }
        public PlayerStatus(boolean authorized, boolean known, boolean active, boolean reminderOptIn, String message) {
            this(authorized, known, new ParticipationStatus(active), new ParticipationStatus(active), reminderOptIn, message);
        }
        public boolean active() { return gridWordsParticipation.active() || quadWordsParticipation.active(); }
    }

    record ParticipationStatus(boolean active) { }
}
