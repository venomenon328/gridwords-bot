package de.venomenon.gridwordsbot.application.player;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameParticipationSelection;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Application service for idempotent participation and reminder commands. */
public final class PlayerParticipationService implements PlayerParticipationUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerParticipationService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");

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
        Map<GameType, Optional<GameParticipationPeriod>> before = selectedPeriods(actor.discordUserId(), selection, today);
        PlayerStatus result = changed(actor, activate(actor, selection, today), activationMessage(selection, before));
        refreshSafely(today);
        return result;
    }

    @Override
    public PlayerStatus leave(PlayerIdentity actor, GameParticipationSelection selection) {
        LocalDate today = today();
        LocalDate effectiveDate = today.plusDays(1);
        Map<GameType, Optional<GameParticipationPeriod>> before = selectedPeriods(actor.discordUserId(), selection, today);
        return changed(actor, deactivate(actor, selection, effectiveDate),
                deactivationMessage(selection, before, effectiveDate));
    }

    @Override
    public PlayerStatus activate(PlayerIdentity actor, PlayerIdentity target, GameParticipationSelection selection) {
        if (!authorizeMutation(actor)) return denied();
        LocalDate today = today();
        Map<GameType, Optional<GameParticipationPeriod>> before = selectedPeriods(target.discordUserId(), selection, today);
        PlayerStatus result = changed(target, activate(target, selection, today), activationMessage(selection, before));
        refreshSafely(today);
        return result;
    }

    @Override
    public PlayerStatus deactivate(PlayerIdentity actor, PlayerIdentity target, GameParticipationSelection selection) {
        if (!authorizeMutation(actor)) return denied();
        LocalDate today = today();
        LocalDate effectiveDate = today.plusDays(1);
        Map<GameType, Optional<GameParticipationPeriod>> before = selectedPeriods(target.discordUserId(), selection, today);
        return changed(target, deactivate(target, selection, effectiveDate),
                deactivationMessage(selection, before, effectiveDate));
    }

    @Override
    public PlayerStatus status(PlayerIdentity actor, PlayerIdentity target) {
        if (!isAdministrator(actor)) return denied();
        return statusForExisting(target);
    }

    @Override
    public PlayerStatus enableReminders(PlayerIdentity actor) {
        PlayerStore.StoredPlayer player = players.setReminderOptIn(profile(actor), true);
        return changed(actor, player, "Reminder: an");
    }

    @Override
    public PlayerStatus disableReminders(PlayerIdentity actor) {
        PlayerStore.StoredPlayer player = players.setReminderOptIn(profile(actor), false);
        return changed(actor, player, "Reminder: aus");
    }

    @Override
    public PlayerStatus reminderStatus(PlayerIdentity actor) {
        PlayerStore.StoredPlayer player = findExisting(actor.discordUserId()).orElse(null);
        if (player == null) {
            return unknown("Reminder: kein Spielerprofil vorhanden.");
        }
        return currentStatus(actor, player, "Reminder: " + (player.reminderOptIn() ? "an" : "aus"));
    }

    private PlayerStatus statusForExisting(PlayerIdentity identity) {
        PlayerStore.StoredPlayer player = findExisting(identity.discordUserId()).orElse(null);
        if (player == null) {
            return unknown("Kein Spielerprofil vorhanden.");
        }
        ParticipationStatus gridWords = participation(identity.discordUserId(), GameType.GRIDWORDS, today());
        ParticipationStatus quadWords = participation(identity.discordUserId(), GameType.QUADWORDS, today());
        return new PlayerStatus(true, true, gridWords, quadWords, player.reminderOptIn(),
                "GridWords: " + participationText(gridWords)
                        + "; QuadWords: " + participationText(quadWords)
                        + "; Reminder: " + (player.reminderOptIn() ? "an" : "aus") + ".");
    }

    private PlayerStatus changed(PlayerIdentity identity, PlayerStore.StoredPlayer player, String message) {
        return currentStatus(identity, player, message);
    }

    private PlayerStatus currentStatus(PlayerIdentity identity, PlayerStore.StoredPlayer player, String message) {
        LocalDate date = today();
        ParticipationStatus gridWords = participation(identity.discordUserId(), GameType.GRIDWORDS, date);
        ParticipationStatus quadWords = participation(identity.discordUserId(), GameType.QUADWORDS, date);
        return new PlayerStatus(true, true, gridWords, quadWords, player.reminderOptIn(), message);
    }

    private ParticipationStatus participation(long playerId, GameType gameType, LocalDate date) {
        return new ParticipationStatus(players.findGameParticipationPeriod(playerId, gameType, date).isPresent());
    }

    private Map<GameType, Optional<GameParticipationPeriod>> selectedPeriods(
            long playerId, GameParticipationSelection selection, LocalDate date) {
        EnumMap<GameType, Optional<GameParticipationPeriod>> periods = new EnumMap<>(GameType.class);
        selection.gameTypes().forEach(gameType -> periods.put(
                gameType, players.findGameParticipationPeriod(playerId, gameType, date)));
        return Map.copyOf(periods);
    }

    private static String activationMessage(
            GameParticipationSelection selection,
            Map<GameType, Optional<GameParticipationPeriod>> before) {
        return selection.gameTypes().stream()
                .map(gameType -> gameName(gameType) + ": " + activationEffect(before.get(gameType)))
                .collect(Collectors.joining("\n"));
    }

    private static String activationEffect(Optional<GameParticipationPeriod> current) {
        if (current == null || current.isEmpty()) {
            return "ab heute aktiv";
        }
        GameParticipationPeriod period = current.orElseThrow();
        if (period.inactiveFrom() != null) {
            return "heute aktiv · endet bereits ab " + DATE.format(period.inactiveFrom());
        }
        return "bereits aktiv";
    }

    private static String deactivationMessage(
            GameParticipationSelection selection,
            Map<GameType, Optional<GameParticipationPeriod>> before,
            LocalDate effectiveDate) {
        return selection.gameTypes().stream()
                .map(gameType -> gameName(gameType) + ": " + deactivationEffect(before.get(gameType), effectiveDate))
                .collect(Collectors.joining("\n"));
    }

    private static String deactivationEffect(Optional<GameParticipationPeriod> current, LocalDate effectiveDate) {
        if (current == null || current.isEmpty()) {
            return "bereits inaktiv";
        }
        GameParticipationPeriod period = current.orElseThrow();
        if (period.inactiveFrom() != null) {
            return "endet bereits ab " + DATE.format(period.inactiveFrom());
        }
        return "heute noch aktiv · ab " + DATE.format(effectiveDate) + " inaktiv";
    }

    private static String gameName(GameType gameType) {
        return gameType == GameType.GRIDWORDS ? "GridWords" : "QuadWords";
    }

    private Optional<PlayerStore.StoredPlayer> findExisting(long discordUserId) {
        return players.findAllPlayers().stream()
                .filter(player -> player.discordUserId() == discordUserId)
                .findFirst();
    }

    private boolean authorizeMutation(PlayerIdentity actor) {
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

    private static PlayerStatus unknown(String message) {
        return new PlayerStatus(true, false, false, false, message);
    }

    private static String participationText(ParticipationStatus participation) {
        return participation.active() ? "aktiv" : "inaktiv";
    }
}
