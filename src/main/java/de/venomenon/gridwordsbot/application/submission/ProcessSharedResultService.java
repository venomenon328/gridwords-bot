package de.venomenon.gridwordsbot.application.submission;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.domain.parsing.ParseResult;
import de.venomenon.gridwordsbot.domain.parsing.ShareParseInput;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsImageParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import de.venomenon.gridwordsbot.port.out.AttachmentContentLoader;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.function.LongPredicate;

/** Parses and persists an already filtered shared message without framework-specific types. */
public final class ProcessSharedResultService implements ProcessSharedResultUseCase {
    static final String GRIDWORDS_PARSER_VERSION = "gridwords-share-v1";
    static final String QUADWORDS_PARSER_VERSION = QuadWordsImageParser.VERSION;
    static final String OUTSIDE_ALLOWED_DATE_WINDOW = "OUTSIDE_ALLOWED_DATE_WINDOW";

    private final GridWordsShareParser gridWordsParser;
    private final QuadWordsShareParser quadWordsParser;
    private final AttachmentContentLoader attachmentContentLoader;
    private final QuadWordsImageParser quadWordsImageParser;
    private final Clock clock;
    private final ZoneId timeZone;
    private final PlayerStore playerStore;
    private final SubmissionStore submissionStore;
    private final List<Long> configuredPlayerIds;
    private final LongPredicate canonicalPublisher;

    public ProcessSharedResultService(GridWordsShareParser gridWordsParser, QuadWordsShareParser quadWordsParser,
            Clock clock, ZoneId timeZone, PlayerStore playerStore, SubmissionStore submissionStore) {
        this(gridWordsParser, quadWordsParser, unavailableLoader(), new QuadWordsImageParser(), clock, timeZone,
                playerStore, submissionStore, List.of(), ignored -> true);
    }

    public ProcessSharedResultService(GridWordsShareParser gridWordsParser, QuadWordsShareParser quadWordsParser,
            Clock clock, ZoneId timeZone, PlayerStore playerStore, SubmissionStore submissionStore,
            LongPredicate canonicalPublisher) {
        this(gridWordsParser, quadWordsParser, unavailableLoader(), new QuadWordsImageParser(), clock, timeZone,
                playerStore, submissionStore, List.of(), canonicalPublisher);
    }

    public ProcessSharedResultService(GridWordsShareParser gridWordsParser, QuadWordsShareParser quadWordsParser,
            Clock clock, ZoneId timeZone, PlayerStore playerStore, SubmissionStore submissionStore,
            List<Long> configuredPlayerIds, LongPredicate canonicalPublisher) {
        this(gridWordsParser, quadWordsParser, unavailableLoader(), new QuadWordsImageParser(), clock, timeZone,
                playerStore, submissionStore, configuredPlayerIds, canonicalPublisher);
    }

    public ProcessSharedResultService(GridWordsShareParser gridWordsParser, QuadWordsShareParser quadWordsParser,
            AttachmentContentLoader attachmentContentLoader, QuadWordsImageParser quadWordsImageParser,
            Clock clock, ZoneId timeZone, PlayerStore playerStore, SubmissionStore submissionStore,
            List<Long> configuredPlayerIds, LongPredicate canonicalPublisher) {
        this.gridWordsParser = Objects.requireNonNull(gridWordsParser);
        this.quadWordsParser = Objects.requireNonNull(quadWordsParser);
        this.attachmentContentLoader = Objects.requireNonNull(attachmentContentLoader);
        this.quadWordsImageParser = Objects.requireNonNull(quadWordsImageParser);
        this.clock = Objects.requireNonNull(clock);
        this.timeZone = Objects.requireNonNull(timeZone);
        this.playerStore = Objects.requireNonNull(playerStore);
        this.submissionStore = Objects.requireNonNull(submissionStore);
        this.configuredPlayerIds = List.copyOf(Objects.requireNonNull(configuredPlayerIds));
        if (!this.configuredPlayerIds.isEmpty() && (this.configuredPlayerIds.size() != 2
                || this.configuredPlayerIds.stream().distinct().count() != 2
                || this.configuredPlayerIds.stream().anyMatch(playerId -> playerId <= 0))) {
            throw new IllegalArgumentException("configuredPlayerIds must contain exactly two distinct positive IDs");
        }
        this.canonicalPublisher = Objects.requireNonNull(canonicalPublisher);
    }

