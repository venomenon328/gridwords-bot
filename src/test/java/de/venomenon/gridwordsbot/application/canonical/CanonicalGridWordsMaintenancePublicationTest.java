package de.venomenon.gridwordsbot.application.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
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
import org.junit.jupiter.api.Test;

class CanonicalGridWordsMaintenancePublicationTest {

    private static final long SOURCE = 10L;
    private static final long RESULT = 20L;
    private static final long PLAYER = 30L;
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");

    @Test
    void explicitMaintenancePublicationCanFinishOldParserRepairWithoutWeakeningNormalPublish() {
        GameResultStore results = mock(GameResultStore.class);
        PlayerStore players = mock(PlayerStore.class);
        SubmissionStore submissions = mock(SubmissionStore.class);
        CanonicalMessageGateway discord = mock(CanonicalMessageGateway.class);
        PublicationRetryScheduler retries = mock(PublicationRetryScheduler.class);
        CanonicalGridWordsPublicationService service = new CanonicalGridWordsPublicationService(
                results,
                players,
                submissions,
                discord,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                retries);

        ParsedGameResult parsed = new ParsedGameResult(
                GameType.GRIDWORDS,
                LocalDate.of(2026, 8, 11),
                new ShareOutcome.Solved(3, 6),
                Duration.ofHours(7).plusMinutes(38).plusSeconds(28),
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(List.of(
                        "⬜⬜⬜⬜⬜",
                        "🟨⬜⬜⬜⬜",
                        "🟩🟩🟩🟩🟩"))));
        GameResultStore.StoredGameResult result = new GameResultStore.StoredGameResult(
                RESULT,
                PLAYER,
                parsed,
                "GridWords (11. August 2026) 3/6 in 7:38:28",
                "gridwords-share-v1",
                OptionalLong.empty(),
                NOW,
                NOW);
        SubmissionStore.StoredSubmission submission = new SubmissionStore.StoredSubmission(
                SOURCE,
                11L,
                12L,
                PLAYER,
                result.rawShareText(),
                SubmissionStore.SubmissionState.RESULT_STORED,
                Optional.of(RESULT),
                List.of(),
                Optional.of("INVALID_DURATION"),
                Optional.empty(),
                NOW,
                NOW);
        GameResultStore.PublicationClaim claim = new GameResultStore.PublicationClaim(
                UUID.fromString("00000000-0000-0000-0000-000000000127"),
                NOW.plusSeconds(60));

        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(submission));
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        when(results.findAll()).thenReturn(List.of(result));
        when(players.findGameParticipationPeriods()).thenReturn(List.of());
        when(players.findByDiscordUserId(PLAYER)).thenReturn(Optional.of(new PlayerStore.StoredPlayer(
                PLAYER, "Player", true, false, Instant.EPOCH, Instant.EPOCH)));
        when(submissions.prepareCanonicalPublication(SOURCE, RESULT))
                .thenReturn(SubmissionStore.CanonicalPublicationPreparation.PUBLISHABLE);
        when(results.claimCanonicalPublication(eq(RESULT), any())).thenReturn(Optional.of(claim));
        when(submissions.beginCanonicalDelivery(SOURCE, RESULT, claim.token()))
                .thenReturn(new SubmissionStore.CanonicalDeliveryAttempt(1));
        when(discord.create(eq(12L), any())).thenReturn(99L);
        when(submissions.completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token())).thenReturn(true);

        assertThat(service.publish(SOURCE)).isFalse();
        verify(results, never()).claimCanonicalPublication(eq(RESULT), any());

        assertThat(service.publishMaintenanceRecovery(SOURCE)).isTrue();
        verify(discord).create(eq(12L), any());
        verify(submissions).completeCanonicalPublication(SOURCE, RESULT, 99L, claim.token());
    }
}
