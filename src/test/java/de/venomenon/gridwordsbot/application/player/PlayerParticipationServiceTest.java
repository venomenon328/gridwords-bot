package de.venomenon.gridwordsbot.application.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameParticipationSelection;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase.PlayerIdentity;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerParticipationServiceTest {
    private static final long ADMIN = 1L;
    private static final long PLAYER = 2L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 29);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 7, 30);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void joinOfInactiveGamesExplainsImmediateActivationPerGame() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.findGameParticipationPeriod(PLAYER, GameType.GRIDWORDS, TODAY))
                .thenReturn(Optional.empty(), Optional.of(period(GameType.GRIDWORDS, TODAY, null)));
        when(store.findGameParticipationPeriod(PLAYER, GameType.QUADWORDS, TODAY))
                .thenReturn(Optional.empty(), Optional.of(period(GameType.QUADWORDS, TODAY, null)));
        when(store.activateGames(any())).thenReturn(player(true, true));

        var status = service(store).join(identity(PLAYER), GameParticipationSelection.BOTH);

        assertThat(status.message()).isEqualTo("GridWords: ab heute aktiv\nQuadWords: ab heute aktiv");
        assertThat(status.gridWordsParticipation().active()).isTrue();
        assertThat(status.quadWordsParticipation().active()).isTrue();
        verify(store).activateGames(new PlayerStore.GameParticipationChange(
                new PlayerStore.ProfileUpdate(PLAYER, "Player", false), GameParticipationSelection.BOTH, TODAY));
    }

    @Test
    void joinOfAlreadyActiveGameIsExplicitlyIdempotent() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.findGameParticipationPeriod(PLAYER, GameType.GRIDWORDS, TODAY))
                .thenReturn(Optional.of(period(GameType.GRIDWORDS, TODAY.minusDays(2), null)));
        when(store.activateGames(any())).thenReturn(player(true, true));

        var status = service(store).join(identity(PLAYER), GameParticipationSelection.GRIDWORDS);

        assertThat(status.message()).isEqualTo("GridWords: bereits aktiv");
    }

    @Test
    void joinDoesNotPretendToCancelAnAlreadyScheduledEnd() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.findGameParticipationPeriod(PLAYER, GameType.GRIDWORDS, TODAY))
                .thenReturn(Optional.of(period(GameType.GRIDWORDS, TODAY.minusDays(2), TOMORROW)));
        when(store.activateGames(any())).thenReturn(player(true, true));

        var status = service(store).join(identity(PLAYER), GameParticipationSelection.GRIDWORDS);

        assertThat(status.message()).isEqualTo("GridWords: heute aktiv · endet bereits ab 30.07.2026");
    }

    @Test
    void leaveExplainsThatCurrentDayRemainsActiveAndRepeatLeaveIsIdempotent() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.deactivateGames(any())).thenReturn(player(true, true));
        when(store.findGameParticipationPeriod(PLAYER, GameType.GRIDWORDS, TODAY))
                .thenReturn(Optional.of(period(GameType.GRIDWORDS, TODAY.minusDays(3), null)),
                        Optional.of(period(GameType.GRIDWORDS, TODAY.minusDays(3), TOMORROW)),
                        Optional.of(period(GameType.GRIDWORDS, TODAY.minusDays(3), TOMORROW)),
                        Optional.of(period(GameType.GRIDWORDS, TODAY.minusDays(3), TOMORROW)));

        var first = service(store).leave(identity(PLAYER), GameParticipationSelection.GRIDWORDS);
        var repeated = service(store).leave(identity(PLAYER), GameParticipationSelection.GRIDWORDS);

        assertThat(first.message()).isEqualTo("GridWords: heute noch aktiv · ab 30.07.2026 inaktiv");
        assertThat(repeated.message()).isEqualTo("GridWords: endet bereits ab 30.07.2026");
        verify(store, org.mockito.Mockito.times(2)).deactivateGames(new PlayerStore.GameParticipationChange(
                new PlayerStore.ProfileUpdate(PLAYER, "Player", false), GameParticipationSelection.GRIDWORDS, TOMORROW));
    }

    @Test
    void leaveOfInactiveGameSaysAlreadyInactive() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.findGameParticipationPeriod(PLAYER, GameType.QUADWORDS, TODAY)).thenReturn(Optional.empty());
        when(store.deactivateGames(any())).thenReturn(player(false, false));

        var status = service(store).leave(identity(PLAYER), GameParticipationSelection.QUADWORDS);

        assertThat(status.message()).isEqualTo("QuadWords: bereits inaktiv");
    }

    @Test
    void deniesAdminMutationWithoutPersistingAnything() {
        PlayerStore store = mock(PlayerStore.class);

        assertThat(service(store).activate(identity(PLAYER), identity(3L)).authorized()).isFalse();

        verify(store, never()).activateGames(any());
        verify(store, never()).synchronizeProfile(any());
    }

    @Test
    void adminMutationStillSynchronizesAdministratorProfileAndUsesSameEffectText() {
        PlayerStore store = mock(PlayerStore.class);
        long target = 3L;
        when(store.synchronizeProfile(any())).thenReturn(admin());
        when(store.findGameParticipationPeriod(target, GameType.QUADWORDS, TODAY))
                .thenReturn(Optional.empty(), Optional.of(new GameParticipationPeriod(target, GameType.QUADWORDS, TODAY, null)));
        when(store.activateGames(any())).thenReturn(new PlayerStore.StoredPlayer(
                target, "Target", true, false, true, Instant.EPOCH, Instant.EPOCH));

        var status = service(store).activate(identity(ADMIN), new PlayerIdentity(target, "Target"),
                GameParticipationSelection.QUADWORDS);

        assertThat(status.message()).isEqualTo("QuadWords: ab heute aktiv");
        verify(store).synchronizeProfile(new PlayerStore.ProfileUpdate(ADMIN, "Player", true));
    }

    @Test
    void adminDeactivationUsesTheSameProspectiveEffectSemantics() {
        PlayerStore store = mock(PlayerStore.class);
        long target = 3L;
        when(store.synchronizeProfile(any())).thenReturn(admin());
        when(store.findGameParticipationPeriod(target, GameType.GRIDWORDS, TODAY))
                .thenReturn(Optional.of(new GameParticipationPeriod(target, GameType.GRIDWORDS, TODAY.minusDays(2), null)),
                        Optional.of(new GameParticipationPeriod(target, GameType.GRIDWORDS, TODAY.minusDays(2), TOMORROW)));
        when(store.deactivateGames(any())).thenReturn(new PlayerStore.StoredPlayer(
                target, "Target", true, false, false, Instant.EPOCH, Instant.EPOCH));

        var status = service(store).deactivate(identity(ADMIN), new PlayerIdentity(target, "Target"),
                GameParticipationSelection.GRIDWORDS);

        assertThat(status.message()).isEqualTo("GridWords: heute noch aktiv · ab 30.07.2026 inaktiv");
        verify(store).deactivateGames(new PlayerStore.GameParticipationChange(
                new PlayerStore.ProfileUpdate(target, "Target", false), GameParticipationSelection.GRIDWORDS, TOMORROW));
    }

    @Test
    void administrativeStatusIsReadOnlyAndUnknownTargetRemainsUnknown() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.findAllPlayers()).thenReturn(List.of());

        var status = service(store).status(identity(ADMIN), new PlayerIdentity(3L, "Unknown"));

        assertThat(status.authorized()).isTrue();
        assertThat(status.known()).isFalse();
        assertThat(status.message()).isEqualTo("Kein Spielerprofil vorhanden.");
        verify(store, never()).synchronizeProfile(any());
    }

    @Test
    void administrativeStatusReadsExistingPlayerWithoutProfileMutation() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.findAllPlayers()).thenReturn(List.of(player(true, true)));
        when(store.findGameParticipationPeriod(PLAYER, GameType.GRIDWORDS, TODAY))
                .thenReturn(Optional.of(period(GameType.GRIDWORDS, TODAY.minusDays(2), null)));

        var status = service(store).status(identity(ADMIN), identity(PLAYER));

        assertThat(status.message()).isEqualTo("GridWords: aktiv; QuadWords: inaktiv; Reminder: an.");
        verify(store, never()).synchronizeProfile(any());
    }

    @Test
    void reminderStatusIsReadOnlyWhileOptInStillCreatesOrUpdatesTheProfile() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.findAllPlayers()).thenReturn(List.of(player(false, false)));

        var read = service(store).reminderStatus(identity(PLAYER));

        assertThat(read.message()).isEqualTo("Reminder: aus");
        verify(store, never()).synchronizeProfile(any());
        verify(store, never()).setReminderOptIn(any(), org.mockito.ArgumentMatchers.anyBoolean());

        when(store.setReminderOptIn(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(player(false, true));
        var changed = service(store).enableReminders(identity(PLAYER));
        assertThat(changed.message()).isEqualTo("Reminder: an");
        verify(store).setReminderOptIn(new PlayerStore.ProfileUpdate(PLAYER, "Player", false), true);
    }

    @Test
    void joinAndAdminActivationRefreshTodaysStatusButProspectiveLeaveDoesNot() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.activateGames(any())).thenReturn(player(true, false));
        when(store.deactivateGames(any())).thenReturn(player(true, false));
        when(store.synchronizeProfile(any())).thenReturn(admin());
        java.util.List<LocalDate> refreshes = new java.util.ArrayList<>();
        PlayerParticipationService service = new PlayerParticipationService(
                store, CLOCK, BERLIN, Set.of(ADMIN), refreshes::add);

        service.join(identity(PLAYER));
        service.leave(identity(PLAYER));
        service.activate(identity(ADMIN), identity(PLAYER));

        assertThat(refreshes).containsExactly(TODAY, TODAY);
    }

    @Test
    void statusRefreshFailureDoesNotRollBackSuccessfulJoin() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.activateGames(any())).thenReturn(player(true, false));
        when(store.findGameParticipationPeriod(PLAYER, GameType.GRIDWORDS, TODAY))
                .thenReturn(Optional.empty(), Optional.of(period(GameType.GRIDWORDS, TODAY, null)));
        PlayerParticipationService service = new PlayerParticipationService(store, CLOCK, BERLIN, Set.of(ADMIN),
                ignored -> { throw new IllegalStateException("status unavailable"); });

        assertThat(service.join(identity(PLAYER), GameParticipationSelection.GRIDWORDS).active()).isTrue();
        verify(store).activateGames(any());
    }

    private static PlayerParticipationService service(PlayerStore store) {
        return new PlayerParticipationService(store, CLOCK, BERLIN, Set.of(ADMIN));
    }

    private static PlayerIdentity identity(long id) {
        return new PlayerIdentity(id, "Player");
    }

    private static GameParticipationPeriod period(GameType gameType, LocalDate activeFrom, LocalDate inactiveFrom) {
        return new GameParticipationPeriod(PLAYER, gameType, activeFrom, inactiveFrom);
    }

    private static PlayerStore.StoredPlayer player(boolean active, boolean reminders) {
        return new PlayerStore.StoredPlayer(PLAYER, "Player", active, false, reminders, Instant.EPOCH, Instant.EPOCH);
    }

    private static PlayerStore.StoredPlayer admin() {
        return new PlayerStore.StoredPlayer(ADMIN, "Player", true, true, false, Instant.EPOCH, Instant.EPOCH);
    }
}
