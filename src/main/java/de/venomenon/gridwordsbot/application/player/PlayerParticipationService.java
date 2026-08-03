package de.venomenon.gridwordsbot.application.player;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameParticipationSelection;
import de.venomenon.gridwordsbot.domain.model.GameType;
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
        PlayerStatus result = changed(actor, activate(actor, selection, today), "Teilnahme ist ab heute aktiv.");
        refreshSafely(today);
        return result;
    }

    @Override
    public PlayerStatus leave(PlayerIdentity actor, GameParticipationSelection selection) {
        return changed(actor, deactivate(actor, selection, today().plusDays(1)), "Teilnahme endet ab morgen.");
    }

    @Override
    public PlayerStatus activate(PlayerIdentity actor, PlayerIdentity target, GameParticipationSelection selection) {
        if (!authorize(actor)) return denied();
        LocalDate today = today();
        PlayerStatus result = changed(target, activate(target, selection, today), "Teilnahme ist ab heute aktiv.");
        refreshSafely(today);
        return result;
    }

    @Override
    public PlayerStatus deactivate(PlayerIdentity actor, PlayerIdentity target, GameParticipationSelection selection) {
        if (!authorize(actor)) return denied();
        return changed(target, deactivate(target, selection, today().plusDays(1)), "Teilnahme endet ab morgen.");
    }

    @Override
    public PlayerStatus status(PlayerIdentity actor, PlayerIdentity target) {
        if (!authorize(actor)) return denied();
        return statusFor(target);
    }

    @Override
    public PlayerStatus enableReminders(PlayerIdentity actor) {
        return changed(actor, players.setReminderOptIn(profile(actor), true), "Reminder sind aktiviert.");
    }

    @Override
    public PlayerStatus disableReminders(PlayerIdentity actor) {
        return changed(actor, players.setReminderOptIn(profile(actor), false), "Reminder sind deaktiviert.");
    }

    @Override
    public PlayerStatus reminderStatus(PlayerIdentity actor) {
        return statusFor(actor);
    }

    private PlayerStatus statusFor(PlayerIdentity identity) {
        PlayerStore.StoredPlayer player = players.synchronizeProfile(profile(identity));
        return status(identity, player, "Status");
    }

    private PlayerStatus changed(PlayerIdentity identity, PlayerStore.StoredPlayer player, String message) {
        return status(identity, player, message);
    }

    private PlayerStatus status(PlayerIdentity identity, PlayerStore.StoredPlayer player, String action) {
        LocalDate date = today();
        ParticipationStatus gridWords = participation(identity.discordUserId(), GameType.GRIDWORDS, date);
        ParticipationStatus quadWords = participation(identity.discordUserId(), GameType.QUADWORDS, date);
        return new PlayerStatus(true, true, gridWords, quadWords, player.reminderOptIn(),
                action + " " + statusMessage(gridWords, quadWords, player.reminderOptIn()));
    }

    private ParticipationStatus participation(long playerId, GameType gameType, LocalDate date) {
        Optional<GameParticipationPeriod> period = players.findGameParticipationPeriod(playerId, gameType, date);
        return new ParticipationStatus(period.isPresent());
    }

    private boolean authorize(PlayerIdentity actor) {
        if (!isAdministrator(actor)) return false;
        players.synchronizeProfile(profile(actor));
        return true;
    }

    private PlayerStore.StoredPlayer activate(PlayerIdentity identity, GameParticipationSelection selection, LocalDate effectiveDate) {
        Objects.requireNonNull(selection, "selection");
        return players.activateGames(new PlayerStore.GameParticipationChange(profile(identity), selection, effectiveDate));
    }

    private PlayerStore.StoredPlayer deactivate(PlayerIdentity identity, GameParticipationSelection selection, LocalDate effectiveDate) {
        Objects.requireNonNull(selection, "selection");
        return players.deactivateGames(new PlayerStore.GameParticipationChange(profile(identity), selection, effectiveDate));
    }

    private PlayerStore.ProfileUpdate profile(PlayerIdentity identity) {
        return new PlayerStore.ProfileUpdate(identity.discordUserId(), identity.displayName(), isAdministrator(identity));
    }

    private boolean isAdministrator(PlayerIdentity identity) {
        return administratorIds.contains(identity.discordUserId());
    }

    private LocalDate today() {
        return clock.instant().atZone(zoneId).toLocalDate();
    }

    private void refreshSafely(LocalDate date) {
        try {
            statusRefresh.accept(date);
        } catch (RuntimeException exception) {
            LOGGER.warn("Daily status refresh after participation change failed for game date {}", date, exception);
        }
    }

    private static PlayerStatus denied() {
        return new PlayerStatus(false, false, false, false, "Keine Berechtigung.");
    }

    private static String statusMessage(
            ParticipationStatus gridWords, ParticipationStatus quadWords, boolean reminders) {
        return "GridWords: " + participationText(gridWords) + "; QuadWords: " + participationText(quadWords)
                + "; Reminder für aktive Spiele: " + (reminders ? "an" : "aus") + ".";
    }

    private static String participationText(ParticipationStatus participation) {
        return participation.active() ? "aktiv" : "inaktiv";
    }
}
