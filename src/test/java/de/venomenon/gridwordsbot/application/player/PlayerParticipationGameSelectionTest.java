package de.venomenon.gridwordsbot.application.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameParticipationSelection;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase.PlayerIdentity;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerParticipationGameSelectionTest {
    private static final long PLAYER = 42L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 3);

    @Test
    void singleGameJoinUsesTheTypedStoreMutation() {
        PlayerStore store = mock(PlayerStore.class);
        PlayerStore.StoredPlayer stored = new PlayerStore.StoredPlayer(
                PLAYER, "Player", true, false, true, Instant.EPOCH, Instant.EPOCH);
        when(store.activateGames(any())).thenReturn(stored);
        PlayerParticipationService service = new PlayerParticipationService(
                store, Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant(), ZoneId.of("UTC")),
                ZoneId.of("Europe/Berlin"), Set.of());

        assertThat(service.join(new PlayerIdentity(PLAYER, "Player"), GameParticipationSelection.GRIDWORDS).active())
                .isTrue();

        verify(store).activateGames(new PlayerStore.GameParticipationChange(
                new PlayerStore.ProfileUpdate(PLAYER, "Player", false), GameParticipationSelection.GRIDWORDS, TODAY));
    }
}