    @Override
    public ProcessingResult process(InboundSharedMessage message) {
        ParseResult parseResult = parse(message);
        if (parseResult instanceof ParseResult.NotApplicable
                || playerStore.findByDiscordUserId(message.authorId()).filter(PlayerStore.StoredPlayer::active).isEmpty()) {
            return new ProcessingResult.Ignored();
        }
        SubmissionStore.StoredSubmission submission = submissionStore.register(registration(message));
        if (parseResult instanceof ParseResult.Invalid invalid) {
            return reject(message.messageId(), invalid.errorCode());
        }

        ParsedGameResult parsed = ((ParseResult.Parsed) parseResult).result();
        if (isTerminal(submission)) {
            return new ProcessingResult.Accepted(parsed.gameType());
        }
        if (submission.state() == SubmissionStore.SubmissionState.SUPERSEDED) {
            return new ProcessingResult.Ignored();
        }
        if (submission.state() == SubmissionStore.SubmissionState.PARSE_REJECTED) {
            return new ProcessingResult.Rejected(submission.parserErrorCode().orElse(ParseErrorCode.INVALID_IMAGE_STRUCTURE.name()));
        }
        if (submission.state() != SubmissionStore.SubmissionState.RESULT_STORED
                && submission.state() != SubmissionStore.SubmissionState.FAILED_RETRYABLE
                && !isTodayOrYesterday(parsed.gameDate())) {
            return reject(message.messageId(), OUTSIDE_ALLOWED_DATE_WINDOW);
        }
        if (parsed.gameType() == GameType.QUADWORDS) {
            if (submission.state() == SubmissionStore.SubmissionState.RESULT_STORED) {
                return new ProcessingResult.Accepted(GameType.QUADWORDS);
            }
            QuadWordsCompletion completion = completeQuadWords(message, parsed);
            if (completion instanceof QuadWordsCompletion.Rejected rejected) {
                return rejected.result();
            }
            if (completion instanceof QuadWordsCompletion.RetryableFailure) {
                return new ProcessingResult.Ignored();
            }
            parsed = ((QuadWordsCompletion.Parsed) completion).parsed();
            if (submission.state() == SubmissionStore.SubmissionState.FAILED_RETRYABLE
                    && submission.gameResultId().isEmpty()
                    && !submissionStore.transition(
                            message.messageId(),
                            SubmissionStore.SubmissionState.FAILED_RETRYABLE,
                            SubmissionStore.SubmissionState.VALIDATED)) {
                return new ProcessingResult.Ignored();
            }
        }

        GameResultStore.GameResultUpsert result = new GameResultStore.GameResultUpsert(
                message.authorId(), parsed, message.content(), parserVersion(parsed));
        SubmissionStore.StoredSubmission stored = submissionStore.storeResult(
                new SubmissionStore.ResultStorage(message.messageId(), result, configuredPlayerIds));
        if (stored.state() == SubmissionStore.SubmissionState.SUPERSEDED) {
            return new ProcessingResult.Ignored();
        }
        if (parsed.gameType() == GameType.GRIDWORDS && !canonicalPublisher.test(message.messageId())) {
            return new ProcessingResult.Ignored();
        }
        return new ProcessingResult.Accepted(parsed.gameType());
    }

    private QuadWordsCompletion completeQuadWords(InboundSharedMessage message, ParsedGameResult header) {
        List<AttachmentMetadata> candidates = message.attachments().stream()
                .filter(AttachmentMetadata::isPlausibleImage)
                .toList();
        if (candidates.isEmpty()) {
            return new QuadWordsCompletion.Rejected(reject(message.messageId(), ParseErrorCode.MISSING_IMAGE_ATTACHMENT));
        }
        if (candidates.size() != 1) {
            return new QuadWordsCompletion.Rejected(reject(message.messageId(), ParseErrorCode.AMBIGUOUS_IMAGE_ATTACHMENT));
        }
        try {
            byte[] content = attachmentContentLoader.load(candidates.getFirst());
            QuadWordsImageParser.Parse imageParse = quadWordsImageParser.parse(content, header.outcome());
            if (imageParse instanceof QuadWordsImageParser.Parse.Invalid invalid) {
                return new QuadWordsCompletion.Rejected(reject(message.messageId(), invalid.errorCode()));
            }
            return new QuadWordsCompletion.Parsed(new ParsedGameResult(header.gameType(), header.gameDate(),
                    header.outcome(), header.duration(), header.gridgamesStreak(), header.board(),
                    java.util.Optional.of(((QuadWordsImageParser.Parse.Parsed) imageParse).boards())));
        } catch (AttachmentContentLoader.AttachmentTooLargeException exception) {
            return new QuadWordsCompletion.Rejected(reject(message.messageId(), ParseErrorCode.IMAGE_TOO_LARGE));
        } catch (AttachmentContentLoader.AttachmentUnavailableException
                | AttachmentContentLoader.RetryableAttachmentException exception) {
            markPreResultRetryableFailure(message.messageId(), "attachment download failed");
            return new QuadWordsCompletion.RetryableFailure();
        }
    }

