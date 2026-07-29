package de.venomenon.gridwordsbot.application.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceMessageReactionGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionConflictException;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private SourceMessageReactionGateway recoveredReactionGateway;
    private CanonicalGridWordsPublicationService service;
    private GameResultStore.StoredGameResult result;

    @BeforeEach
    void setUp() {
        results = mock(GameResultStore.class);
        players = mock(PlayerStore.class);
        submissions = mock(SubmissionStore.class);
        discord = mock(CanonicalMessageGateway.class);
        retryScheduler = mock(PublicationRetryScheduler.class);
        recoveredReactionGateway = mock(SourceMessageReactionGateway.class);
        result = gridResult(RESULT, TOBIAS, OptionalLong.empty(), 3);
        service = new CanonicalGridWordsPublicationService(
                results,
                players,
                submissions,
                discord,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                List.of(TOBIAS, GEORGIA),
                retryScheduler,
                recoveredReactionGateway);

        when(players.findByDiscordUserId(TOBIAS)).thenReturn(Optional.of(new PlayerStore.StoredPlayer(
                TOBIAS,
                "Tobias",
                true,
                false,
                Instant.EPOCH,
                Instant.EPOCH)));
        when(results.findAll()).thenReturn(List.of(result));
    }

    @Test
    void publishesFirstGridWordsExactlyOnceAndPersistsStateWithTheClaimToken() {
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
    void replayOfPublishedSubmissionDoesNotSendOrEdit() {
        stored(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED);

        assertThat(service.publish(SOURCE)).isTrue();

        verifyNoInteractions(results, players, discord);
        verify(submissions, never()).completeCanonicalPublication(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void replacesMissingCanonicalMessageUnderTheClaim() {
        result = gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 3);
        when(results.findAll()).thenReturn(List.of(result));
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
        when(discord.findByPublicationKey(anyLong(), any()))
                .thenReturn(OptionalLong.empty(), OptionalLong.of(99L));
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
    void retriesAnActiveStartupLeaseAndAddsTheAcceptedReactionAfterRecoveredSuccess() {
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
        verifyNoInteractions(recoveredReactionGateway);

        retry.getValue().run();

        verify(recoveredReactionGateway).addAcceptedReaction(12L, SOURCE);
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


    @Test
    void correctionWithANewSourceMessageEditsTheExistingCanonicalMessage() {
        long correctionSource = 11L;
        result = gridResult(RESULT, TOBIAS, OptionalLong.of(88L), 2);
        when(results.findAll()).thenReturn(List.of(result));
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
    }    private SubmissionStore.StoredSubmission stored(SubmissionStore.SubmissionState state) {
        SubmissionStore.StoredSubmission submission = new SubmissionStore.StoredSubmission(
                SOURCE,
                11L,
                12L,
                TOBIAS,
                "share",
                state,
                Optional.of(RESULT),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Instant.EPOCH,
                Instant.EPOCH);
        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(submission));
        return submission;
    }

    private static GameResultStore.PublicationClaim claim(String token) {
        return new GameResultStore.PublicationClaim(UUID.fromString(token), NOW.plusSeconds(60));
    }

    private static GameResultStore.StoredGameResult gridResult(
            long id,
            long playerId,
            OptionalLong canonicalMessageId,
            int attempts) {
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.GRIDWORDS,
                LocalDate.of(2026, 7, 29),
                new ShareOutcome.Solved(attempts, 6),
                Duration.ofSeconds(85),
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(List.of(
                        "\u2B1C\u2B1C\u2B1C\u2B1C\u2B1C",
                        "\uD83D\uDFE8\uD83D\uDFE8\uD83D\uDFE8\uD83D\uDFE8\uD83D\uDFE8",
                        "\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9\uD83D\uDFE9").subList(0, attempts))));
        return new GameResultStore.StoredGameResult(
                id,
                playerId,
                parsed,
                "share",
                "v1",
                canonicalMessageId,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static GameResultStore.StoredGameResult quadResult(long id, long playerId) {
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.QUADWORDS,
                LocalDate.of(2026, 7, 29),
                new ShareOutcome.Solved(4, 9),
                Duration.ofSeconds(90),
                OptionalInt.empty(),
                Optional.empty());
        return new GameResultStore.StoredGameResult(
                id,
                playerId,
                parsed,
                "quad",
                "v1",
                OptionalLong.empty(),
                Instant.EPOCH,
                Instant.EPOCH);
    }
}