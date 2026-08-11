package de.venomenon.gridwordsbot.application.submission;

import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.domain.parsing.ParseResult;
import de.venomenon.gridwordsbot.domain.parsing.ShareParseInput;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.InvalidDurationRecoveryUseCase;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import de.venomenon.gridwordsbot.port.out.ParserRecoveryStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit, silent maintenance path for the former GridWords H:MM:SS parser bug. */
public final class InvalidDurationRecoveryService implements InvalidDurationRecoveryUseCase {

    private static final ParseErrorCode RECOVERED_ERROR = ParseErrorCode.INVALID_DURATION;

    private final ParserRecoveryStore recoveryStore;
    private final ProcessSharedResultUseCase processor;
    private final GridWordsShareParser gridWordsParser;

    public InvalidDurationRecoveryService(
            ParserRecoveryStore recoveryStore,
            ProcessSharedResultUseCase processor) {
        this(recoveryStore, processor, new GridWordsShareParser());
    }

    InvalidDurationRecoveryService(
            ParserRecoveryStore recoveryStore,
            ProcessSharedResultUseCase processor,
            GridWordsShareParser gridWordsParser) {
        this.recoveryStore = Objects.requireNonNull(recoveryStore);
        this.processor = Objects.requireNonNull(processor);
        this.gridWordsParser = Objects.requireNonNull(gridWordsParser);
    }

    @Override
    public List<Long> findCandidates(long guildId, long channelId) {
        List<Long> recoverable = new ArrayList<>();
        for (ParserRecoveryStore.Candidate candidate
                : recoveryStore.findCandidates(guildId, channelId, RECOVERED_ERROR)) {
            if (isCompletedRecoveryState(candidate.state())) {
                recoveryStore.complete(candidate.sourceMessageId(), RECOVERED_ERROR);
                continue;
            }
            if (isNowParseableGridWords(candidate.rawMessageContent())) {
                recoverable.add(candidate.sourceMessageId());
            }
        }
        return List.copyOf(recoverable);
    }

    @Override
    public boolean recover(InboundSharedMessage message) {
        Objects.requireNonNull(message, "message");
        if (!isNowParseableGridWords(message.content())) {
            return false;
        }
        if (!recoveryStore.prepare(message.messageId(), RECOVERED_ERROR)) {
            return false;
        }

        ProcessingResult result = processor.processMaintenanceRecovery(message, RECOVERED_ERROR);
        if (result instanceof ProcessingResult.Rejected) {
            return false;
        }
        return recoveryStore.complete(message.messageId(), RECOVERED_ERROR);
    }

    private boolean isNowParseableGridWords(String content) {
        return gridWordsParser.parse(new ShareParseInput(content, List.of())) instanceof ParseResult.Parsed;
    }

    private static boolean isCompletedRecoveryState(SubmissionStore.SubmissionState state) {
        return switch (state) {
            case RESULT_STORED, CANONICAL_MESSAGE_PUBLISHED, ORIGINAL_MESSAGE_DELETED, COMPLETED, SUPERSEDED -> true;
            default -> false;
        };
    }
}
