package de.venomenon.gridwordsbot.application.player;

import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/** Application service for idempotent participation and reminder commands. */
public final class PlayerParticipationService implements PlayerParticipationUseCase {
    private final PlayerStore players;
    private final Clock clock;
    private final ZoneId zoneId;
    private final Set<Long> administratorIds;

    public PlayerParticipationService(PlayerStore players, Clock clock, ZoneId zoneId, Set<Long> administratorIds) {
        this.players = Objects.requireNonNull(players);
        this.clock = Objects.requireNonNull(clock);
        this.zoneId = Objects.requireNonNull(zoneId);
        this.administratorIds = Set.copyOf(Objects.requireNonNull(administratorIds));
    }

    @Override public PlayerStatus join(PlayerIdentity actor) { return active(players.activate(change(actor, today())), "Teilnahme ist ab heute aktiv."); }
    @Override public PlayerStatus leave(PlayerIdentity actor) { return active(players.deactivate(change(actor, today().plusDays(1))), "Teilnahme endet ab morgen."); }
    @Override public PlayerStatus status(PlayerIdentity actor) { return statusFor(actor); }

    @Override public PlayerStatus activate(PlayerIdentity actor, PlayerIdentity target) {
        if (!isAdministrator(actor)) return denied();
        return active(players.activate(change(target, today())), "Teilnahme ist ab heute aktiv.");
    }
    @Override public PlayerStatus deactivate(PlayerIdentity actor, PlayerIdentity target) {
        if (!isAdministrator(actor)) return denied();
        return active(players.deactivate(change(target, today().plusDays(1))), "Teilnahme endet ab morgen.");
    }
    @Override public PlayerStatus status(PlayerIdentity actor, PlayerIdentity target) {
        return isAdministrator(actor) ? statusFor(target) : denied();
    }

    @Override public PlayerStatus enableReminders(PlayerIdentity actor) {
        return active(players.setReminderOptIn(profile(actor), true), "Reminder sind aktiviert.");
    }
    @Override public PlayerStatus disableReminders(PlayerIdentity actor) {
        return active(players.setReminderOptIn(profile(actor), false), "Reminder sind deaktiviert.");
    }
    @Override public PlayerStatus reminderStatus(PlayerIdentity actor) { return statusFor(actor); }

    private PlayerStatus statusFor(PlayerIdentity identity) {
        return players.findByDiscordUserId(identity.discordUserId())
                .map(player -> new PlayerStatus(true, true, player.active(), player.reminderOptIn(), statusMessage(player.active(), player.reminderOptIn())))
                .orElseGet(() -> new PlayerStatus(true, false, false, false, "Kein Spielerprofil vorhanden."));
    }
    private PlayerStore.ParticipationChange change(PlayerIdentity identity, LocalDate effectiveDate) {
        return new PlayerStore.ParticipationChange(profile(identity), effectiveDate);
    }
    private PlayerStore.ProfileUpdate profile(PlayerIdentity identity) {
        return new PlayerStore.ProfileUpdate(identity.discordUserId(), identity.displayName(), isAdministrator(identity));
    }
    private boolean isAdministrator(PlayerIdentity identity) { return administratorIds.contains(identity.discordUserId()); }
    private LocalDate today() { return clock.instant().atZone(zoneId).toLocalDate(); }
    private static PlayerStatus active(PlayerStore.StoredPlayer player, String message) {
        return new PlayerStatus(true, true, player.active(), player.reminderOptIn(), message);
    }
    private static PlayerStatus denied() { return new PlayerStatus(false, false, false, false, "Keine Berechtigung."); }
    private static String statusMessage(boolean active, boolean reminders) {
        return "Teilnahme: " + (active ? "aktiv" : "inaktiv") + "; Reminder: " + (reminders ? "an" : "aus") + ".";
    }
}
