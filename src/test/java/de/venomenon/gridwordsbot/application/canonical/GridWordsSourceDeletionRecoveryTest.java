package de.venomenon.gridwordsbot.application.canonical;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceDeletionRecoveryStore;
import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GridWordsSourceDeletionRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");
    private static final long SOURCE = 10L;
    private static final long RESULT = 20L;
    private static final UUID TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000041");

    @Test
    void startupReactivatesPermanentFailuresBeforeReadingAndCompletingRecoveryWork() {
        SubmissionStore submissions = mock(SubmissionStore.class);
        SourceMessageDeletionGateway discord = mock(SourceMessageDeletionGateway.class);
        PublicationRetryScheduler scheduler = mock(PublicationRetryScheduler.class);
        SourceDeletionRecoveryStore recovery = mock(SourceDeletionRecoveryStore.class);
        GridWordsSourceDeletionService service = new GridWordsSourceDeletionService(
                submissions, discord, Clock.fixed(NOW, ZoneOffset.UTC), scheduler, recovery);
        SubmissionStore.StoredSubmission stored = permanentSubmission(SOURCE, RESULT);

        when(recovery.reactivatePermanentFailures(OptionalLong.empty())).thenReturn(1);
        when(submissions.findGridWordsAwaitingOriginalSourceDeletion()).thenReturn(List.of(stored));
        when(submissions.findAwaitingOriginalSourceDeletion(GameType.QUADWORDS)).thenReturn(List.of());
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(stored));
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any())).thenReturn(Optional.of(
                new SubmissionStore.SourceDeletionClaim(TOKEN, NOW.plusSeconds(60))));
        when(discord.deleteSourceMessage(12L, SOURCE)).thenReturn(SourceMessageDeletionGateway.DeletionResult.DELETED);
        when(submissions.recordOriginalSourceDeleted(SOURCE, TOKEN)).thenReturn(true);
        when(submissions.completeOriginalSourceDeletion(SOURCE)).thenReturn(true);

        service.resumeOpenDeletions();

        InOrder order = inOrder(recovery, submissions, discord);
        order.verify(recovery).reactivatePermanentFailures(OptionalLong.empty());
        order.verify(submissions).findGridWordsAwaitingOriginalSourceDeletion();
        order.verify(discord).deleteSourceMessage(12L, SOURCE);
        verify(submissions).completeOriginalSourceDeletion(SOURCE);
        verifyNoInteractions(scheduler);
    }

    @Test
    void successfulCorrectionReactivatesOnlyPermanentFailuresOfItsOwnResult() {
        SubmissionStore submissions = mock(SubmissionStore.class);
        SourceMessageDeletionGateway discord = mock(SourceMessageDeletionGateway.class);
        PublicationRetryScheduler scheduler = mock(PublicationRetryScheduler.class);
        SourceDeletionRecoveryStore recovery = mock(SourceDeletionRecoveryStore.class);
        GridWordsSourceDeletionService service = new GridWordsSourceDeletionService(
                submissions, discord, Clock.fixed(NOW, ZoneOffset.UTC), scheduler, recovery);
        SubmissionStore.StoredSubmission current = publishedSubmission(SOURCE, RESULT);

        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(current));
        when(submissions.findGridWordsAwaitingOriginalSourceDeletion()).thenReturn(List.of());
        when(submissions.findAwaitingOriginalSourceDeletion(GameType.QUADWORDS)).thenReturn(List.of());
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any())).thenReturn(Optional.empty());

        service.reconcileAfterCanonicalPublication(SOURCE);

        verify(recovery).reactivatePermanentFailures(OptionalLong.of(RESULT));
        verify(recovery, never()).reactivatePermanentFailures(OptionalLong.empty());
        verifyNoInteractions(discord);
        verifyNoInteractions(scheduler);
    }

    private static SubmissionStore.StoredSubmission permanentSubmission(long sourceMessageId, long resultId) {
        return new SubmissionStore.StoredSubmission(
                sourceMessageId, 11L, 12L, 101L, "share",
                SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                Optional.of(resultId), List.of(), Optional.empty(), Optional.of("permission denied"),
                SubmissionStore.PublicationContext.none(), Optional.empty(),
                SubmissionStore.OriginalDeletionFailure.PERMANENT, Optional.empty(), NOW, NOW);
    }

    private static SubmissionStore.StoredSubmission publishedSubmission(long sourceMessageId, long resultId) {
        return new SubmissionStore.StoredSubmission(
                sourceMessageId, 11L, 12L, 101L, "share",
                SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                Optional.of(resultId), List.of(), Optional.empty(), Optional.empty(),
                SubmissionStore.PublicationContext.none(), Optional.empty(),
                SubmissionStore.OriginalDeletionFailure.NONE, Optional.empty(), NOW, NOW);
    }
}
