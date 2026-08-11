package de.venomenon.gridwordsbot.application.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.parser.FixtureSupport;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessSharedResultMaintenanceRecoveryTest {

    private static final long MESSAGE_ID = 12700L;
    private static final long PLAYER_ID = 101L;
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-11T19:36:00Z");
    private static final String CONTENT = FixtureSupport.read(
            "gridwords/unsolved/synthetic-unsolved-long-duration.txt");

    @Test
    void maintenanceRecoveryUsesPreparedMarkerAndCanRepairHistoricalShare() {
        SubmissionStore submissions = mock(SubmissionStore.class);
        PlayerStore players = mock(PlayerStore.class);
        SubmissionStore.StoredSubmission prepared = submission(
                SubmissionStore.SubmissionState.RECEIVED,
                Optional.empty(),
                Optional.of(ParseErrorCode.INVALID_DURATION.name()));
        SubmissionStore.StoredSubmission stored = submission(
                SubmissionStore.SubmissionState.RESULT_STORED,
                Optional.of(77L),
                Optional.of(ParseErrorCode.INVALID_DURATION.name()));
        when(submissions.findBySourceMessageId(MESSAGE_ID)).thenReturn(Optional.of(prepared));
        when(submissions.register(any())).thenReturn(prepared);
        when(submissions.storeResult(any())).thenReturn(stored);
        when(submissions.consumeResultStorageOutcome(stored)).thenReturn(
                new SubmissionStore.ResultStorageOutcome(
                        stored, SubmissionStore.ResultStorageKind.PREVIOUSLY_STORED));

        ProcessSharedResultService service = new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                players,
                submissions,
                ignored -> true);
        InboundSharedMessage message = message();

        assertThat(service.process(message)).isEqualTo(new ProcessingResult.Rejected(
                ProcessSharedResultService.OUTSIDE_ALLOWED_DATE_WINDOW));
        assertThat(service.processMaintenanceRecovery(message, ParseErrorCode.INVALID_DURATION))
                .isEqualTo(new ProcessingResult.Accepted());

        ArgumentCaptor<SubmissionStore.ResultStorage> storage = ArgumentCaptor.forClass(
                SubmissionStore.ResultStorage.class);
        verify(submissions).storeResult(storage.capture());
        assertThat(storage.getValue().result().parsedResult().duration())
                .isEqualTo(Duration.ofHours(7).plusMinutes(38).plusSeconds(28));
        assertThat(storage.getValue().result().parserVersion())
                .isEqualTo(ProcessSharedResultService.GRIDWORDS_PARSER_VERSION);
    }

    @Test
    void maintenanceRecoveryRefusesUnpreparedSubmission() {
        SubmissionStore submissions = mock(SubmissionStore.class);
        PlayerStore players = mock(PlayerStore.class);
        SubmissionStore.StoredSubmission unprepared = submission(
                SubmissionStore.SubmissionState.RECEIVED, Optional.empty(), Optional.empty());
        when(submissions.findBySourceMessageId(MESSAGE_ID)).thenReturn(Optional.of(unprepared));

        ProcessSharedResultService service = new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                players,
                submissions,
                ignored -> true);

        assertThatThrownBy(() -> service.processMaintenanceRecovery(
                message(), ParseErrorCode.INVALID_DURATION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prepared parser rejection");
    }

    private static InboundSharedMessage message() {
        return new InboundSharedMessage(
                11L, 12L, MESSAGE_ID, PLAYER_ID, "Player", CONTENT, List.of(), RECEIVED_AT);
    }

    private static SubmissionStore.StoredSubmission submission(
            SubmissionStore.SubmissionState state,
            Optional<Long> resultId,
            Optional<String> errorCode) {
        return new SubmissionStore.StoredSubmission(
                MESSAGE_ID,
                11L,
                12L,
                PLAYER_ID,
                CONTENT,
                state,
                resultId,
                List.of(),
                errorCode,
                Optional.empty(),
                RECEIVED_AT,
                RECEIVED_AT);
    }
}
