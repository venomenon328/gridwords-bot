package de.venomenon.gridwordsbot.application.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class GridWordsSourceDeletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");
    private static final long SOURCE = 10L;
    private static final UUID TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private SubmissionStore submissions;
    private SourceMessageDeletionGateway discord;
    private PublicationRetryScheduler scheduler;
    private GridWordsSourceDeletionService service;

    @BeforeEach
    void setUp() {
        submissions = mock(SubmissionStore.class);
        discord = mock(SourceMessageDeletionGateway.class);
        scheduler = mock(PublicationRetryScheduler.class);
        service = new GridWordsSourceDeletionService(
                submissions, discord, Clock.fixed(NOW, ZoneOffset.UTC), scheduler);
    }

    @Test
    void persistsCanonicalPublicationBeforeDeletingTheExactSourceAndThenCompletes() {
        published();
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any()))
                .thenReturn(Optional.of(new SubmissionStore.SourceDeletionClaim(TOKEN, NOW.plusSeconds(60))));
        when(discord.deleteSourceMessage(12L, SOURCE))
                .thenReturn(SourceMessageDeletionGateway.DeletionResult.DELETED);
        when(submissions.recordOriginalSourceDeleted(SOURCE, TOKEN)).thenReturn(true);
        when(submissions.completeOriginalSourceDeletion(SOURCE)).thenReturn(true);

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isTrue();

        InOrder order = inOrder(submissions, discord);
        order.verify(submissions).claimOriginalSourceDeletion(eq(SOURCE), any());
        order.verify(discord).deleteSourceMessage(12L, SOURCE);
        order.verify(submissions).recordOriginalSourceDeleted(SOURCE, TOKEN);
        order.verify(submissions).completeOriginalSourceDeletion(SOURCE);
        verify(discord, never()).deleteSourceMessage(12L, 99L);
    }

    @Test
    void alreadyMissingSourceIsCompletedIdempotently() {
        published();
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any()))
                .thenReturn(Optional.of(new SubmissionStore.SourceDeletionClaim(TOKEN, NOW.plusSeconds(60))));
        when(discord.deleteSourceMessage(12L, SOURCE))
                .thenReturn(SourceMessageDeletionGateway.DeletionResult.ALREADY_MISSING);
        when(submissions.recordOriginalSourceDeleted(SOURCE, TOKEN)).thenReturn(true);
        when(submissions.completeOriginalSourceDeletion(SOURCE)).thenReturn(true);

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isTrue();

        verify(submissions).recordOriginalSourceDeleted(SOURCE, TOKEN);
        verify(submissions).completeOriginalSourceDeletion(SOURCE);
    }

    @Test
    void transientDeleteFailureRemainsRetryableWithoutDeletingAnotherMessage() {
        published();
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any()))
                .thenReturn(Optional.of(new SubmissionStore.SourceDeletionClaim(TOKEN, NOW.plusSeconds(60))));
        when(discord.deleteSourceMessage(12L, SOURCE))
                .thenReturn(SourceMessageDeletionGateway.DeletionResult.RETRYABLE_FAILURE);
        when(submissions.recordOriginalSourceDeletionFailure(
                SOURCE, TOKEN, SubmissionStore.OriginalDeletionFailure.RETRYABLE,
                "source message deletion failed transiently")).thenReturn(true);
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isFalse();

        verify(submissions).recordOriginalSourceDeletionFailure(
                SOURCE, TOKEN, SubmissionStore.OriginalDeletionFailure.RETRYABLE,
                "source message deletion failed transiently");
        verify(scheduler).schedule(eq(NOW.plusSeconds(61)), retry.capture());
        verify(discord, never()).deleteSourceMessage(12L, 99L);
    }

    @Test
    void transientRetryCanScheduleAnotherAttemptAfterItsFirstWakeUp() {
        published();
        UUID nextToken = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any())).thenReturn(
                Optional.of(new SubmissionStore.SourceDeletionClaim(TOKEN, NOW.plusSeconds(60))),
                Optional.of(new SubmissionStore.SourceDeletionClaim(nextToken, NOW.plusSeconds(60))));
        when(discord.deleteSourceMessage(12L, SOURCE))
                .thenReturn(SourceMessageDeletionGateway.DeletionResult.RETRYABLE_FAILURE);
        when(submissions.recordOriginalSourceDeletionFailure(
                eq(SOURCE), any(), eq(SubmissionStore.OriginalDeletionFailure.RETRYABLE), any()))
                .thenReturn(true);
        ArgumentCaptor<Runnable> firstRetry = ArgumentCaptor.forClass(Runnable.class);

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isFalse();
        verify(scheduler).schedule(eq(NOW.plusSeconds(61)), firstRetry.capture());

        firstRetry.getValue().run();

        verify(scheduler, times(2)).schedule(eq(NOW.plusSeconds(61)), any());
    }

    @Test
    void permanentDeleteFailureIsRecordedWithoutAHotRetryLoop() {
        published();
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any()))
                .thenReturn(Optional.of(new SubmissionStore.SourceDeletionClaim(TOKEN, NOW.plusSeconds(60))));
        when(discord.deleteSourceMessage(12L, SOURCE))
                .thenReturn(SourceMessageDeletionGateway.DeletionResult.PERMANENT_FAILURE);
        when(submissions.recordOriginalSourceDeletionFailure(
                SOURCE, TOKEN, SubmissionStore.OriginalDeletionFailure.PERMANENT,
                "source message deletion was denied permanently")).thenReturn(true);

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isFalse();

        verify(submissions).recordOriginalSourceDeletionFailure(
                SOURCE, TOKEN, SubmissionStore.OriginalDeletionFailure.PERMANENT,
                "source message deletion was denied permanently");
        verifyNoInteractions(scheduler);
    }

    @Test
    void crashAfterDiscordDeleteNeedsNoSecondDeleteWhenThePersistedStateIsRecovered() {
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(
                stored(SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED)));
        when(submissions.completeOriginalSourceDeletion(SOURCE)).thenReturn(true);

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isTrue();

        verify(submissions).completeOriginalSourceDeletion(SOURCE);
        verifyNoInteractions(discord, scheduler);
    }

    @Test
    void stateBeforeCanonicalPublicationCannotReachDiscordDeletion() {
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(
                stored(SubmissionStore.SubmissionState.RESULT_STORED)));

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isFalse();

        verify(submissions, never()).claimOriginalSourceDeletion(anyLong(), any());
        verifyNoInteractions(discord, scheduler);
    }

    @Test
    void completedReplayDoesNotIssueAnotherDiscordDelete() {
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(
                stored(SubmissionStore.SubmissionState.COMPLETED)));

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isTrue();

        verify(submissions, never()).claimOriginalSourceDeletion(anyLong(), any());
        verifyNoInteractions(discord, scheduler);
    }

    @Test
    void staleClaimCannotFalselyCompleteTheDeletion() {
        published();
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any()))
                .thenReturn(Optional.of(new SubmissionStore.SourceDeletionClaim(TOKEN, NOW.plusSeconds(60))));
        when(discord.deleteSourceMessage(12L, SOURCE))
                .thenReturn(SourceMessageDeletionGateway.DeletionResult.DELETED);
        when(submissions.recordOriginalSourceDeleted(SOURCE, TOKEN)).thenReturn(false);

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isFalse();

        verify(submissions, never()).completeOriginalSourceDeletion(SOURCE);
    }

    @Test
    void startupRecoveryProcessesOnlyPersistedOpenWork() {
        SubmissionStore.StoredSubmission open = stored(SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED);
        when(submissions.findGridWordsAwaitingOriginalSourceDeletion()).thenReturn(List.of(open));
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(open));
        when(submissions.completeOriginalSourceDeletion(SOURCE)).thenReturn(true);

        service.resumeOpenDeletions();

        verify(submissions).completeOriginalSourceDeletion(SOURCE);
        verifyNoInteractions(discord);
    }

    @Test
    void startupRecoverySchedulesAnActivePersistedLeaseAndRetriesItAfterExpiry() {
        MutableClock mutableClock = new MutableClock(NOW);
        service = new GridWordsSourceDeletionService(submissions, discord, mutableClock, scheduler);
        SubmissionStore.StoredSubmission busy = storedWithLease(NOW.plusSeconds(60));
        SubmissionStore.StoredSubmission available = stored(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED);
        when(submissions.findGridWordsAwaitingOriginalSourceDeletion()).thenReturn(List.of(busy));
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(busy), Optional.of(available));
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any())).thenReturn(
                Optional.of(new SubmissionStore.SourceDeletionClaim(TOKEN, NOW.plusSeconds(121))));
        when(discord.deleteSourceMessage(12L, SOURCE))
                .thenReturn(SourceMessageDeletionGateway.DeletionResult.DELETED);
        when(submissions.recordOriginalSourceDeleted(SOURCE, TOKEN)).thenReturn(true);
        when(submissions.completeOriginalSourceDeletion(SOURCE)).thenReturn(true);
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);

        service.resumeOpenDeletions();

        verify(scheduler).schedule(eq(NOW.plusSeconds(61)), retry.capture());
        verify(submissions, never()).claimOriginalSourceDeletion(eq(SOURCE), any());
        mutableClock.set(NOW.plusSeconds(61));
        retry.getValue().run();

        verify(discord).deleteSourceMessage(12L, SOURCE);
        verify(submissions).completeOriginalSourceDeletion(SOURCE);
    }

    @Test
    void claimCollisionSchedulesThePersistedBusyLeaseForRecovery() {
        SubmissionStore.StoredSubmission available = stored(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED);
        SubmissionStore.StoredSubmission busy = storedWithLease(NOW.plusSeconds(60));
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(available), Optional.of(busy));
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any())).thenReturn(Optional.empty());

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isFalse();

        verify(scheduler).schedule(eq(NOW.plusSeconds(61)), any());
        verifyNoInteractions(discord);
    }

    @Test
    void confirmedCorrectionReconcilesItsSourceAndOlderEligibleSourcesOfTheSameResult() {
        long olderSource = 9L;
        long unrelatedSource = 11L;
        long resultId = 20L;
        SubmissionStore.StoredSubmission current = stored(
                SOURCE, resultId, SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED);
        SubmissionStore.StoredSubmission older = stored(
                olderSource, resultId, SubmissionStore.SubmissionState.SUPERSEDED);
        SubmissionStore.StoredSubmission unrelated = stored(
                unrelatedSource, 21L, SubmissionStore.SubmissionState.SUPERSEDED);
        UUID olderToken = UUID.fromString("00000000-0000-0000-0000-000000000003");

        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(current), Optional.of(current));
        when(submissions.findBySourceMessageId(olderSource)).thenReturn(Optional.of(older));
        when(submissions.findGridWordsAwaitingOriginalSourceDeletion())
                .thenReturn(List.of(current, older, unrelated));
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any()))
                .thenReturn(Optional.of(new SubmissionStore.SourceDeletionClaim(TOKEN, NOW.plusSeconds(60))));
        when(submissions.claimOriginalSourceDeletion(eq(olderSource), any()))
                .thenReturn(Optional.of(new SubmissionStore.SourceDeletionClaim(olderToken, NOW.plusSeconds(60))));
        when(discord.deleteSourceMessage(12L, SOURCE))
                .thenReturn(SourceMessageDeletionGateway.DeletionResult.DELETED);
        when(discord.deleteSourceMessage(12L, olderSource))
                .thenReturn(SourceMessageDeletionGateway.DeletionResult.DELETED);
        when(submissions.recordOriginalSourceDeleted(SOURCE, TOKEN)).thenReturn(true);
        when(submissions.recordOriginalSourceDeleted(olderSource, olderToken)).thenReturn(true);
        when(submissions.completeOriginalSourceDeletion(SOURCE)).thenReturn(true);
        when(submissions.completeOriginalSourceDeletion(olderSource)).thenReturn(true);

        service.reconcileAfterCanonicalPublication(SOURCE);

        InOrder order = inOrder(discord);
        order.verify(discord).deleteSourceMessage(12L, SOURCE);
        order.verify(discord).deleteSourceMessage(12L, olderSource);
        verify(discord, never()).deleteSourceMessage(12L, unrelatedSource);
        verify(submissions, times(1)).claimOriginalSourceDeletion(eq(SOURCE), any());
        verify(submissions, times(1)).claimOriginalSourceDeletion(eq(olderSource), any());
        verify(submissions, never()).claimOriginalSourceDeletion(eq(unrelatedSource), any());
    }

    private void published() {
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(
                stored(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED)));
    }

    private static SubmissionStore.StoredSubmission stored(SubmissionStore.SubmissionState state) {
        return stored(SOURCE, 20L, state);
    }

    private static SubmissionStore.StoredSubmission stored(
            long sourceMessageId, long resultId, SubmissionStore.SubmissionState state) {
        return new SubmissionStore.StoredSubmission(
                sourceMessageId,
                11L,
                12L,
                101L,
                "share",
                state,
                Optional.of(resultId),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static SubmissionStore.StoredSubmission storedWithLease(Instant leaseUntil) {
        return new SubmissionStore.StoredSubmission(
                SOURCE, 11L, 12L, 101L, "share",
                SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                Optional.of(20L), List.of(), Optional.empty(), Optional.empty(),
                SubmissionStore.PublicationContext.none(), Optional.empty(),
                SubmissionStore.OriginalDeletionFailure.NONE, Optional.of(leaseUntil), Instant.EPOCH, Instant.EPOCH);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
