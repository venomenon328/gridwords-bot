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
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceDeletionRecoveryStore;
import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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
    void startupDoesNotGloballyReactivatePermanentFailuresBeforeReadingAndCompletingRecoveryWork() {
        SubmissionStore submissions = mock(SubmissionStore.class);
        SourceMessageDeletionGateway discord = mock(SourceMessageDeletionGateway.class);
        PublicationRetryScheduler scheduler = mock(PublicationRetryScheduler.class);
        SourceDeletionRecoveryStore recovery = mock(SourceDeletionRecoveryStore.class);
        GridWordsSourceDeletionService service = new GridWordsSourceDeletionService(
                submissions, discord, Clock.fixed(NOW, ZoneOffset.UTC), scheduler, recovery);
        SubmissionStore.StoredSubmission stored = permanentSubmission(SOURCE, RESULT);

        when(submissions.findGridWordsAwaitingOriginalSourceDeletion()).thenReturn(List.of(stored));
        when(submissions.findAwaitingOriginalSourceDeletion(GameType.QUADWORDS)).thenReturn(List.of());
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(stored));
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any())).thenReturn(Optional.of(
                new SubmissionStore.SourceDeletionClaim(TOKEN, NOW.plusSeconds(60))));
        when(discord.deleteSourceMessage(12L, SOURCE)).thenReturn(SourceMessageDeletionGateway.DeletionResult.DELETED);
        when(submissions.recordOriginalSourceDeleted(SOURCE, TOKEN)).thenReturn(true);
        when(submissions.completeOriginalSourceDeletion(SOURCE)).thenReturn(true);

        service.resumeOpenDeletions();

        InOrder order = inOrder(submissions, discord);
        order.verify(submissions).findGridWordsAwaitingOriginalSourceDeletion();
        order.verify(discord).deleteSourceMessage(12L, SOURCE);
        verify(submissions).completeOriginalSourceDeletion(SOURCE);
        verify(recovery).findPermanentlyFailedResultIds();
        verify(recovery, never()).reactivatePermanentFailures(any());
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

    @Test
    void recoveryCannotDeleteAStoredYesterdaySourceAfterDayCloseButCompletedReplayRemainsTerminal() {
        Instant atDayClose = Instant.parse("2026-07-29T04:00:00Z");
        SubmissionStore submissions = mock(SubmissionStore.class);
        SourceMessageDeletionGateway discord = mock(SourceMessageDeletionGateway.class);
        PublicationRetryScheduler scheduler = mock(PublicationRetryScheduler.class);
        SourceDeletionRecoveryStore recovery = mock(SourceDeletionRecoveryStore.class);
        GameResultStore results = mock(GameResultStore.class);
        GridWordsSourceDeletionService service = new GridWordsSourceDeletionService(
                submissions, discord, Clock.fixed(atDayClose, ZoneOffset.UTC), scheduler, recovery, results,
                ZoneId.of("Europe/Berlin"), LocalTime.of(6, 0));
        SubmissionStore.StoredSubmission open = publishedSubmission(SOURCE, RESULT);

        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(open));
        when(results.findById(RESULT)).thenReturn(Optional.of(yesterdayResult(atDayClose)));

        org.assertj.core.api.Assertions.assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isFalse();

        verify(submissions, never()).claimOriginalSourceDeletion(eq(SOURCE), any());
        verifyNoInteractions(discord, recovery, scheduler);
    }

    private static GameResultStore.StoredGameResult yesterdayResult(Instant now) {
        ParsedGameResult parsed = new ParsedGameResult(GameType.QUADWORDS, LocalDate.of(2026, 7, 28),
                new ShareOutcome.Solved(3, 9), Duration.ofSeconds(60), OptionalInt.empty(), Optional.empty());
        return new GameResultStore.StoredGameResult(RESULT, 101L, parsed, "share", "quadwords-share-v2",
                OptionalLong.empty(), now, now);
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
