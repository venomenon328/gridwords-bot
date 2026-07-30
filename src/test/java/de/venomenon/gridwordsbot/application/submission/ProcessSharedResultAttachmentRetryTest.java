package de.venomenon.gridwordsbot.application.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentReference;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsImageParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import de.venomenon.gridwordsbot.port.out.AttachmentContentLoader;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessSharedResultAttachmentRetryTest {

    private static final long PLAYER_ID = 100L;
    private static final long SOURCE_ID = 500L;
    private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");

    @Test
    void persistsPreResultTechnicalFailureAndStoresTheSameSubmissionOnReplay() {
        PlayerStore playerStore = mock(PlayerStore.class);
        SubmissionStore submissionStore = mock(SubmissionStore.class);
        when(playerStore.findByDiscordUserId(PLAYER_ID)).thenReturn(Optional.of(
                new PlayerStore.StoredPlayer(PLAYER_ID, "Tobias", true, true, NOW, NOW)));

        AtomicReference<SubmissionStore.StoredSubmission> submission = new AtomicReference<>();
        when(submissionStore.register(any())).thenAnswer(invocation -> {
            SubmissionStore.SubmissionRegistration registration = invocation.getArgument(0);
            if (submission.get() == null) {
                submission.set(stored(
                        SubmissionStore.SubmissionState.RECEIVED,
                        Optional.empty(),
                        Optional.empty(),
                        registration.attachments()));
            }
            return submission.get();
        });
        when(submissionStore.transition(any(Long.class), any(), any())).thenAnswer(invocation -> {
            SubmissionStore.SubmissionState expected = invocation.getArgument(1);
            SubmissionStore.SubmissionState target = invocation.getArgument(2);
            if (submission.get().state() != expected) {
                return false;
            }
            submission.set(stored(target, submission.get().gameResultId(),
                    submission.get().technicalErrorMessage(), submission.get().attachments()));
            return true;
        });
        doAnswer(invocation -> {
            submission.set(stored(
                    SubmissionStore.SubmissionState.FAILED_RETRYABLE,
                    Optional.empty(),
                    Optional.of(invocation.getArgument(1)),
                    submission.get().attachments()));
            return null;
        }).when(submissionStore).markRetryableFailure(SOURCE_ID, "attachment download failed");
        when(submissionStore.storeResult(any())).thenAnswer(invocation -> {
            assertThat(submission.get().state()).isEqualTo(SubmissionStore.SubmissionState.VALIDATED);
            submission.set(stored(
                    SubmissionStore.SubmissionState.RESULT_STORED,
                    Optional.of(77L),
                    Optional.empty(),
                    submission.get().attachments()));
            return submission.get();
        });

        AtomicInteger loads = new AtomicInteger();
        AttachmentContentLoader loader = attachment -> {
            if (loads.getAndIncrement() == 0) {
                throw new AttachmentContentLoader.RetryableAttachmentException("temporary network failure", null);
            }
            return new byte[] {1};
        };
        QuadWordsImageParser imageParser = new QuadWordsImageParser() {
            @Override
            public Parse parse(byte[] bytes, de.venomenon.gridwordsbot.domain.model.ShareOutcome outcome) {
                return new Parse.Parsed(boards(7));
            }
        };
        ProcessSharedResultService service = new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                loader,
                imageParser,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                playerStore,
                submissionStore,
                List.of(),
                ignored -> true);
        InboundSharedMessage inbound = message();

        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Ignored());
        assertThat(submission.get().state()).isEqualTo(SubmissionStore.SubmissionState.FAILED_RETRYABLE);
        assertThat(submission.get().gameResultId()).isEmpty();
        assertThat(submission.get().technicalErrorMessage()).contains("attachment download failed");

        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));
        assertThat(submission.get().state()).isEqualTo(SubmissionStore.SubmissionState.RESULT_STORED);
        assertThat(submission.get().gameResultId()).contains(77L);
        assertThat(loads).hasValue(2);

        ArgumentCaptor<SubmissionStore.ResultStorage> storedResult =
                ArgumentCaptor.forClass(SubmissionStore.ResultStorage.class);
        verify(submissionStore).storeResult(storedResult.capture());
        GameResultStore.GameResultUpsert result = storedResult.getValue().result();
        assertThat(result.parserVersion()).isEqualTo(QuadWordsImageParser.VERSION);
        assertThat(result.parsedResult().quadWordsBoards()).contains(boards(7));
    }

    @Test
    void unavailableDiscordSourceRemainsTechnicalAndDoesNotBecomeAParserRejection() {
        PlayerStore playerStore = mock(PlayerStore.class);
        SubmissionStore submissionStore = mock(SubmissionStore.class);
        when(playerStore.findByDiscordUserId(PLAYER_ID)).thenReturn(Optional.of(
                new PlayerStore.StoredPlayer(PLAYER_ID, "Tobias", true, true, NOW, NOW)));
        AtomicReference<SubmissionStore.StoredSubmission> submission = new AtomicReference<>(stored(
                SubmissionStore.SubmissionState.RECEIVED,
                Optional.empty(),
                Optional.empty(),
                List.of(new SubmissionStore.AttachmentSnapshot(0, "quadwords.png", Optional.of("image/png"), 100L))));
        when(submissionStore.register(any())).thenAnswer(ignored -> submission.get());
        when(submissionStore.transition(any(Long.class), any(), any())).thenAnswer(invocation -> {
            SubmissionStore.SubmissionState expected = invocation.getArgument(1);
            SubmissionStore.SubmissionState target = invocation.getArgument(2);
            if (submission.get().state() != expected) {
                return false;
            }
            submission.set(stored(target, Optional.empty(), Optional.empty(), submission.get().attachments()));
            return true;
        });
        doAnswer(invocation -> {
            submission.set(stored(SubmissionStore.SubmissionState.FAILED_RETRYABLE, Optional.empty(),
                    Optional.of("attachment download failed"), submission.get().attachments()));
            return null;
        }).when(submissionStore).markRetryableFailure(SOURCE_ID, "attachment download failed");

        ProcessSharedResultService service = new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                attachment -> { throw new AttachmentContentLoader.AttachmentUnavailableException("gone"); },
                new QuadWordsImageParser(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                playerStore,
                submissionStore,
                List.of(),
                ignored -> true);

        assertThat(service.process(message())).isEqualTo(new ProcessingResult.Ignored());
        assertThat(submission.get().state()).isEqualTo(SubmissionStore.SubmissionState.FAILED_RETRYABLE);
        assertThat(submission.get().parserErrorCode()).isEmpty();
        assertThat(submission.get().gameResultId()).isEmpty();
    }

    @Test
    void doesNotDowngradeAResultStoredByAConcurrentWorkerAfterDownloadFailure() {
        PlayerStore playerStore = mock(PlayerStore.class);
        SubmissionStore submissionStore = mock(SubmissionStore.class);
        when(playerStore.findByDiscordUserId(PLAYER_ID)).thenReturn(Optional.of(
                new PlayerStore.StoredPlayer(PLAYER_ID, "Tobias", true, true, NOW, NOW)));
        when(submissionStore.register(any())).thenReturn(stored(
                SubmissionStore.SubmissionState.RECEIVED,
                Optional.empty(),
                Optional.empty(),
                List.of(new SubmissionStore.AttachmentSnapshot(0, "quadwords.png", Optional.of("image/png"), 100L))));
        when(submissionStore.transition(any(Long.class), any(), any())).thenReturn(false);

        ProcessSharedResultService service = new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                attachment -> { throw new AttachmentContentLoader.RetryableAttachmentException("temporary", null); },
                new QuadWordsImageParser(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                playerStore,
                submissionStore,
                List.of(),
                ignored -> true);

        assertThat(service.process(message())).isEqualTo(new ProcessingResult.Ignored());
        verify(submissionStore, never()).markRetryableFailure(SOURCE_ID, "attachment download failed");
    }

    private static InboundSharedMessage message() {
        return new InboundSharedMessage(
                200L,
                300L,
                SOURCE_ID,
                PLAYER_ID,
                "Tobias",
                "QuadWords (29. Juli 2026) 7/9 in 4:05",
                List.of(new AttachmentMetadata(
                        "quadwords.png",
                        "image/png",
                        100L,
                        Optional.of(new AttachmentReference(300L, SOURCE_ID, 700L)))),
                NOW);
    }

    private static SubmissionStore.StoredSubmission stored(
            SubmissionStore.SubmissionState state,
            Optional<Long> gameResultId,
            Optional<String> technicalError,
            List<SubmissionStore.AttachmentSnapshot> attachments) {
        return new SubmissionStore.StoredSubmission(
                SOURCE_ID,
                200L,
                300L,
                PLAYER_ID,
                "QuadWords (29. Juli 2026) 7/9 in 4:05",
                state,
                gameResultId,
                attachments,
                Optional.empty(),
                technicalError,
                NOW,
                NOW);
    }

    private static QuadWordsBoards boards(int rows) {
        String line = "⬜".repeat(5);
        QuadWordsBoard board = new QuadWordsBoard(Collections.nCopies(rows, line));
        return new QuadWordsBoards(board, board, board, board);
    }
}
