package de.venomenon.gridwordsbot.application.submission;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.GameDateAdmissionPolicy;
import de.venomenon.gridwordsbot.domain.model.GameParticipationSelection;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses and persists an already filtered shared message without framework-specific types. */
public final class ProcessSharedResultService implements ProcessSharedResultUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessSharedResultService.class);
    static final String GRIDWORDS_PARSER_VERSION = "gridwords-share-v1";
    static final String QUADWORDS_PARSER_VERSION = QuadWordsImageParser.VERSION;
    static final String QUADWORDS_TEXT_ONLY_PARSER_VERSION = "quadwords-share-v2";
    static final String OUTSIDE_ALLOWED_DATE_WINDOW = "OUTSIDE_ALLOWED_DATE_WINDOW";

    private final GridWordsShareParser gridWordsParser;
    private final QuadWordsShareParser quadWordsParser;
    private final AttachmentContentLoader attachmentContentLoader;
    private final QuadWordsImageParser quadWordsImageParser;
    private final GameDateAdmissionPolicy admission;
    private final PlayerStore playerStore;
    private final SubmissionStore submissionStore;
    private final LongPredicate canonicalPublisher;
    private final LongPredicate administrator;
    private final Consumer<LocalDate> statusRefresh;

    public ProcessSharedResultService(GridWordsShareParser gridWordsParser, QuadWordsShareParser quadWordsParser,
            Clock clock, ZoneId timeZone, PlayerStore playerStore, SubmissionStore submissionStore) {
        this(gridWordsParser, quadWordsParser, unavailableLoader(), new QuadWordsImageParser(), clock, timeZone,
                playerStore, submissionStore, ignored -> true, ignored -> false);
    }

    public ProcessSharedResultService(GridWordsShareParser gridWordsParser, QuadWordsShareParser quadWordsParser,
            Clock clock, ZoneId timeZone, PlayerStore playerStore, SubmissionStore submissionStore,
            LongPredicate canonicalPublisher) {
        this(gridWordsParser, quadWordsParser, unavailableLoader(), new QuadWordsImageParser(), clock, timeZone,
                playerStore, submissionStore, canonicalPublisher, ignored -> false);
    }

    public ProcessSharedResultService(GridWordsShareParser gridWordsParser, QuadWordsShareParser quadWordsParser,
            AttachmentContentLoader attachmentContentLoader, QuadWordsImageParser quadWordsImageParser,
            Clock clock, ZoneId timeZone, PlayerStore playerStore, SubmissionStore submissionStore,
            LongPredicate canonicalPublisher) {
        this(gridWordsParser, quadWordsParser, attachmentContentLoader, quadWordsImageParser, clock, timeZone,
                playerStore, submissionStore, canonicalPublisher, ignored -> false);
    }

    public ProcessSharedResultService(GridWordsShareParser gridWordsParser, QuadWordsShareParser quadWordsParser,
            AttachmentContentLoader attachmentContentLoader, QuadWordsImageParser quadWordsImageParser,
            Clock clock, ZoneId timeZone, PlayerStore playerStore, SubmissionStore submissionStore,
            LongPredicate canonicalPublisher, LongPredicate administrator) {
        this(gridWordsParser, quadWordsParser, attachmentContentLoader, quadWordsImageParser, clock, timeZone,
                playerStore, submissionStore, canonicalPublisher, administrator, ignored -> { });
    }

    public ProcessSharedResultService(GridWordsShareParser gridWordsParser, QuadWordsShareParser quadWordsParser,
            AttachmentContentLoader attachmentContentLoader, QuadWordsImageParser quadWordsImageParser,
            Clock clock, ZoneId timeZone, PlayerStore playerStore, SubmissionStore submissionStore,
            LongPredicate canonicalPublisher, LongPredicate administrator, Consumer<LocalDate> statusRefresh) {
        this(gridWordsParser, quadWordsParser, attachmentContentLoader, quadWordsImageParser, clock, timeZone,
                LocalTime.of(6, 0), playerStore, submissionStore, canonicalPublisher, administrator, statusRefresh);
    }

    public ProcessSharedResultService(GridWordsShareParser gridWordsParser, QuadWordsShareParser quadWordsParser,
            AttachmentContentLoader attachmentContentLoader, QuadWordsImageParser quadWordsImageParser,
            Clock clock, ZoneId timeZone, LocalTime dayCloseTime, PlayerStore playerStore, SubmissionStore submissionStore,
            LongPredicate canonicalPublisher, LongPredicate administrator, Consumer<LocalDate> statusRefresh) {
        this.gridWordsParser = Objects.requireNonNull(gridWordsParser);
        this.quadWordsParser = Objects.requireNonNull(quadWordsParser);
        this.attachmentContentLoader = Objects.requireNonNull(attachmentContentLoader);
        this.quadWordsImageParser = Objects.requireNonNull(quadWordsImageParser);
        this.admission = new GameDateAdmissionPolicy(clock, timeZone, dayCloseTime);
        this.playerStore = Objects.requireNonNull(playerStore);
        this.submissionStore = Objects.requireNonNull(submissionStore);
        this.canonicalPublisher = Objects.requireNonNull(canonicalPublisher);
        this.administrator = Objects.requireNonNull(administrator);
        this.statusRefresh = Objects.requireNonNull(statusRefresh);
    }

    @Override
    public ProcessingResult process(InboundSharedMessage message) {
        ParseResult parseResult = parse(message);
        if (parseResult instanceof ParseResult.NotApplicable) {
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
            return new ProcessingResult.Rejected(
                    submission.parserErrorCode().orElse(ParseErrorCode.INVALID_IMAGE_STRUCTURE.name()));
        }
        if (!admission.allows(parsed.gameDate())) {
            // RESULT_STORED and FAILED_RETRYABLE are intentionally not exempt:
            // after logical day close neither recovery nor canonical completion
            // of an ordinary previous-day user operation may proceed.  Such
            // states cannot transition to PARSE_REJECTED, so they are rejected
            // without rewriting already persisted canonical data.
            if (submission.state() == SubmissionStore.SubmissionState.RECEIVED
                    || submission.state() == SubmissionStore.SubmissionState.VALIDATED) {
                return reject(message.messageId(), OUTSIDE_ALLOWED_DATE_WINDOW);
            }
            return new ProcessingResult.Rejected(OUTSIDE_ALLOWED_DATE_WINDOW);
        }
        if (parsed.gameType() == GameType.QUADWORDS) {
            if (submission.state() == SubmissionStore.SubmissionState.RESULT_STORED) {
                return canonicalPublisher.test(message.messageId())
                        ? new ProcessingResult.Accepted(GameType.QUADWORDS)
                        : new ProcessingResult.Ignored();
            }
            try {
                parsed = completeQuadWords(message, parsed);
            } catch (AttachmentContentLoader.RetryableAttachmentException exception) {
                submissionStore.markRetryableFailure(message.messageId(), "attachment download failed");
                return new ProcessingResult.Ignored();
            }
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
                new SubmissionStore.ResultStorage(message.messageId(), result, new PlayerStore.GameParticipationChange(
                        new PlayerStore.ProfileUpdate(message.authorId(), message.authorDisplayName(),
                                administrator.test(message.authorId())),
                        GameParticipationSelection.forGameType(parsed.gameType()),
                        parsed.gameDate())));
        if (stored.state() == SubmissionStore.SubmissionState.SUPERSEDED) {
            return new ProcessingResult.Ignored();
        }
        refreshStatusSafely(parsed.gameDate());
        if (!canonicalPublisher.test(message.messageId())) {
            return new ProcessingResult.Ignored();
        }
        return new ProcessingResult.Accepted(parsed.gameType());
    }

    private ParsedGameResult completeQuadWords(InboundSharedMessage message, ParsedGameResult header) {
        List<AttachmentMetadata> candidates = message.attachments().stream()
                .filter(AttachmentMetadata::isPlausibleImage)
                .toList();
        if (candidates.size() != 1) {
            if (candidates.size() > 1) {
                LOGGER.info("Ignoring ambiguous optional QuadWords images for source message {}", message.messageId());
            }
            return header;
        }
        try {
            byte[] content = attachmentContentLoader.load(candidates.getFirst());
            QuadWordsImageParser.Parse imageParse = quadWordsImageParser.parse(content, header.outcome());
            if (imageParse instanceof QuadWordsImageParser.Parse.Invalid invalid) {
                LOGGER.info("Ignoring invalid optional QuadWords image for source message {}: {}",
                        message.messageId(), invalid.errorCode());
                return header;
            }
            return new ParsedGameResult(header.gameType(), header.gameDate(),
                    header.outcome(), header.duration(), header.gridgamesStreak(), header.board(),
                    java.util.Optional.of(((QuadWordsImageParser.Parse.Parsed) imageParse).boards()));
        } catch (AttachmentContentLoader.AttachmentTooLargeException
                | AttachmentContentLoader.AttachmentUnavailableException exception) {
            LOGGER.info("Ignoring unavailable optional QuadWords image for source message {}: {}",
                    message.messageId(), exception.getClass().getSimpleName());
            return header;
        }
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
        List<SubmissionStore.AttachmentSnapshot> attachments = java.util.stream.IntStream
                .range(0, message.attachments().size())
                .mapToObj(index -> snapshot(index, message.attachments().get(index)))
                .toList();
        return new SubmissionStore.SubmissionRegistration(
                message.messageId(), message.guildId(), message.channelId(), message.authorId(),
                message.content(), attachments, message.receivedAt());
    }

    private SubmissionStore.AttachmentSnapshot snapshot(int index, AttachmentMetadata attachment) {
        return new SubmissionStore.AttachmentSnapshot(index, attachment.filename(),
                attachment.contentType().isBlank()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(attachment.contentType()),
                attachment.size());
    }

    private static boolean isTerminal(SubmissionStore.StoredSubmission submission) {
        // A persisted canonical message is deliberately not terminal: source
        // deletion and the final durable transition still belong to the same
        // ordinary user operation and must not be resumed after day close.
        return submission.state() == SubmissionStore.SubmissionState.COMPLETED;
    }

    private void refreshStatusSafely(LocalDate date) {
        try {
            statusRefresh.accept(date);
        } catch (RuntimeException exception) {
            LOGGER.warn("Daily status refresh after result storage failed for game date {}", date, exception);
        }
    }

    private static String parserVersion(ParsedGameResult parsed) {
        if (parsed.gameType() == GameType.GRIDWORDS) {
            return GRIDWORDS_PARSER_VERSION;
        }
        return parsed.quadWordsBoards().isPresent()
                ? QUADWORDS_PARSER_VERSION
                : QUADWORDS_TEXT_ONLY_PARSER_VERSION;
    }

    private static AttachmentContentLoader unavailableLoader() {
        return attachment -> {
            throw new AttachmentContentLoader.RetryableAttachmentException(
                    "attachment loader is not configured", null);
        };
    }
}
