package de.venomenon.gridwordsbot.application.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
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
    void retryableImageFailureMarksTheSubmissionForReplayWithoutStoringABoardlessResult() {
        AtomicInteger loads = new AtomicInteger();
        Fixture fixture = fixture(attachment -> {
            loads.incrementAndGet();
            throw new AttachmentContentLoader.RetryableAttachmentException("temporary network failure", null);
        });

        assertThat(fixture.service().process(message()))
                .isEqualTo(new ProcessingResult.Ignored());

        assertThat(loads).hasValue(1);
        verify(fixture.submissions()).markRetryableFailure(SOURCE_ID, "attachment download failed");
        verify(fixture.submissions(), never()).storeResult(any());
    }

    @Test
    void unavailableDiscordAttachmentStoresTextOnlyWithoutParserRejection() {
        Fixture fixture = fixture(attachment -> {
            throw new AttachmentContentLoader.AttachmentUnavailableException("gone");
        });

        assertThat(fixture.service().process(message()))
                .isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));

        verify(fixture.submissions(), never()).reject(any());
        verify(fixture.submissions(), never()).markRetryableFailure(any(Long.class), any(String.class));
        assertTextOnlyResult(fixture.submissions());
    }

    private static Fixture fixture(AttachmentContentLoader loader) {
        PlayerStore players = mock(PlayerStore.class);
        SubmissionStore submissions = mock(SubmissionStore.class);
        AtomicReference<SubmissionStore.StoredSubmission> current = new AtomicReference<>(stored(
                SubmissionStore.SubmissionState.RECEIVED, Optional.empty()));
        when(submissions.register(any())).thenAnswer(ignored -> current.get());
        when(submissions.storeResult(any())).thenAnswer(invocation -> {
            current.set(stored(SubmissionStore.SubmissionState.RESULT_STORED, Optional.of(77L)));
            return current.get();
        });
        ProcessSharedResultService service = new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                loader,
                new QuadWordsImageParser(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                players,
                submissions,
                ignored -> true);
        return new Fixture(service, submissions);
    }

    private static void assertTextOnlyResult(SubmissionStore submissions) {
        ArgumentCaptor<SubmissionStore.ResultStorage> stored =
                ArgumentCaptor.forClass(SubmissionStore.ResultStorage.class);
        verify(submissions).storeResult(stored.capture());
        GameResultStore.GameResultUpsert result = stored.getValue().result();
        assertThat(result.parserVersion())
                .isEqualTo(ProcessSharedResultService.QUADWORDS_TEXT_ONLY_PARSER_VERSION);
        assertThat(result.parsedResult().quadWordsBoards()).isEmpty();
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
            Optional<Long> gameResultId) {
        return new SubmissionStore.StoredSubmission(
                SOURCE_ID,
                200L,
                300L,
                PLAYER_ID,
                "QuadWords (29. Juli 2026) 7/9 in 4:05",
                state,
                gameResultId,
                List.of(new SubmissionStore.AttachmentSnapshot(
                        0, "quadwords.png", Optional.of("image/png"), 100L)),
                Optional.empty(),
                Optional.empty(),
                NOW,
                NOW);
    }

    private record Fixture(ProcessSharedResultService service, SubmissionStore submissions) {
    }
}
