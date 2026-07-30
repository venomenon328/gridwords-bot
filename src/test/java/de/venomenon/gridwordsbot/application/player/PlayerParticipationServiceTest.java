package de.venomenon.gridwordsbot.application.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase.PlayerIdentity;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.Instant;
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
                java.time.LocalDate.of(2026, 7, 29)));
        verify(store).deactivate(new PlayerStore.ParticipationChange(new PlayerStore.ProfileUpdate(PLAYER, "Player", false),
                java.time.LocalDate.of(2026, 7, 30)));
    }

    @Test
    void deniesAdminMutationWithoutPersistingAnything() {
        PlayerStore store = mock(PlayerStore.class);
        PlayerParticipationService service = service(store);

        assertThat(service.activate(identity(PLAYER), identity(3L)).authorized()).isFalse();

        verify(store, never()).activate(any());
    }

    @Test
    void createsAnInactiveProfileForReminderOptIn() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.setReminderOptIn(any(), any(Boolean.class))).thenReturn(player(false, true));

        assertThat(service(store).enableReminders(identity(PLAYER)).active()).isFalse();
        verify(store).setReminderOptIn(new PlayerStore.ProfileUpdate(PLAYER, "Player", false), true);
    }

    @Test
    void reportsUnknownProfilesWithoutCreatingOne() {
        PlayerStore store = mock(PlayerStore.class);
        when(store.findByDiscordUserId(PLAYER)).thenReturn(Optional.empty());

        assertThat(service(store).status(identity(PLAYER)).known()).isFalse();
    }

    private static PlayerParticipationService service(PlayerStore store) {
        return new PlayerParticipationService(store, CLOCK, BERLIN, Set.of(ADMIN));
    }
    private static PlayerIdentity identity(long id) { return new PlayerIdentity(id, "Player"); }
    private static PlayerStore.StoredPlayer player(boolean active, boolean reminders) {
        return new PlayerStore.StoredPlayer(PLAYER, "Player", active, false, reminders, Instant.EPOCH, Instant.EPOCH);
    }
}
