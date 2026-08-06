package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseKey;
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordDayCloseStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordDayCloseServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T04:01:00Z");
    private static final LocalDate CLOSED_DAY = LocalDate.of(2026, 7, 28);

    @Test
    void registersAndCompletesTheMissingDayExactlyOnce() {
        RecordDefinitionCatalog catalog = RecordDefinitionCatalog.recordsV1();
        RecordDayCloseKey key = new RecordDayCloseKey(1, catalog.version(), CLOSED_DAY);
        RecordDayCloseStore work = mock(RecordDayCloseStore.class);
        RecordHistoryQuery history = mock(RecordHistoryQuery.class);
        RecordBootstrapStore bootstrapStore = mock(RecordBootstrapStore.class);
        RecordStateService states = mock(RecordStateService.class);
        RecordEventStore events = mock(RecordEventStore.class);
        RecordAnnouncementStore announcements = mock(RecordAnnouncementStore.class);
        RecordHistorySnapshot canonical = new RecordHistorySnapshot(List.of(), List.of(
                new GameParticipationPeriod(11, GameType.GRIDWORDS, CLOSED_DAY, null)));
        UUID token = UUID.randomUUID();

        when(history.load(1)).thenReturn(canonical);
        when(bootstrapStore.find(new RecordBootstrapKey(1, catalog.version()))).thenReturn(Optional.of(readyBootstrap(catalog)));
        when(work.latestSucceededDate(1, catalog.version().value())).thenReturn(Optional.empty());
        when(work.register(key)).thenReturn(open(key));
        when(work.claim(any(RecordDayCloseKey.class), any(RecordLeaseClaimRequest.class)))
                .thenReturn(Optional.of(new RecordDayCloseClaim(key, token, NOW.plusSeconds(120), 1)));
        when(work.renewLease(any(), any(), any())).thenReturn(true);
        when(work.fence(key, token, NOW)).thenReturn(true);
        when(work.markSucceeded(key, token, NOW)).thenReturn(true);
        when(states.states(1, catalog.version())).thenReturn(List.of());

        RecordDayCloseService service = new RecordDayCloseService(work, history,
                new RecordBootstrapReadService(bootstrapStore), states, events, announcements, directTransactions(),
                catalog, Clock.fixed(NOW, ZoneOffset.UTC), 12);

        assertThat(service.reconcileThrough(1, CLOSED_DAY)).isEqualTo(1);

        verify(work).register(key);
        verify(work).markSucceeded(key, token, NOW);
    }

    private static RecordBootstrapSnapshot readyBootstrap(RecordDefinitionCatalog catalog) {
        return new RecordBootstrapSnapshot(new RecordBootstrapKey(1, catalog.version()), RecordWorkState.SUCCEEDED,
                Optional.empty(), Optional.empty(), Optional.of(NOW), Optional.of(NOW), 1,
                Optional.empty(), Optional.empty(), NOW, NOW);
    }

    private static RecordDayCloseSnapshot open(RecordDayCloseKey key) {
        return new RecordDayCloseSnapshot(key, RecordWorkState.OPEN, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0, Optional.empty(), Optional.empty(), NOW, NOW);
    }

    private static RecordTransactionRunner directTransactions() {
        return new RecordTransactionRunner() {
            @Override public <T> T inTransaction(java.util.function.Supplier<T> work) { return work.get(); }
        };
    }
}