    private void markPreResultRetryableFailure(long sourceMessageId, String safeTechnicalMessage) {
        submissionStore.transition(
                sourceMessageId,
                SubmissionStore.SubmissionState.RECEIVED,
                SubmissionStore.SubmissionState.FAILED_RETRYABLE);
        submissionStore.transition(
                sourceMessageId,
                SubmissionStore.SubmissionState.VALIDATED,
                SubmissionStore.SubmissionState.FAILED_RETRYABLE);
        submissionStore.markRetryableFailure(sourceMessageId, safeTechnicalMessage);
    }

    private ProcessingResult.Rejected reject(long sourceMessageId, ParseErrorCode errorCode) {
        return reject(sourceMessageId, errorCode.name());
    }

    private ProcessingResult.Rejected reject(long sourceMessageId, String errorCode) {
        submissionStore.reject(new SubmissionStore.RejectedSubmission(sourceMessageId, errorCode));
        return new ProcessingResult.Rejected(errorCode);
    }

    private ParseResult parse(InboundSharedMessage message) {
        ShareParseInput input = new ShareParseInput(message.content(), message.attachments());
        ParseResult gridWords = gridWordsParser.parse(input);
        return gridWords instanceof ParseResult.NotApplicable ? quadWordsParser.parse(input) : gridWords;
    }

    private SubmissionStore.SubmissionRegistration registration(InboundSharedMessage message) {
        List<SubmissionStore.AttachmentSnapshot> attachments = java.util.stream.IntStream.range(0, message.attachments().size())
                .mapToObj(index -> snapshot(index, message.attachments().get(index))).toList();
        return new SubmissionStore.SubmissionRegistration(message.messageId(), message.guildId(), message.channelId(),
                message.authorId(), message.content(), attachments, message.receivedAt());
    }

    private SubmissionStore.AttachmentSnapshot snapshot(int index, AttachmentMetadata attachment) {
        return new SubmissionStore.AttachmentSnapshot(index, attachment.filename(), attachment.contentType().isBlank()
                ? java.util.Optional.empty() : java.util.Optional.of(attachment.contentType()), attachment.size());
    }

    private boolean isTodayOrYesterday(LocalDate gameDate) {
        LocalDate today = clock.instant().atZone(timeZone).toLocalDate();
        return gameDate.equals(today) || gameDate.equals(today.minusDays(1));
    }

    private static boolean isTerminal(SubmissionStore.StoredSubmission submission) {
        return submission.state() == SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                || submission.state() == SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED
                || submission.state() == SubmissionStore.SubmissionState.COMPLETED;
    }

    private static String parserVersion(ParsedGameResult parsed) {
        return parsed.gameType() == GameType.GRIDWORDS ? GRIDWORDS_PARSER_VERSION : QUADWORDS_PARSER_VERSION;
    }

    private static AttachmentContentLoader unavailableLoader() {
        return attachment -> { throw new AttachmentContentLoader.RetryableAttachmentException("attachment loader is not configured", null); };
    }

    private sealed interface QuadWordsCompletion permits QuadWordsCompletion.Parsed, QuadWordsCompletion.Rejected,
            QuadWordsCompletion.RetryableFailure {
        record Parsed(ParsedGameResult parsed) implements QuadWordsCompletion { }
        record Rejected(ProcessingResult.Rejected result) implements QuadWordsCompletion { }
        record RetryableFailure() implements QuadWordsCompletion { }
    }
}
