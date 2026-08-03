package de.venomenon.gridwordsbot.application.player;

import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameParticipationSelection;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Application service for idempotent participation and reminder commands. */
public final class PlayerParticipationService implements PlayerParticipationUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerParticipationService.class);
    private final PlayerStore players;
    private final Clock clock;
    private final ZoneId zoneId;
    private final Set<Long> administratorIds;
    private final Consumer<LocalDate> statusRefresh;

    public PlayerParticipationService(PlayerStore players, Clock clock, ZoneId zoneId, Set<Long> administratorIds) {
        this(players, clock, zoneId, administratorIds, ignored -> { });
    }

    public PlayerParticipationService(PlayerStore players, Clock clock, ZoneId zoneId, Set<Long> administratorIds,
            Consumer<LocalDate> statusRefresh) {
        this.players = Objects.requireNonNull(players);
        this.clock = Objects.requireNonNull(clock);
        this.zoneId = Objects.requireNonNull(zoneId);
        this.administratorIds = Set.copyOf(Objects.requireNonNull(administratorIds));
        this.statusRefresh = Objects.requireNonNull(statusRefresh);
    }

    @Override
    public PlayerStatus join(PlayerIdentity actor, GameParticipationSelection selection) {
        LocalDate today = today();
        PlayerStatus result = changed(activate(actor, selection, today), "Teilnahme ist ab heute aktiv.");
        refreshSafely(today);
        return result;
    }

    @Override
    public PlayerStatus leave(PlayerIdentity actor, GameParticipationSelection selection) {
        return changed(deactivate(actor, selection, today().plusDays(1)), "Teilnahme endet ab morgen.");
    }


    @Override
    public PlayerStatus activate(PlayerIdentity actor, PlayerIdentity target, GameParticipationSelection selection) {
        if (!authorize(actor)) return denied();
        LocalDate today = today();
        PlayerStatus result = changed(activate(target, selection, today), "Teilnahme ist ab heute aktiv.");
        refreshSafely(today);
        return result;
    }

    @Override
    public PlayerStatus deactivate(PlayerIdentity actor, PlayerIdentity target, GameParticipationSelection selection) {
        if (!authorize(actor)) return denied();
        return changed(deactivate(target, selection, today().plusDays(1)), "Teilnahme endet ab morgen.");
    }

    @Override
    public PlayerStatus status(PlayerIdentity actor, PlayerIdentity target) {
        if (!authorize(actor)) return denied();
        return statusFor(target);
    }

    @Override
    public PlayerStatus enableReminders(PlayerIdentity actor) {
        return changed(players.setReminderOptIn(profile(actor), true), "Reminder sind aktiviert.");
    }

    @Override
    public PlayerStatus disableReminders(PlayerIdentity actor) {
        return changed(players.setReminderOptIn(profile(actor), false), "Reminder sind deaktiviert.");
    }

    @Override public PlayerStatus reminderStatus(PlayerIdentity actor) { return statusFor(actor); }

    private PlayerStatus statusFor(PlayerIdentity identity) {
        LocalDate date = today();
        PlayerStore.StoredPlayer player = players.synchronizeProfile(profile(identity));
        Optional<ParticipationPeriod> period = players.findParticipationPeriod(identity.discordUserId(), date);
        return new PlayerStatus(true, true, player.active(), player.reminderOptIn(),
                statusMessage(player.active(), player.reminderOptIn(), period));
    }

    private boolean authorize(PlayerIdentity actor) {
        if (!isAdministrator(actor)) return false;
        players.synchronizeProfile(profile(actor));
        return true;
    }

    private PlayerStore.ParticipationChange change(PlayerIdentity identity, LocalDate effectiveDate) {
        return new PlayerStore.ParticipationChange(profile(identity), effectiveDate);
    }
    private PlayerStore.StoredPlayer activate(PlayerIdentity identity, GameParticipationSelection selection, LocalDate effectiveDate) {
        Objects.requireNonNull(selection, "selection");
        if (selection == GameParticipationSelection.BOTH) return players.activate(change(identity, effectiveDate));
        return players.activateGames(new PlayerStore.GameParticipationChange(profile(identity), selection, effectiveDate));
    }

    private PlayerStore.StoredPlayer deactivate(PlayerIdentity identity, GameParticipationSelection selection, LocalDate effectiveDate) {
        Objects.requireNonNull(selection, "selection");
        if (selection == GameParticipationSelection.BOTH) return players.deactivate(change(identity, effectiveDate));
        return players.deactivateGames(new PlayerStore.GameParticipationChange(profile(identity), selection, effectiveDate));
    }


    private PlayerStore.ProfileUpdate profile(PlayerIdentity identity) {
        return new PlayerStore.ProfileUpdate(identity.discordUserId(), identity.displayName(), isAdministrator(identity));
    }

    private boolean isAdministrator(PlayerIdentity identity) { return administratorIds.contains(identity.discordUserId()); }
    private LocalDate today() { return clock.instant().atZone(zoneId).toLocalDate(); }

    private void refreshSafely(LocalDate date) {
        try {
            statusRefresh.accept(date);
        } catch (RuntimeException exception) {
            LOGGER.warn("Daily status refresh after participation change failed for game date {}", date, exception);
        }
    }

    private static PlayerStatus changed(PlayerStore.StoredPlayer player, String message) {
        return new PlayerStatus(true, true, player.active(), player.reminderOptIn(), message);
    }

    private static PlayerStatus denied() {
        return new PlayerStatus(false, false, false, false, "Keine Berechtigung.");
    }

    private static String statusMessage(boolean active, boolean reminders,
            Optional<ParticipationPeriod> currentPeriod) {
        String participation = active ? "aktiv" + currentPeriod.map(PlayerParticipationService::periodText).orElse("")
                : "inaktiv";
        return "Teilnahme: " + participation + "; Reminder: " + (reminders ? "an" : "aus") + ".";
    }

    private static String periodText(ParticipationPeriod period) {
        if (period.inactiveFrom() == null) return " seit " + period.activeFrom();
        return " von " + period.activeFrom() + " bis " + period.inactiveFrom().minusDays(1);
    }
}