package de.venomenon.gridwordsbot.application.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.parser.FixtureSupport;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import de.venomenon.gridwordsbot.port.out.ParserRecoveryStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvalidDurationRecoveryServiceTest {

    private static final String VALID = FixtureSupport.read(
            "gridwords/unsolved/synthetic-unsolved-long-duration.txt");

    @Test
    void exposesOnlySharesThatCurrentGridWordsParserCanRepairAndCleansFinishedMarkers() {
        ParserRecoveryStore store = mock(ParserRecoveryStore.class);
        ProcessSharedResultUseCase processor = mock(ProcessSharedResultUseCase.class);
        when(store.findCandidates(11L, 12L, ParseErrorCode.INVALID_DURATION)).thenReturn(List.of(
                new ParserRecoveryStore.Candidate(
                        101L, VALID, SubmissionStore.SubmissionState.PARSE_REJECTED),
                new ParserRecoveryStore.Candidate(
                        102L, VALID.replace("7:38:28", "1:99"),
                        SubmissionStore.SubmissionState.PARSE_REJECTED),
                new ParserRecoveryStore.Candidate(
                        103L, VALID, SubmissionStore.SubmissionState.RESULT_STORED)));

        InvalidDurationRecoveryService service = new InvalidDurationRecoveryService(store, processor);

        assertThat(service.findCandidates(11L, 12L)).containsExactly(101L);
        verify(store).complete(103L, ParseErrorCode.INVALID_DURATION);
        verify(store, never()).prepare(102L, ParseErrorCode.INVALID_DURATION);
    }

    @Test
    void preparesProcessesAndCompletesRepair() {
        ParserRecoveryStore store = mock(ParserRecoveryStore.class);
        ProcessSharedResultUseCase processor = mock(ProcessSharedResultUseCase.class);
        InboundSharedMessage message = message(201L, VALID);
        when(store.prepare(201L, ParseErrorCode.INVALID_DURATION)).thenReturn(true);
        when(processor.processMaintenanceRecovery(message, ParseErrorCode.INVALID_DURATION))
                .thenReturn(new ProcessingResult.Accepted());
        when(store.complete(201L, ParseErrorCode.INVALID_DURATION)).thenReturn(true);

        InvalidDurationRecoveryService service = new InvalidDurationRecoveryService(store, processor);

        assertThat(service.recover(message)).isTrue();
        verify(store).prepare(201L, ParseErrorCode.INVALID_DURATION);
        verify(processor).processMaintenanceRecovery(message, ParseErrorCode.INVALID_DURATION);
        verify(store).complete(201L, ParseErrorCode.INVALID_DURATION);
    }

    @Test
    void leavesMarkerForRestartWhenProcessingIsStillRejected() {
        ParserRecoveryStore store = mock(ParserRecoveryStore.class);
        ProcessSharedResultUseCase processor = mock(ProcessSharedResultUseCase.class);
        InboundSharedMessage message = message(202L, VALID);
        when(store.prepare(202L, ParseErrorCode.INVALID_DURATION)).thenReturn(true);
        when(processor.processMaintenanceRecovery(message, ParseErrorCode.INVALID_DURATION))
                .thenReturn(new ProcessingResult.Rejected(ParseErrorCode.INVALID_DURATION.name()));

        InvalidDurationRecoveryService service = new InvalidDurationRecoveryService(store, processor);

        assertThat(service.recover(message)).isFalse();
        verify(store, never()).complete(202L, ParseErrorCode.INVALID_DURATION);
    }

    private static InboundSharedMessage message(long messageId, String content) {
        return new InboundSharedMessage(
                11L, 12L, messageId, 101L, "Player", content, List.of(),
                Instant.parse("2026-08-11T19:36:00Z"));
    }
}
