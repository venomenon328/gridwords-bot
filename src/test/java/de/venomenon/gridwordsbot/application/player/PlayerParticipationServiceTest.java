package de.venomenon.gridwordsbot.application.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase.PlayerIdentity;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerParticipationServiceTest {
    private static final long ADMIN = 1L;
    private static final long PLAYER = 2L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void joinStartsTodayAndLeaveEndsTomorrow() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.activate(any())).thenReturn(player(true, false));
        when(store.deactivate(any())).thenReturn(player(true, false));
        PlayerParticipationService service = service(store);

        service.join(identity(PLAYER));
        service.leave(identity(PLAYER));

        verify(store).activate(new PlayerStore.ParticipationChange(new PlayerStore.ProfileUpdate(PLAYER, "Player", false),
                LocalDate.of(2026, 7, 29)));
        verify(store).deactivate(new PlayerStore.ParticipationChange(new PlayerStore.ProfileUpdate(PLAYER, "Player", false),
                LocalDate.of(2026, 7, 30)));
    }

    @Test
    void deniesAdminMutationWithoutPersistingAnything() {
        PlayerStore store = mock(PlayerStore.class);
        PlayerParticipationService service = service(store);

        assertThat(service.activate(identity(PLAYER), identity(3L)).authorized()).isFalse();

        verify(store, never()).activate(any());
        verify(store, never()).synchronizeProfile(any());
    }

    @Test
    void createsAnInactiveProfileForReminderOptIn() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.setReminderOptIn(any(), any(Boolean.class))).thenReturn(player(false, true));

        assertThat(service(store).enableReminders(identity(PLAYER)).active()).isFalse();
        verify(store).setReminderOptIn(new PlayerStore.ProfileUpdate(PLAYER, "Player", false), true);
    }

    @Test
    void statusSynchronizesAnUnknownProfileAndReportsItsCurrentPeriod() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.synchronizeProfile(any())).thenReturn(player(true, true));
        when(store.findParticipationPeriod(PLAYER, LocalDate.of(2026, 7, 29)))
                .thenReturn(Optional.of(new ParticipationPeriod(PLAYER, LocalDate.of(2026, 7, 27), null)));

        var status = service(store).status(identity(ADMIN), identity(PLAYER));

        assertThat(status.known()).isTrue();
        assertThat(status.message()).isEqualTo("Teilnahme: aktiv seit 2026-07-27; Reminder: an.");
        verify(store).synchronizeProfile(new PlayerStore.ProfileUpdate(PLAYER, "Player", false));
    }

    @Test
    void adminStatusCreatesAndSynchronizesAnUnknownTargetWithoutActivatingIt() {
        PlayerStore store = mock(PlayerStore.class);
        long target = 3L;
        when(store.synchronizeProfile(any())).thenReturn(new PlayerStore.StoredPlayer(
                target, "Player", false, false, false, Instant.EPOCH, Instant.EPOCH));
        PlayerParticipationService service = service(store);

        var status = service.status(identity(ADMIN), identity(target));

        assertThat(status.authorized()).isTrue();
        assertThat(status.active()).isFalse();
        assertThat(status.message()).isEqualTo("Teilnahme: inaktiv; Reminder: aus.");
        verify(store).synchronizeProfile(new PlayerStore.ProfileUpdate(target, "Player", false));
        verify(store, never()).activate(any());
    }

    @Test
    void administratorIdentityIsSynchronizedFromExternalConfiguration() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.synchronizeProfile(any())).thenReturn(new PlayerStore.StoredPlayer(
                ADMIN, "Player", false, true, false, Instant.EPOCH, Instant.EPOCH));

        service(store).status(identity(ADMIN), identity(PLAYER));

        verify(store).synchronizeProfile(new PlayerStore.ProfileUpdate(ADMIN, "Player", true));
    }

    @Test
    void joinAndAdminActivationRefreshTodaysStatusButProspectiveLeaveDoesNot() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.activate(any())).thenReturn(player(true, false));
        when(store.deactivate(any())).thenReturn(player(true, false));
        when(store.synchronizeProfile(any())).thenReturn(new PlayerStore.StoredPlayer(
                ADMIN, "Admin", true, true, false, Instant.EPOCH, Instant.EPOCH));
        java.util.List<LocalDate> refreshes = new java.util.ArrayList<>();
        PlayerParticipationService service = new PlayerParticipationService(
                store, CLOCK, BERLIN, Set.of(ADMIN), refreshes::add);

        service.join(identity(PLAYER));
        service.leave(identity(PLAYER));
        service.activate(identity(ADMIN), identity(PLAYER));

        assertThat(refreshes).containsExactly(LocalDate.of(2026, 7, 29), LocalDate.of(2026, 7, 29));
    }

    @Test
    void statusRefreshFailureDoesNotRollBackSuccessfulJoin() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.activate(any())).thenReturn(player(true, false));
        PlayerParticipationService service = new PlayerParticipationService(store, CLOCK, BERLIN, Set.of(ADMIN),
                ignored -> { throw new IllegalStateException("status unavailable"); });

        assertThat(service.join(identity(PLAYER)).active()).isTrue();
        verify(store).activate(any());
    }
    private static PlayerParticipationService service(PlayerStore store) {
        return new PlayerParticipationService(store, CLOCK, BERLIN, Set.of(ADMIN));
    }
    private static PlayerIdentity identity(long id) { return new PlayerIdentity(id, "Player"); }
    private static PlayerStore.StoredPlayer player(boolean active, boolean reminders) {
        return new PlayerStore.StoredPlayer(PLAYER, "Player", active, false, reminders, Instant.EPOCH, Instant.EPOCH);
    }
}
