package de.venomenon.gridwordsbot.application.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SubmissionConflictException;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class CanonicalGridWordsPublicationServiceTest {

    private static final long TOBIAS = 1L;
    private static final long GEORGIA = 2L;
    private static final long SOURCE = 10L;
    private static final long RESULT = 20L;
    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");

    private GameResultStore results;
    private PlayerStore players;
    private SubmissionStore submissions;
    private CanonicalMessageGateway discord;
    private PublicationRetryScheduler retryScheduler;
    private CanonicalGridWordsPublicationService service;
    private GameResultStore.StoredGameResult result;

    @BeforeEach
    void setUp() {
        results = mock(GameResultStore.class);
        players = mock(PlayerStore.class);
        submissions = mock(SubmissionStore.class);
        discord = mock(CanonicalMessageGateway.class);
        retryScheduler = mock(PublicationRetryScheduler.class);
        result = gridResult(RESULT, TOBIAS, OptionalLong.empty(), 3);
        service = new CanonicalGridWordsPublicationService(
                results,
                players,
                submissions,
                discord,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                retryScheduler);

        when(players.findByDiscordUserId(TOBIAS)).thenReturn(Optional.of(new PlayerStore.StoredPlayer(
                TOBIAS,
                "Tobias",
                true,
                false,
                Instant.EPOCH,
                Instant.EPOCH)));
        when(results.findAll()).thenReturn(List.of(result));
        when(players.findParticipationPeriods()).thenReturn(List.of(
                new ParticipationPeriod(TOBIAS, LocalDate.MIN, null),
                new ParticipationPeriod(GEORGIA, LocalDate.MIN, null)));
        when(submissions.prepareCanonicalPublication(anyLong(), anyLong()))
                .thenReturn(SubmissionStore.CanonicalPublicationPreparation.PUBLISHABLE);
        when(submissions.beginCanonicalDelivery(anyLong(), anyLong(), any()))
                .thenReturn(new SubmissionStore.CanonicalDeliveryAttempt(1));
        when(discord.findAllByPublicationKey(anyLong(), any())).thenAnswer(invocation -> {
            OptionalLong found = discord.findByPublicationKey(invocation.getArgument(0), invocation.getArgument(1));
            return found.isPresent() ? List.of(found.getAsLong()) : List.of();
        });
    }

    @Test
    void retirementFencePreventsRecoveryFromRecreatingACanonicalMessage() {
        SubmissionStore.StoredSubmission pending = stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(pending));
        service.withRetirementFence(resultId -> false);

        assertThat(service.publish(SOURCE)).isFalse();

        verify(results, never()).findById(RESULT);
        verifyNoInteractions(discord);
    }

    @ParameterizedTest
    @EnumSource(GameType.class)
    void handsOffSuccessfulScheduledPublicationRetryToTheExactSourceDeletionWithoutAnotherEvent(GameType gameType) {
        useResult(resultForGame(gameType, OptionalLong.empty(), 3));
        GridWordsSourceDeletionService deletion = mock(GridWordsSourceDeletionService.class);
        CanonicalGridWordsPublicationService retryingService = new CanonicalGridWordsPublicationService(
                results, players, submissions, discord, Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Berlin"),
                retryScheduler, deletion::deleteAfterCanonicalPublication);
        SubmissionStore.StoredSubmission pending = stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(pending));
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000024");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.empty(), Optional.of(claim));
        when(discord.findByPublicationKey(anyLong(), any())).thenReturn(OptionalLong.empty());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token())).thenReturn(true);
        when(deletion.deleteAfterCanonicalPublication(SOURCE)).thenReturn(true);
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);

        assertThat(retryingService.publish(SOURCE)).isFalse();
        verify(retryScheduler).schedule(eq(NOW.plusSeconds(61)), retry.capture());
        retry.getValue().run();

        verify(discord, times(1)).create(eq(12L), any());
        verify(submissions).completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token());
        verify(deletion).deleteAfterCanonicalPublication(SOURCE);
        verify(submissions, never()).markRetryableFailure(SOURCE, "canonical publication failed");
    }

    @Test
    void deleteFailureAfterSuccessfulScheduledPublicationDoesNotDowngradeCanonicalPublication() {
        GridWordsSourceDeletionService deletion = mock(GridWordsSourceDeletionService.class);
        CanonicalGridWordsPublicationService retryingService = new CanonicalGridWordsPublicationService(
                results, players, submissions, discord, Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Berlin"),
                retryScheduler, deletion::deleteAfterCanonicalPublication);
        SubmissionStore.StoredSubmission pending = stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(pending));
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000025");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.empty(), Optional.of(claim));
        when(discord.findByPublicationKey(anyLong(), any())).thenReturn(OptionalLong.empty());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token())).thenReturn(true);
        when(deletion.deleteAfterCanonicalPublication(SOURCE)).thenReturn(false);
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);

        assertThat(retryingService.publish(SOURCE)).isFalse();
        verify(retryScheduler).schedule(eq(NOW.plusSeconds(61)), retry.capture());
        retry.getValue().run();

        verify(submissions).completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token());
        verify(submissions, never()).markRetryableFailure(SOURCE, "canonical publication failed");
        verify(retryScheduler, times(1)).schedule(any(), any());
        verify(deletion).deleteAfterCanonicalPublication(SOURCE);
    }
    @ParameterizedTest
    @EnumSource(GameType.class)
    void publishesEachGameTypeExactlyOnceAndPersistsStateWithTheClaimToken(GameType gameType) {
        useResult(resultForGame(gameType, OptionalLong.empty(), 3));
        stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000001");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(discord.findByPublicationKey(anyLong(), any())).thenReturn(OptionalLong.empty());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token())).thenReturn(true);

        assertThat(service.publish(SOURCE)).isTrue();

        verify(discord).create(eq(12L), any());
        verify(submissions).completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token());
        verify(results, never()).releaseCanonicalPublicationClaim(RESULT, claim.token());
    }

    @Test
    void reRendersTheKeyedWinnerBeforePersistingPublicationWithoutACanonicalId() {
        stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000022");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        doReturn(List.of(77L), List.of(77L)).when(discord).findAllByPublicationKey(anyLong(), any());
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 77L, claim.token())).thenReturn(true);

        assertThat(service.publish(SOURCE)).isTrue();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(discord, submissions);
        order.verify(discord).edit(eq(12L), eq(77L), any());
        order.verify(submissions).completeCanonicalPublication(SOURCE, RESULT, 77L, claim.token());
        verify(discord, never()).create(anyLong(), any());
    }
    @ParameterizedTest
    @EnumSource(GameType.class)
    void persistsTheDeliveryFenceBeforeCreateAndRemovesRecognizedDuplicateMessages(GameType gameType) {
        useResult(resultForGame(gameType, OptionalLong.empty(), 3));
        stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000021");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        doReturn(List.of(), List.of(99L, 100L)).when(discord).findAllByPublicationKey(anyLong(), any());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token())).thenReturn(true);

        assertThat(service.publish(SOURCE)).isTrue();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(submissions, discord);
        order.verify(submissions).beginCanonicalDelivery(SOURCE, RESULT, claim.token());
        order.verify(discord).create(eq(12L), any());
        verify(discord).delete(12L, 100L);
    }
    @Test
    void replayOfPublishedSubmissionDoesNotSendOrEdit() {
        stored(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED);
        when(results.findById(RESULT)).thenReturn(Optional.of(result));

        assertThat(service.publish(SOURCE)).isTrue();

        verifyNoInteractions(players, discord);
        verify(results).findById(RESULT);
        verify(submissions, never()).completeCanonicalPublication(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void replacesMissingCanonicalMessageUnderTheClaim() {
        result = gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 3);
        when(results.findAll()).thenReturn(List.of(result));
        when(players.findParticipationPeriods()).thenReturn(List.of(
                new ParticipationPeriod(TOBIAS, LocalDate.MIN, null),
                new ParticipationPeriod(GEORGIA, LocalDate.MIN, null)));
        stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000002");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        doThrow(new CanonicalMessageGateway.UnknownMessageException())
                .when(discord)
                .edit(eq(12L), eq(88L), any());
        when(discord.findByPublicationKey(anyLong(), any())).thenReturn(OptionalLong.empty());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token())).thenReturn(true);

        assertThat(service.publish(SOURCE)).isTrue();

        verify(discord).create(eq(12L), any());
        verify(submissions).completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token());
    }

    @Test
    void recordsRetryableFailureAndReleasesOnlyItsOwnClaimWhenPublicationFails() {
        stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000003");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(discord.findByPublicationKey(anyLong(), any())).thenThrow(new IllegalStateException("Discord unavailable"));

        assertThat(service.publish(SOURCE)).isFalse();

        verify(submissions).markRetryableFailure(eq(SOURCE), any());
        verify(results).releaseCanonicalPublicationClaim(RESULT, claim.token());
        verify(submissions, never()).completeCanonicalPublication(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void findsTheEarlierBotMessageAfterSendSucceededButDatabaseCompletionFailed() {
        stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim firstClaim = claim("00000000-0000-0000-0000-000000000004");
        GameResultStore.PublicationClaim retryClaim = claim("00000000-0000-0000-0000-000000000005");
        when(results.claimCanonicalPublication(eq(RESULT), any()))
                .thenReturn(Optional.of(firstClaim), Optional.of(retryClaim));
        doReturn(List.of(), List.of(), List.of(99L), List.of(99L))
                .when(discord).findAllByPublicationKey(anyLong(), any());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, firstClaim.token()))
                .thenThrow(new SubmissionConflictException("database unavailable"));
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, retryClaim.token())).thenReturn(true);

        assertThat(service.publish(SOURCE)).isFalse();
        assertThat(service.publish(SOURCE)).isTrue();

        verify(discord).create(eq(12L), any());
        verify(submissions).markRetryableFailure(eq(SOURCE), any());
        verify(results).releaseCanonicalPublicationClaim(RESULT, firstClaim.token());
        verify(submissions).completeCanonicalPublication(SOURCE, RESULT, 99L, retryClaim.token());
    }

    @Test
    void retriesAnActiveStartupLeaseWithoutAddingASourceReaction() {
        SubmissionStore.StoredSubmission submission = stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(submissions.findGridWordsAwaitingCanonicalPublication()).thenReturn(List.of(submission));
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000006");
        when(results.claimCanonicalPublication(eq(RESULT), any()))
                .thenReturn(Optional.empty(), Optional.of(claim));
        when(discord.findByPublicationKey(anyLong(), any())).thenReturn(OptionalLong.empty());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token())).thenReturn(true);
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);

        service.resumeOpenPublications();

        verify(retryScheduler).schedule(eq(NOW.plusSeconds(61)), retry.capture());

        retry.getValue().run();

        verify(discord).create(eq(12L), any());
    }

    @Test
    void correctionDoesNotReannounceAlreadyEstablishedPersonalOrSharedDayStates() {
        result = gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 2);
        GameResultStore.StoredGameResult personalQuad = quadResult(21L, TOBIAS);
        GameResultStore.StoredGameResult georgiaGrid = gridResult(22L, GEORGIA, OptionalLong.empty(), 3);
        GameResultStore.StoredGameResult georgiaQuad = quadResult(23L, GEORGIA);
        when(results.findAll()).thenReturn(List.of(result, personalQuad, georgiaGrid, georgiaQuad));
        stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000007");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 88L, claim.token())).thenReturn(true);
        ArgumentCaptor<CanonicalResultMessage> message = ArgumentCaptor.forClass(CanonicalResultMessage.class);

        assertThat(service.publish(SOURCE)).isTrue();

        verify(discord).edit(eq(12L), eq(88L), message.capture());
        assertThat(message.getValue().personalComplete()).isEmpty();
        assertThat(message.getValue().personalPerfect()).isEmpty();
        assertThat(message.getValue().sharedComplete()).isEmpty();
        assertThat(message.getValue().sharedPerfect()).isEmpty();
    }


    @ParameterizedTest
    @EnumSource(GameType.class)
    void correctionWithANewSourceMessageEditsTheExistingCanonicalMessage(GameType gameType) {
        useResult(resultForGame(gameType, OptionalLong.of(88L), 2));
        long correctionSource = 11L;
        SubmissionStore.StoredSubmission correction = new SubmissionStore.StoredSubmission(
                correctionSource,
                11L,
                12L,
                TOBIAS,
                "corrected share",
                SubmissionStore.SubmissionState.RESULT_STORED,
                Optional.of(RESULT),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Instant.EPOCH,
                Instant.EPOCH);
        when(submissions.findBySourceMessageId(correctionSource)).thenReturn(Optional.of(correction));
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000008");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(submissions.completeCanonicalPublication(correctionSource, RESULT, 88L, claim.token())).thenReturn(true);

        assertThat(service.publish(correctionSource)).isTrue();

        verify(discord).edit(eq(12L), eq(88L), any());
        verify(discord, never()).create(anyLong(), any());
    }

    @Test
    void delayedFirstPublicationDoesNotTreatALaterCompletionAsItsOwnContext() {
        result = gridResult(RESULT, TOBIAS, OptionalLong.empty(), 3);
        GameResultStore.StoredGameResult personalQuad = quadResult(21L, TOBIAS);
        GameResultStore.StoredGameResult georgiaGrid = gridResult(22L, GEORGIA, OptionalLong.empty(), 3);
        GameResultStore.StoredGameResult georgiaQuad = quadResult(23L, GEORGIA);
        when(results.findAll()).thenReturn(List.of(result, personalQuad, georgiaGrid, georgiaQuad));
        stored(SubmissionStore.SubmissionState.RESULT_STORED, SubmissionStore.PublicationContext.none());
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000009");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(discord.findByPublicationKey(anyLong(), any())).thenReturn(OptionalLong.empty());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token())).thenReturn(true);
        ArgumentCaptor<CanonicalResultMessage> message = ArgumentCaptor.forClass(CanonicalResultMessage.class);

        assertThat(service.publish(SOURCE)).isTrue();

        verify(discord).create(eq(12L), message.capture());
        assertThat(message.getValue().personalComplete()).isEmpty();
        assertThat(message.getValue().personalPerfect()).isEmpty();
        assertThat(message.getValue().sharedComplete()).isEmpty();
        assertThat(message.getValue().sharedPerfect()).isEmpty();
    }

    @Test
    void correctionBeforeFirstPublicationDoesNotReannounceStatesItDidNotEstablish() {
        long correctionSource = 11L;
        result = gridResult(RESULT, TOBIAS, OptionalLong.empty(), 2);
        GameResultStore.StoredGameResult personalQuad = quadResult(21L, TOBIAS);
        GameResultStore.StoredGameResult georgiaGrid = gridResult(22L, GEORGIA, OptionalLong.empty(), 3);
        GameResultStore.StoredGameResult georgiaQuad = quadResult(23L, GEORGIA);
        when(results.findAll()).thenReturn(List.of(result, personalQuad, georgiaGrid, georgiaQuad));
        SubmissionStore.StoredSubmission correction = storedSubmission(
                correctionSource,
                SubmissionStore.SubmissionState.RESULT_STORED,
                SubmissionStore.PublicationContext.none());
        when(submissions.findBySourceMessageId(correctionSource)).thenReturn(Optional.of(correction));
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000010");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(discord.findByPublicationKey(anyLong(), any())).thenReturn(OptionalLong.empty());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(correctionSource, RESULT, 99L, claim.token())).thenReturn(true);
        ArgumentCaptor<CanonicalResultMessage> message = ArgumentCaptor.forClass(CanonicalResultMessage.class);

        assertThat(service.publish(correctionSource)).isTrue();

        verify(discord).create(eq(12L), message.capture());
        assertThat(message.getValue().personalComplete()).isEmpty();
        assertThat(message.getValue().personalPerfect()).isEmpty();
        assertThat(message.getValue().sharedComplete()).isEmpty();
        assertThat(message.getValue().sharedPerfect()).isEmpty();
    }

    @Test
    void omitsZeroValuedContextualPerfectSeriesAfterTheDayStateWasEstablished() {
        result = gridResultForDate(RESULT, TOBIAS, LocalDate.of(2026, 7, 28), OptionalLong.empty(), true, 3);
        GameResultStore.StoredGameResult personalQuad = quadResultForDate(21L, TOBIAS, LocalDate.of(2026, 7, 28), true);
        GameResultStore.StoredGameResult georgiaGrid = gridResultForDate(22L, GEORGIA, LocalDate.of(2026, 7, 28), OptionalLong.empty(), true, 3);
        GameResultStore.StoredGameResult georgiaQuad = quadResultForDate(23L, GEORGIA, LocalDate.of(2026, 7, 28), true);
        GameResultStore.StoredGameResult unsolvedGridToday = gridResultForDate(
                24L, TOBIAS, LocalDate.of(2026, 7, 29), OptionalLong.empty(), false, 6);
        GameResultStore.StoredGameResult solvedQuadToday = quadResultForDate(25L, TOBIAS, LocalDate.of(2026, 7, 29), true);
        when(results.findAll()).thenReturn(List.of(
                result, personalQuad, georgiaGrid, georgiaQuad, unsolvedGridToday, solvedQuadToday));
        stored(SubmissionStore.SubmissionState.RESULT_STORED, new SubmissionStore.PublicationContext(
                true, true, true, true));
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000011");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(discord.findByPublicationKey(anyLong(), any())).thenReturn(OptionalLong.empty());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token())).thenReturn(true);
        ArgumentCaptor<CanonicalResultMessage> message = ArgumentCaptor.forClass(CanonicalResultMessage.class);

        assertThat(service.publish(SOURCE)).isTrue();

        verify(discord).create(eq(12L), message.capture());
        assertThat(message.getValue().personalComplete()).isEqualTo(OptionalInt.of(2));
        assertThat(message.getValue().sharedComplete()).isEqualTo(OptionalInt.of(1));
        assertThat(message.getValue().personalPerfect()).isEmpty();
        assertThat(message.getValue().sharedPerfect()).isEmpty();
    }

    @Test
    void retriesALiveClaimCollisionWithoutAnEarlyAcceptedReaction() {
        long correctionSource = 11L;
        GameResultStore.StoredGameResult publishedResult = gridResult(RESULT, TOBIAS, OptionalLong.of(99L), 3);
        SubmissionStore.StoredSubmission first = stored(SubmissionStore.SubmissionState.RESULT_STORED);
        SubmissionStore.StoredSubmission correction = storedSubmission(
                correctionSource, SubmissionStore.SubmissionState.RESULT_STORED, SubmissionStore.PublicationContext.none());
        when(submissions.findBySourceMessageId(correctionSource)).thenReturn(Optional.of(correction));
        when(results.findById(RESULT)).thenReturn(
                Optional.of(result), Optional.of(result), Optional.of(result), Optional.of(publishedResult));
        GameResultStore.PublicationClaim firstClaim = claim("00000000-0000-0000-0000-000000000012");
        GameResultStore.PublicationClaim retryClaim = claim("00000000-0000-0000-0000-000000000013");
        when(results.claimCanonicalPublication(eq(RESULT), any()))
                .thenReturn(Optional.of(firstClaim), Optional.empty(), Optional.empty(), Optional.of(retryClaim));
        when(discord.findByPublicationKey(anyLong(), any())).thenReturn(OptionalLong.empty());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, firstClaim.token())).thenReturn(true);
        when(submissions.completeCanonicalPublication(correctionSource, RESULT, 99L, retryClaim.token())).thenReturn(true);
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);

        assertThat(service.publish(first.sourceMessageId())).isTrue();
        assertThat(service.publish(correctionSource)).isFalse();
        assertThat(service.publish(correctionSource)).isFalse();

        verify(retryScheduler, times(1)).schedule(eq(NOW.plusSeconds(61)), retry.capture());

        retry.getValue().run();

        verify(discord, times(1)).create(eq(12L), any());
        verify(discord, times(1)).edit(eq(12L), eq(99L), any());
        verify(submissions).completeCanonicalPublication(correctionSource, RESULT, 99L, retryClaim.token());
    }
    @Test
    void doesNotLetAnOlderFailedPublicationOverwriteANewerCorrectionOnRetry() {
        long correctionSource = 11L;
        GameResultStore.StoredGameResult firstResult = gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 4);
        GameResultStore.StoredGameResult correctedResult = gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 2);
        SubmissionStore.StoredSubmission first = storedSubmission(
                SOURCE,
                SubmissionStore.SubmissionState.RESULT_STORED,
                new SubmissionStore.PublicationContext(true, false, false, false));
        SubmissionStore.StoredSubmission superseded = storedSubmission(
                SOURCE,
                SubmissionStore.SubmissionState.SUPERSEDED,
                first.publicationContext());
        SubmissionStore.StoredSubmission correction = storedSubmission(
                correctionSource,
                SubmissionStore.SubmissionState.RESULT_STORED,
                SubmissionStore.PublicationContext.none());
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(first), Optional.of(superseded));
        when(submissions.findBySourceMessageId(correctionSource)).thenReturn(Optional.of(correction));
        when(results.findById(RESULT)).thenReturn(Optional.of(firstResult), Optional.of(correctedResult));
        when(results.findAll()).thenReturn(List.of(firstResult), List.of(correctedResult));
        GameResultStore.PublicationClaim firstClaim = claim("00000000-0000-0000-0000-000000000014");
        GameResultStore.PublicationClaim correctionClaim = claim("00000000-0000-0000-0000-000000000015");
        when(results.claimCanonicalPublication(eq(RESULT), any()))
                .thenReturn(Optional.of(firstClaim), Optional.of(correctionClaim));
        doThrow(new IllegalStateException("Discord unavailable"))
                .doNothing()
                .when(discord)
                .edit(eq(12L), eq(88L), any());
        when(submissions.completeCanonicalPublication(correctionSource, RESULT, 88L, correctionClaim.token()))
                .thenReturn(true);
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<CanonicalResultMessage> messages = ArgumentCaptor.forClass(CanonicalResultMessage.class);

        assertThat(service.publish(SOURCE)).isFalse();
        assertThat(service.publish(correctionSource)).isTrue();
        verify(retryScheduler).schedule(eq(NOW.plusSeconds(61)), retry.capture());

        retry.getValue().run();

        verify(discord, times(2)).edit(eq(12L), eq(88L), messages.capture());
        assertThat(((ShareOutcome.Solved) messages.getAllValues().get(1).outcome()).attemptsUsed()).isEqualTo(2);
        verify(submissions, never()).completeCanonicalPublication(eq(SOURCE), anyLong(), anyLong(), any());
        verify(results, times(2)).claimCanonicalPublication(eq(RESULT), any());
    }

    @Test
    void compensatesAStaleOlderEditAfterANewerCorrectionWasPersisted() throws Exception {
        long correctionSource = 11L;
        SubmissionStore.StoredSubmission older = storedSubmission(
                SOURCE, SubmissionStore.SubmissionState.RESULT_STORED, SubmissionStore.PublicationContext.none());
        SubmissionStore.StoredSubmission newerOpen = storedSubmission(
                correctionSource, SubmissionStore.SubmissionState.RESULT_STORED, SubmissionStore.PublicationContext.none());
        SubmissionStore.StoredSubmission newerPublished = storedSubmission(
                correctionSource,
                SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                SubmissionStore.PublicationContext.none());
        AtomicReference<SubmissionStore.StoredSubmission> olderState = new AtomicReference<>(older);
        AtomicReference<SubmissionStore.StoredSubmission> newerState = new AtomicReference<>(newerOpen);
        AtomicReference<GameResultStore.StoredGameResult> currentResult = new AtomicReference<>(
                gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 4));
        AtomicReference<CanonicalResultMessage> visibleEmbed = new AtomicReference<>();
        AtomicReference<Instant> currentTime = new AtomicReference<>(NOW);
        Clock controlledClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentTime.get();
            }
        };
        CountDownLatch olderEditStarted = new CountDownLatch(1);
        CountDownLatch allowOlderEditToReturn = new CountDownLatch(1);
        List<ScheduledAction> scheduled = new ArrayList<>();
        List<Instant> requestedLeaseEnds = new ArrayList<>();
        service = new CanonicalGridWordsPublicationService(
                results,
                players,
                submissions,
                discord,
                controlledClock,
                ZoneId.of("Europe/Berlin"),
                (at, action) -> scheduled.add(new ScheduledAction(at, action)));
        when(submissions.findBySourceMessageId(anyLong())).thenAnswer(invocation -> {
            long sourceMessageId = invocation.getArgument(0);
            if (sourceMessageId == SOURCE) {
                return Optional.of(olderState.get());
            }
            if (sourceMessageId == correctionSource) {
                return Optional.of(newerState.get());
            }
            return Optional.empty();
        });
        when(submissions.findCurrentCanonicalPublicationCandidate(RESULT)).thenAnswer(
                invocation -> Optional.of(new SubmissionStore.CanonicalRefreshCandidate(newerState.get(), 1)));
        when(submissions.prepareCanonicalPublication(anyLong(), anyLong()))
                .thenReturn(SubmissionStore.CanonicalPublicationPreparation.PUBLISHABLE);
        when(results.findById(RESULT)).thenAnswer(invocation -> Optional.of(currentResult.get()));
        when(results.findAll()).thenAnswer(invocation -> List.of(currentResult.get()));
        AtomicInteger claims = new AtomicInteger();
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenAnswer(invocation -> {
            Instant leaseUntil = invocation.getArgument(1);
            requestedLeaseEnds.add(leaseUntil);
            return Optional.of(new GameResultStore.PublicationClaim(UUID.fromString(switch (claims.incrementAndGet()) {
                case 1 -> "00000000-0000-0000-0000-000000000016";
                case 2 -> "00000000-0000-0000-0000-000000000017";
                case 3 -> "00000000-0000-0000-0000-000000000018";
                default -> throw new AssertionError("unexpected additional claim");
            }), leaseUntil));
        });
        doAnswer(invocation -> {
            CanonicalResultMessage message = invocation.getArgument(2);
            int attempts = ((ShareOutcome.Solved) message.outcome()).attemptsUsed();
            if (attempts == 4) {
                olderEditStarted.countDown();
                if (!allowOlderEditToReturn.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("older edit was not released");
                }
            }
            visibleEmbed.set(message);
            return null;
        }).when(discord).edit(eq(12L), eq(88L), any());
        when(submissions.completeCanonicalPublication(anyLong(), eq(RESULT), eq(88L), any())).thenAnswer(invocation -> {
            long sourceMessageId = invocation.getArgument(0);
            if (sourceMessageId == SOURCE) {
                throw new SubmissionConflictException("older publisher was superseded");
            }
            newerState.set(newerPublished);
            return true;
        });
        when(submissions.completeCanonicalRefresh(correctionSource, RESULT, 88L,
                UUID.fromString("00000000-0000-0000-0000-000000000018"), 1))
                .thenReturn(new SubmissionStore.CanonicalRefreshCompletion(false));

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Boolean> olderPublish = executor.submit(() -> service.publish(SOURCE));
            assertThat(olderEditStarted.await(5, TimeUnit.SECONDS)).isTrue();

            currentTime.set(NOW.plusSeconds(61));
            olderState.set(storedSubmission(
                    SOURCE, SubmissionStore.SubmissionState.SUPERSEDED, SubmissionStore.PublicationContext.none()));
            currentResult.set(gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 2));
            assertThat(service.publish(correctionSource)).isTrue();
            assertThat(requestedLeaseEnds).containsExactly(NOW.plusSeconds(60), NOW.plusSeconds(121));

            allowOlderEditToReturn.countDown();
            assertThat(olderPublish.get(5, TimeUnit.SECONDS)).isFalse();
        }

        assertThat(((ShareOutcome.Solved) visibleEmbed.get().outcome()).attemptsUsed()).isEqualTo(4);
        assertThat(scheduled).hasSize(2);
        ScheduledAction compensation = scheduled.stream()
                .filter(action -> action.at().equals(NOW.plusSeconds(62)))
                .findFirst()
                .orElseThrow();
        compensation.action().run();

        assertThat(((ShareOutcome.Solved) visibleEmbed.get().outcome()).attemptsUsed()).isEqualTo(2);
        assertThat(requestedLeaseEnds).containsExactly(NOW.plusSeconds(60), NOW.plusSeconds(121), NOW.plusSeconds(121));
        verify(discord, times(3)).edit(eq(12L), eq(88L), any());
        verify(submissions).completeCanonicalRefresh(
                correctionSource, RESULT, 88L, UUID.fromString("00000000-0000-0000-0000-000000000018"), 1);
        verifyNoInteractions(retryScheduler);
    }
    @Test
    void rerunsARefreshWhenAnotherStaleEditArrivesDuringTheCurrentRefresh() throws Exception {
        long correctionSource = 11L;
        SubmissionStore.StoredSubmission older = storedSubmission(
                SOURCE, SubmissionStore.SubmissionState.RESULT_STORED, SubmissionStore.PublicationContext.none());
        SubmissionStore.StoredSubmission current = storedSubmission(
                correctionSource,
                SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                SubmissionStore.PublicationContext.none());
        SubmissionStore.CanonicalRefreshCandidate refresh = new SubmissionStore.CanonicalRefreshCandidate(current, 1);
        AtomicReference<GameResultStore.StoredGameResult> currentResult = new AtomicReference<>(
                gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 2));
        AtomicReference<CanonicalResultMessage> visibleEmbed = new AtomicReference<>();
        AtomicReference<Instant> currentTime = new AtomicReference<>(NOW);
        Clock controlledClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentTime.get();
            }
        };
        CountDownLatch firstRefreshEditStarted = new CountDownLatch(1);
        CountDownLatch allowFirstRefreshToComplete = new CountDownLatch(1);
        AtomicInteger currentEdits = new AtomicInteger();
        List<ScheduledAction> scheduled = new ArrayList<>();
        service = new CanonicalGridWordsPublicationService(
                results,
                players,
                submissions,
                discord,
                controlledClock,
                ZoneId.of("Europe/Berlin"),
                (at, action) -> scheduled.add(new ScheduledAction(at, action)));
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(older));
        when(submissions.findCurrentCanonicalPublicationCandidate(RESULT)).thenReturn(Optional.of(refresh));
        when(submissions.prepareCanonicalPublication(SOURCE, RESULT))
                .thenReturn(SubmissionStore.CanonicalPublicationPreparation.PUBLISHABLE);
        when(results.findById(RESULT)).thenAnswer(invocation -> Optional.of(currentResult.get()));
        when(results.findAll()).thenAnswer(invocation -> List.of(currentResult.get()));
        AtomicInteger claims = new AtomicInteger();
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenAnswer(invocation -> Optional.of(
                new GameResultStore.PublicationClaim(UUID.fromString(switch (claims.incrementAndGet()) {
                    case 1 -> "00000000-0000-0000-0000-000000000020";
                    case 2 -> "00000000-0000-0000-0000-000000000021";
                    case 3 -> "00000000-0000-0000-0000-000000000022";
                    case 4 -> "00000000-0000-0000-0000-000000000023";
                    default -> throw new AssertionError("unexpected additional claim");
                }), invocation.getArgument(1))));
        doAnswer(invocation -> {
            CanonicalResultMessage message = invocation.getArgument(2);
            int attempts = ((ShareOutcome.Solved) message.outcome()).attemptsUsed();
            visibleEmbed.set(message);
            if (attempts == 2 && currentEdits.getAndIncrement() == 0) {
                firstRefreshEditStarted.countDown();
                if (!allowFirstRefreshToComplete.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("first refresh was not released");
                }
            }
            return null;
        }).when(discord).edit(eq(12L), eq(88L), any());
        when(submissions.completeCanonicalPublication(eq(SOURCE), eq(RESULT), eq(88L), any()))
                .thenThrow(new SubmissionConflictException("stale publisher completion was fenced"));
        when(submissions.completeCanonicalRefresh(correctionSource, RESULT, 88L,
                UUID.fromString("00000000-0000-0000-0000-000000000021"), 1))
                .thenReturn(new SubmissionStore.CanonicalRefreshCompletion(false));
        when(submissions.completeCanonicalRefresh(correctionSource, RESULT, 88L,
                UUID.fromString("00000000-0000-0000-0000-000000000023"), 1))
                .thenReturn(new SubmissionStore.CanonicalRefreshCompletion(false));

        currentResult.set(gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 4));
        assertThat(service.publish(SOURCE)).isFalse();
        currentResult.set(gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 2));
        ScheduledAction firstRefresh = scheduled.stream()
                .filter(action -> action.at().equals(NOW.plusSeconds(1)))
                .findFirst()
                .orElseThrow();

        currentTime.set(NOW.plusSeconds(1));
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> runningRefresh = executor.submit(firstRefresh.action());
            assertThat(firstRefreshEditStarted.await(5, TimeUnit.SECONDS)).isTrue();

            currentResult.set(gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 4));
            assertThat(service.publish(SOURCE)).isFalse();
            assertThat(((ShareOutcome.Solved) visibleEmbed.get().outcome()).attemptsUsed()).isEqualTo(4);
            currentResult.set(gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 2));

            allowFirstRefreshToComplete.countDown();
            runningRefresh.get(5, TimeUnit.SECONDS);
        }

        ScheduledAction rerun = scheduled.stream()
                .filter(action -> action.at().equals(NOW.plusSeconds(2)))
                .findFirst()
                .orElseThrow();
        rerun.action().run();

        assertThat(((ShareOutcome.Solved) visibleEmbed.get().outcome()).attemptsUsed()).isEqualTo(2);
        verify(submissions, times(2)).requestCanonicalRefresh(RESULT);
        verify(discord, times(4)).edit(eq(12L), eq(88L), any());
        verify(submissions).completeCanonicalRefresh(correctionSource, RESULT, 88L,
                UUID.fromString("00000000-0000-0000-0000-000000000023"), 1);
    }
    @Test
    void startupReconcilesAPersistedCanonicalRefreshWithoutAcknowledgingASupersededSource() {
        SubmissionStore.StoredSubmission current = storedSubmission(
                SOURCE,
                SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                SubmissionStore.PublicationContext.none());
        SubmissionStore.CanonicalRefreshCandidate refresh =
                new SubmissionStore.CanonicalRefreshCandidate(current, 7);
        result = gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 2);
        when(results.findAll()).thenReturn(List.of(result));
        when(players.findParticipationPeriods()).thenReturn(List.of(
                new ParticipationPeriod(TOBIAS, LocalDate.MIN, null),
                new ParticipationPeriod(GEORGIA, LocalDate.MIN, null)));
        when(submissions.findGridWordsAwaitingCanonicalPublication()).thenReturn(List.of());
        when(submissions.findCanonicalRefreshCandidates()).thenReturn(List.of(refresh));
        when(submissions.findCurrentCanonicalPublicationCandidate(RESULT)).thenReturn(Optional.of(refresh));
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000019");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(submissions.completeCanonicalRefresh(SOURCE, RESULT, 88L, claim.token(), 1))
                .thenReturn(new SubmissionStore.CanonicalRefreshCompletion(false));

        service.resumeOpenPublications();

        verify(discord).edit(eq(12L), eq(88L), any());
        verify(submissions).completeCanonicalRefresh(SOURCE, RESULT, 88L, claim.token(), 1);
    }
    @Test
    void startupRecoverySkipsASubmissionSupersededAfterTheRecoveryScan() {
        SubmissionStore.StoredSubmission recoverySnapshot = storedSubmission(
                SOURCE, SubmissionStore.SubmissionState.RESULT_STORED, SubmissionStore.PublicationContext.none());
        SubmissionStore.StoredSubmission superseded = storedSubmission(
                SOURCE, SubmissionStore.SubmissionState.SUPERSEDED, SubmissionStore.PublicationContext.none());
        when(submissions.findGridWordsAwaitingCanonicalPublication()).thenReturn(List.of(recoverySnapshot));
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(superseded));

        service.resumeOpenPublications();

        verifyNoInteractions(results, players, discord, retryScheduler);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void boardlessLegacyQuadWordsIsNotPublishedOrHandedToDeletionEvenWithCanonicalId(boolean hasCanonicalId) {
        GameResultStore.StoredGameResult legacy = legacyQuadResult(
                RESULT, TOBIAS, hasCanonicalId ? OptionalLong.of(88L) : OptionalLong.empty());
        useResult(legacy);
        stored(SubmissionStore.SubmissionState.RESULT_STORED);
        java.util.function.LongConsumer handoff = mock(java.util.function.LongConsumer.class);
        service = new CanonicalGridWordsPublicationService(
                results, players, submissions, discord, Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Berlin"),
                retryScheduler, handoff);

        assertThat(service.publish(SOURCE)).isFalse();

        verifyNoInteractions(discord, retryScheduler, handoff);
        verify(results, never()).claimCanonicalPublication(anyLong(), any());
        verify(submissions, never()).prepareCanonicalPublication(anyLong(), anyLong());
    }

    @Test
    void boardlessLegacyRefreshCompletesWithoutDiscordOrRetryHotLoop() {
        GameResultStore.StoredGameResult legacy = legacyQuadResult(RESULT, TOBIAS, OptionalLong.of(88L));
        SubmissionStore.StoredSubmission published = stored(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED);
        SubmissionStore.CanonicalRefreshCandidate refresh = new SubmissionStore.CanonicalRefreshCandidate(published, 7);
        when(submissions.findCanonicalRefreshCandidates()).thenReturn(List.of(refresh));
        when(submissions.findCurrentCanonicalPublicationCandidate(RESULT)).thenReturn(Optional.of(refresh));
        when(results.findById(RESULT)).thenReturn(Optional.of(legacy));

        service.resumeOpenPublications();

        verifyNoInteractions(discord, retryScheduler);
        verify(results, never()).claimCanonicalPublication(anyLong(), any());
    }

    @Test
    void startupRecoversOpenImageBackedQuadWordsSubmissionThroughTheDeliveryFence() {
        useResult(imageQuadResult(RESULT, TOBIAS, OptionalLong.empty(), 4));
        SubmissionStore.StoredSubmission open = stored(SubmissionStore.SubmissionState.RESULT_STORED);
        when(submissions.findAwaitingCanonicalPublication(GameType.QUADWORDS)).thenReturn(List.of(open));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000030");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(discord.findByPublicationKey(anyLong(), any())).thenReturn(OptionalLong.empty());
        when(discord.create(anyLong(), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token())).thenReturn(true);

        service.resumeOpenPublications();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(submissions, discord);
        order.verify(submissions).beginCanonicalDelivery(SOURCE, RESULT, claim.token());
        order.verify(discord).create(eq(12L), any());
        order.verify(submissions).completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token());
    }

    @Test
    void imageBackedQuadWordsUsesItsEstablishedContext() {
        useResult(imageQuadResult(RESULT, TOBIAS, OptionalLong.of(88L), 3));
        SubmissionStore.PublicationContext established = new SubmissionStore.PublicationContext(true, true, true, true);
        stored(SubmissionStore.SubmissionState.RESULT_STORED, established);
        GameResultStore.StoredGameResult tobiasGrid = gridResult(21L, TOBIAS, OptionalLong.empty(), 3);
        GameResultStore.StoredGameResult georgiaGrid = gridResult(22L, GEORGIA, OptionalLong.empty(), 3);
        GameResultStore.StoredGameResult georgiaQuad = imageQuadResult(23L, GEORGIA, OptionalLong.empty(), 3);
        when(results.findAll()).thenReturn(List.of(result, tobiasGrid, georgiaGrid, georgiaQuad));
        GameResultStore.PublicationClaim claim = claim("00000000-0000-0000-0000-000000000031");
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 88L, claim.token())).thenReturn(true);
        ArgumentCaptor<CanonicalResultMessage> message = ArgumentCaptor.forClass(CanonicalResultMessage.class);

        assertThat(service.publish(SOURCE)).isTrue();

        verify(discord).edit(eq(12L), eq(88L), message.capture());
        assertThat(message.getValue().personalComplete()).hasValue(1);
        assertThat(message.getValue().personalPerfect()).hasValue(1);
        assertThat(message.getValue().sharedComplete()).hasValue(1);
        assertThat(message.getValue().sharedPerfect()).hasValue(1);
    }

    private SubmissionStore.StoredSubmission stored(SubmissionStore.SubmissionState state) {
        return stored(state, SubmissionStore.PublicationContext.none());
    }

    private SubmissionStore.StoredSubmission stored(
            SubmissionStore.SubmissionState state,
            SubmissionStore.PublicationContext publicationContext) {
        SubmissionStore.StoredSubmission submission = storedSubmission(SOURCE, state, publicationContext);
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(submission));
        return submission;
    }

    private static SubmissionStore.StoredSubmission storedSubmission(
            long sourceMessageId,
            SubmissionStore.SubmissionState state,
            SubmissionStore.PublicationContext publicationContext) {
        return new SubmissionStore.StoredSubmission(
                sourceMessageId,
                11L,
                12L,
                TOBIAS,
                "share",
                state,
                Optional.of(RESULT),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                publicationContext,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private record ScheduledAction(Instant at, Runnable action) {
    }

    private void useResult(GameResultStore.StoredGameResult selected) {
        result = selected;
        when(results.findById(RESULT)).thenReturn(Optional.of(selected));
        when(results.findAll()).thenReturn(List.of(selected));
    }

    private static GameResultStore.StoredGameResult resultForGame(
            GameType gameType, OptionalLong canonicalMessageId, int attempts) {
        return gameType == GameType.GRIDWORDS
                ? gridResult(RESULT, TOBIAS, canonicalMessageId, attempts)
                : imageQuadResult(RESULT, TOBIAS, canonicalMessageId, attempts);
    }

    private static GameResultStore.StoredGameResult imageQuadResult(
            long id, long playerId, OptionalLong canonicalMessageId, int attempts) {
        String row = "\u2B1C\uD83D\uDFE8\uD83D\uDFE9\u2B1C\uD83D\uDFE8";
        QuadWordsBoard board = new QuadWordsBoard(java.util.Collections.nCopies(attempts, row));
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.QUADWORDS, LocalDate.of(2026, 7, 29), new ShareOutcome.Solved(attempts, 9),
                Duration.ofSeconds(90), OptionalInt.empty(), Optional.empty(),
                Optional.of(new QuadWordsBoards(board, board, board, board)));
        return new GameResultStore.StoredGameResult(
                id, playerId, parsed, "quad", "quadwords-image-v2", canonicalMessageId, Instant.EPOCH, Instant.EPOCH);
    }

    private static GameResultStore.StoredGameResult legacyQuadResult(
            long id, long playerId, OptionalLong canonicalMessageId) {
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.QUADWORDS, LocalDate.of(2026, 7, 29), new ShareOutcome.Solved(4, 9),
                Duration.ofSeconds(90), OptionalInt.empty(), Optional.empty());
        return new GameResultStore.StoredGameResult(
                id, playerId, parsed, "quad", "quadwords-share-v1", canonicalMessageId, Instant.EPOCH, Instant.EPOCH);
    }

    private static GameResultStore.PublicationClaim claim(String token) {
        return new GameResultStore.PublicationClaim(UUID.fromString(token), NOW.plusSeconds(60));
    }

    private static GameResultStore.StoredGameResult gridResult(
            long id,
            long playerId,
            OptionalLong canonicalMessageId,
            int attempts) {
        return gridResultForDate(id, playerId, LocalDate.of(2026, 7, 29), canonicalMessageId, true, attempts);
    }

    private static GameResultStore.StoredGameResult gridResultForDate(
            long id,
            long playerId,
            LocalDate gameDate,
            OptionalLong canonicalMessageId,
            boolean solved,
            int attempts) {
        List<String> rows = List.of(
                "\u2B1C\u2B1C\u2B1C\u2B1C\u2B1C",
                "\uD83D\uDFE8\uD83D\uDFE8\uD83D\uDFE8\uD83D\uDFE8\uD83D\uDFE8",
                "\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9",
                "\u2B1C\u2B1C\u2B1C\u2B1C\u2B1C",
                "\uD83D\uDFE8\uD83D\uDFE8\uD83D\uDFE8\uD83D\uDFE8\uD83D\uDFE8",
                "\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9");
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.GRIDWORDS,
                gameDate,
                solved ? new ShareOutcome.Solved(attempts, 6) : new ShareOutcome.Unsolved(6),
                Duration.ofSeconds(85),
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(rows.subList(0, solved ? attempts : 6))));
        return new GameResultStore.StoredGameResult(
                id, playerId, parsed, "share", "v1", canonicalMessageId, Instant.EPOCH, Instant.EPOCH);
    }

    private static GameResultStore.StoredGameResult quadResult(long id, long playerId) {
        return quadResultForDate(id, playerId, LocalDate.of(2026, 7, 29), true);
    }

    private static GameResultStore.StoredGameResult quadResultForDate(
            long id, long playerId, LocalDate gameDate, boolean solved) {
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.QUADWORDS,
                gameDate,
                solved ? new ShareOutcome.Solved(4, 9) : new ShareOutcome.Unsolved(9),
                Duration.ofSeconds(90),
                OptionalInt.empty(),
                Optional.empty());
        return new GameResultStore.StoredGameResult(
                id, playerId, parsed, "quad", "v1", OptionalLong.empty(), Instant.EPOCH, Instant.EPOCH);
    }
}
