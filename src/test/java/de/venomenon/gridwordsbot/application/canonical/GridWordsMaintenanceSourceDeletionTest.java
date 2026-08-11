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

class GridWordsMaintenanceSourceDeletionTest {

    private static final long SOURCE = 10L;
    private static final long RESULT = 20L;
    private static final long PLAYER = 30L;
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");

    @Test
    void explicitMaintenanceDeletionCanFinishOldParserRepairWithoutWeakeningNormalDeletion() {
        SubmissionStore submissions = mock(SubmissionStore.class);
        SourceMessageDeletionGateway gateway = mock(SourceMessageDeletionGateway.class);
        PublicationRetryScheduler retries = mock(PublicationRetryScheduler.class);
        SourceDeletionRecoveryStore recovery = mock(SourceDeletionRecoveryStore.class);
        GameResultStore results = mock(GameResultStore.class);
        GridWordsSourceDeletionService service = new GridWordsSourceDeletionService(
                submissions,
                gateway,
                Clock.fixed(NOW, ZoneOffset.UTC),
                retries,
                recovery,
                results,
                ZoneId.of("Europe/Berlin"),
                LocalTime.of(6, 0));

        GameResultStore.StoredGameResult result = result();
        SubmissionStore.StoredSubmission submission = new SubmissionStore.StoredSubmission(
                SOURCE,
                11L,
                12L,
                PLAYER,
                result.rawShareText(),
                SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                Optional.of(RESULT),
                List.of(),
                Optional.of("INVALID_DURATION"),
                Optional.empty(),
                NOW,
                NOW);
        SubmissionStore.SourceDeletionClaim claim = new SubmissionStore.SourceDeletionClaim(
                UUID.fromString("00000000-0000-0000-0000-000000000128"),
                NOW.plusSeconds(60));

        when(submissions.findBySourceMessageId(SOURCE)).thenReturn(Optional.of(submission));
        when(results.findById(RESULT)).thenReturn(Optional.of(result));
        when(submissions.claimOriginalSourceDeletion(eq(SOURCE), any())).thenReturn(Optional.of(claim));
        when(gateway.deleteSourceMessage(12L, SOURCE)).thenReturn(SourceMessageDeletionGateway.DeletionResult.DELETED);
        when(submissions.recordOriginalSourceDeleted(SOURCE, claim.token())).thenReturn(true);
        when(submissions.completeOriginalSourceDeletion(SOURCE)).thenReturn(true);

        assertThat(service.deleteAfterCanonicalPublication(SOURCE)).isFalse();
        verify(submissions, never()).claimOriginalSourceDeletion(eq(SOURCE), any());

        assertThat(service.deleteAfterCanonicalPublicationMaintenance(SOURCE)).isTrue();
        verify(gateway).deleteSourceMessage(12L, SOURCE);
        verify(submissions).recordOriginalSourceDeleted(SOURCE, claim.token());
        verify(submissions).completeOriginalSourceDeletion(SOURCE);
    }

    private static GameResultStore.StoredGameResult result() {
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
        return new GameResultStore.StoredGameResult(
                RESULT,
                PLAYER,
                parsed,
                "GridWords (11. August 2026) 3/6 in 7:38:28",
                "gridwords-share-v1",
                OptionalLong.of(99L),
                NOW,
                NOW);
    }
}
