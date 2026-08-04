package de.venomenon.gridwordsbot.application.excuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferMetadata;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.port.out.CanonicalRefreshWakeUp;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExcuseExpirationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");

    @Test
    void expiresDueOffersInBoundedPagesAndWakesOnlyCommittedTransitions() {
        ExcuseStateStore states = mock(ExcuseStateStore.class);
        CanonicalRefreshWakeUp wakeUp = mock(CanonicalRefreshWakeUp.class);
        ExcuseExpirationService service = new ExcuseExpirationService(
                states, wakeUp, Clock.fixed(NOW, ZoneOffset.UTC), 2, 2);
        when(states.findDueExpirations(NOW, 2)).thenReturn(List.of(available(1), available(2)), List.of(available(3)));
        when(states.expireAndRequestCanonicalRefresh(1, NOW)).thenReturn(Optional.of(expired(1)));
        when(states.expireAndRequestCanonicalRefresh(2, NOW)).thenReturn(Optional.empty());
        when(states.expireAndRequestCanonicalRefresh(3, NOW)).thenReturn(Optional.of(expired(3)));

        assertThat(service.reconcile()).isEqualTo(2);

        verify(states, times(2)).findDueExpirations(NOW, 2);
        verify(wakeUp).wakeUp(1);
        verify(wakeUp).wakeUp(3);
    }

    @Test
    void stopsAfterConfiguredPageLimitWithoutAHotLoop() {
        ExcuseStateStore states = mock(ExcuseStateStore.class);
        ExcuseExpirationService service = new ExcuseExpirationService(
                states, mock(CanonicalRefreshWakeUp.class), Clock.fixed(NOW, ZoneOffset.UTC), 1, 1);
        when(states.findDueExpirations(NOW, 1)).thenReturn(List.of(available(1)));
        when(states.expireAndRequestCanonicalRefresh(1, NOW)).thenReturn(Optional.of(expired(1)));

        assertThat(service.reconcile()).isEqualTo(1);
        verify(states, times(1)).findDueExpirations(NOW, 1);
    }

    private static ExcuseState available(long resultId) {
        return new ExcuseState(resultId, ExcuseStatus.AVAILABLE,
                Optional.of(new ExcuseOfferMetadata(1, "catalog", "context", 1, NOW.minusSeconds(60), NOW)),
                Optional.empty(), false, Optional.empty(), NOW.minusSeconds(60), NOW.minusSeconds(60));
    }

    private static ExcuseState expired(long resultId) {
        return new ExcuseState(resultId, ExcuseStatus.EXPIRED, available(resultId).offer(),
                Optional.empty(), false, Optional.empty(), NOW.minusSeconds(60), NOW);
    }
}
