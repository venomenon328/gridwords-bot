package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordLiveEvaluationStore;
import de.venomenon.gridwordsbot.port.out.RecordLiveHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordRetryableFailure;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.LocalDate;
import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordLiveEvaluationProcessorTest {
    @Test
    void lostHeartbeatFencesBeforeCanonicalReadsOrAnyRecordWrites() {
        RecordLiveEvaluationStore work = mock(RecordLiveEvaluationStore.class);
        RecordLiveHistoryQuery history = mock(RecordLiveHistoryQuery.class);
        RecordBootstrapReadService bootstrap = new RecordBootstrapReadService(mock(RecordBootstrapStore.class));
        RecordStateService states = mock(RecordStateService.class);
        RecordEventStore events = mock(RecordEventStore.class);
        RecordAnnouncementStore announcements = mock(RecordAnnouncementStore.class);
        RecordLiveEvaluationClaim claim = new RecordLiveEvaluationClaim(
                new RecordLiveEvaluationKey(1, 2, 0), RecordProcessingOrigin.LIVE_SUBMISSION,
                UUID.randomUUID(), Instant.parse("2026-08-06T10:00:00Z"), 1);
        RecordLiveEvaluationProcessor processor = new RecordLiveEvaluationProcessor(work, history, bootstrap, states,
                events, announcements, directTransactions(), RecordDefinitionCatalog.recordsV1(),
                Clock.fixed(Instant.parse("2026-08-06T09:00:00Z"), ZoneOffset.UTC), 3);

        assertThat(processor.process(claim, () -> false))
                .isEqualTo(RecordLiveEvaluationProcessor.ProcessingResult.FENCED_OUT);

        verify(history, never()).loadFor(claim.key(), claim.processingOrigin());
        verify(work, never()).fence(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(work, never()).markSucceeded(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleClaimMayReadOutsideTheWriteTransactionButIsFencedBeforeAnyWrite() {
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
        when(history.loadFor(claim.key(), claim.processingOrigin())).thenReturn(new RecordHistorySnapshot(List.of(
                new RecordHistorySnapshot.Result(2, 0, 1, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6),
                        new ShareOutcome.Solved(3, 6), Duration.ofSeconds(60), Instant.parse("2026-08-06T08:00:00Z"))),
                List.of(new GameParticipationPeriod(1, GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), null))));

        RecordLiveEvaluationProcessor processor = new RecordLiveEvaluationProcessor(work, history, bootstrap, states,
                events, announcements, directTransactions(), RecordDefinitionCatalog.recordsV1(),
                Clock.fixed(Instant.parse("2026-08-06T09:00:00Z"), ZoneOffset.UTC), 3);

        assertThat(processor.process(claim)).isEqualTo(RecordLiveEvaluationProcessor.ProcessingResult.FENCED_OUT);
        verify(history).loadFor(claim.key(), claim.processingOrigin());
        verify(work, never()).markSucceeded(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void threeChangedCanonicalGenerationsExhaustTheReplanAsRetryableFailure() {
        RecordLiveEvaluationStore work = mock(RecordLiveEvaluationStore.class);
        RecordLiveHistoryQuery history = mock(RecordLiveHistoryQuery.class);
        RecordBootstrapReadService bootstrap = new RecordBootstrapReadService(mock(RecordBootstrapStore.class));
        RecordStateService states = mock(RecordStateService.class);
        RecordEventStore events = mock(RecordEventStore.class);
        RecordAnnouncementStore announcements = mock(RecordAnnouncementStore.class);
        RecordLiveEvaluationClaim claim = new RecordLiveEvaluationClaim(
                new RecordLiveEvaluationKey(1, 2, 1), RecordProcessingOrigin.NORMAL_CORRECTION,
                UUID.randomUUID(), Instant.parse("2026-08-06T10:00:00Z"), 1);
        RecordHistorySnapshot canonical = new RecordHistorySnapshot(List.of(
                new RecordHistorySnapshot.Result(2, 1, 1, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6),
                        new ShareOutcome.Solved(3, 6), Duration.ofSeconds(60),
                        Instant.parse("2026-08-06T08:00:00Z"))),
                List.of(new GameParticipationPeriod(1, GameType.GRIDWORDS,
                        LocalDate.of(2026, 8, 1), null)));
        when(history.loadFor(claim.key(), claim.processingOrigin())).thenReturn(canonical);
        when(states.states(1, RecordDefinitionCatalog.recordsV1().version())).thenReturn(List.of());
        when(work.fence(claim.key(), claim.token(), Instant.parse("2026-08-06T09:00:00Z"))).thenReturn(true);
        when(history.isCurrent(claim.key(), claim.processingOrigin(), canonical)).thenReturn(false);

        RecordLiveEvaluationProcessor processor = new RecordLiveEvaluationProcessor(work, history, bootstrap, states,
                events, announcements, directTransactions(), RecordDefinitionCatalog.recordsV1(),
                Clock.fixed(Instant.parse("2026-08-06T09:00:00Z"), ZoneOffset.UTC), 3);

        assertThatThrownBy(() -> processor.process(claim))
                .isInstanceOf(RecordRetryableFailure.class)
                .hasMessageContaining("three canonical generation changes");
        verify(history, times(3)).loadFor(claim.key(), claim.processingOrigin());
        verify(history, times(3)).isCurrent(claim.key(), claim.processingOrigin(), canonical);
        verify(work, times(3)).fence(claim.key(), claim.token(), Instant.parse("2026-08-06T09:00:00Z"));
    }

    private static RecordTransactionRunner directTransactions() {
        return new RecordTransactionRunner() {
            @Override public <T> T inTransaction(java.util.function.Supplier<T> work) { return work.get(); }
        };
    }
}
