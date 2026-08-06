package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordLiveEvaluationStore;
import de.venomenon.gridwordsbot.port.out.RecordLiveHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordLiveEvaluationProcessorTest {
    @Test
    void staleClaimIsFencedBeforeHistoryStateEventOrAnnouncementWrites() {
        RecordLiveEvaluationStore work = mock(RecordLiveEvaluationStore.class);
        RecordLiveHistoryQuery history = mock(RecordLiveHistoryQuery.class);
        RecordBootstrapReadService bootstrap = new RecordBootstrapReadService(mock(RecordBootstrapStore.class));
        RecordStateService states = mock(RecordStateService.class);
        RecordEventStore events = mock(RecordEventStore.class);
        RecordAnnouncementStore announcements = mock(RecordAnnouncementStore.class);
        RecordLiveEvaluationClaim claim = new RecordLiveEvaluationClaim(
                new RecordLiveEvaluationKey(1, 2, 0), RecordProcessingOrigin.LIVE_SUBMISSION,
                UUID.randomUUID(), Instant.parse("2026-08-06T10:00:00Z"), 1);
        when(work.fence(claim.key(), claim.token(), Instant.parse("2026-08-06T09:00:00Z"))).thenReturn(false);

        RecordLiveEvaluationProcessor processor = new RecordLiveEvaluationProcessor(work, history, bootstrap, states,
                events, announcements, directTransactions(), RecordDefinitionCatalog.recordsV1(),
                Clock.fixed(Instant.parse("2026-08-06T09:00:00Z"), ZoneOffset.UTC), 3);

        assertThat(processor.process(claim)).isEqualTo(RecordLiveEvaluationProcessor.ProcessingResult.FENCED_OUT);
        verify(history, never()).loadFor(claim.key());
        verify(work, never()).markSucceeded(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static RecordTransactionRunner directTransactions() {
        return new RecordTransactionRunner() {
            @Override public <T> T inTransaction(java.util.function.Supplier<T> work) { return work.get(); }
        };
    }
}
