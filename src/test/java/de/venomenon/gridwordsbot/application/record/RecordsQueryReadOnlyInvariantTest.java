package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.in.RecordsQueryUseCase;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordsQueryReadOnlyInvariantTest {
    @Test
    void queryTouchesOnlyTheMaterializedReadOperations() {
        RecordDefinitionCatalog catalog = RecordDefinitionCatalog.recordsV1();
        RecordStateStore stateStore = mock(RecordStateStore.class);
        RecordBootstrapStore bootstrapStore = mock(RecordBootstrapStore.class);
        PlayerStore players = mock(PlayerStore.class);
        RecordBootstrapKey bootstrapKey = new RecordBootstrapKey(1, catalog.version());
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        when(bootstrapStore.find(bootstrapKey)).thenReturn(Optional.of(new RecordBootstrapSnapshot(
                bootstrapKey, RecordWorkState.SUCCEEDED, Optional.empty(), Optional.empty(), Optional.of(now),
                Optional.of(now), 1, Optional.empty(), Optional.empty(), now, now)));
        when(stateStore.findAll(1, catalog.version())).thenReturn(List.of());
        when(players.findAllPlayers()).thenReturn(List.of());
        RecordsQueryService service = new RecordsQueryService(
                new RecordStateReadService(stateStore), new RecordBootstrapReadService(bootstrapStore), catalog, players);

        assertThat(service.query(new RecordsQueryUseCase.Query(
                1, 7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.ALL)))
                .isInstanceOf(RecordsQueryUseCase.Ready.class);

        verify(bootstrapStore).find(bootstrapKey);
        verify(stateStore).findAll(1, catalog.version());
        verify(players).findAllPlayers();
        verifyNoMoreInteractions(bootstrapStore, stateStore, players);
    }
}
