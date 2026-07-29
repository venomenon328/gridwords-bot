package de.venomenon.gridwordsbot.application.submission;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.ParseResult;
import de.venomenon.gridwordsbot.domain.parsing.ShareParseInput;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
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
    static final String QUADWORDS_PARSER_VERSION = "quadwords-share-v1";
    static final String OUTSIDE_ALLOWED_DATE_WINDOW = "OUTSIDE_ALLOWED_DATE_WINDOW";

    private final GridWordsShareParser gridWordsParser;
    private final QuadWordsShareParser quadWordsParser;
    private final Clock clock;
    private final ZoneId timeZone;
    private final PlayerStore playerStore;
    private final SubmissionStore submissionStore;
    private final List<Long> configuredPlayerIds;
    private final LongPredicate canonicalPublisher;

    public ProcessSharedResultService(
            GridWordsShareParser gridWordsParser,
            QuadWordsShareParser quadWordsParser,
            Clock clock,
            ZoneId timeZone,
            PlayerStore playerStore,
            SubmissionStore submissionStore) {
        this(
                gridWordsParser,
                quadWordsParser,
                clock,
                timeZone,
                playerStore,
                submissionStore,
                List.of(),
                ignored -> true);
    }

    public ProcessSharedResultService(
            GridWordsShareParser gridWordsParser,
            QuadWordsShareParser quadWordsParser,
            Clock clock,
            ZoneId timeZone,
            PlayerStore playerStore,
            SubmissionStore submissionStore,
            LongPredicate canonicalPublisher) {
        this(
                gridWordsParser,
                quadWordsParser,
                clock,
                timeZone,
                playerStore,
                submissionStore,
                List.of(),
                canonicalPublisher);
    }

    public ProcessSharedResultService(
            GridWordsShareParser gridWordsParser,
            QuadWordsShareParser quadWordsParser,
            Clock clock,
            ZoneId timeZone,
            PlayerStore playerStore,
            SubmissionStore submissionStore,
            List<Long> configuredPlayerIds,
            LongPredicate canonicalPublisher) {
        this.gridWordsParser = Objects.requireNonNull(gridWordsParser);
        this.quadWordsParser = Objects.requireNonNull(quadWordsParser);
        this.clock = Objects.requireNonNull(clock);
        this.timeZone = Objects.requireNonNull(timeZone);
        this.playerStore = Objects.requireNonNull(playerStore);
        this.submissionStore = Objects.requireNonNull(submissionStore);
        this.configuredPlayerIds = List.copyOf(Objects.requireNonNull(configuredPlayerIds));
        if (!this.configuredPlayerIds.isEmpty()
                && (this.configuredPlayerIds.size() != 2
                || this.configuredPlayerIds.stream().distinct().count() != 2
                || this.configuredPlayerIds.stream().anyMatch(playerId -> playerId <= 0))) {
            throw new IllegalArgumentException("configuredPlayerIds must contain exactly two distinct positive IDs");
        }
        this.canonicalPublisher = Objects.requireNonNull(canonicalPublisher);
    }

    @Override
    public ProcessingResult process(InboundSharedMessage message) {
        ParseResult parseResult = parse(message);
        if (parseResult instanceof ParseResult.NotApplicable) {
            return new ProcessingResult.Ignored();
        }
        if (playerStore.findByDiscordUserId(message.authorId()).filter(PlayerStore.StoredPlayer::active).isEmpty()) {
            return new ProcessingResult.Ignored();
        }

        SubmissionStore.StoredSubmission submission = submissionStore.register(registration(message));
        if (parseResult instanceof ParseResult.Invalid invalid) {
            submissionStore.reject(new SubmissionStore.RejectedSubmission(message.messageId(), invalid.errorCode().name()));
            return new ProcessingResult.Rejected(invalid.errorCode().name());
        }

        ParsedGameResult parsed = ((ParseResult.Parsed) parseResult).result();
        GameResultStore.GameResultUpsert result = new GameResultStore.GameResultUpsert(
                message.authorId(),
                parsed,
                message.content(),
                parserVersion(parsed));

        // A source message that was stored already remains accepted after its date window has elapsed.
        if (submission.state() == SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED) {
            return new ProcessingResult.Accepted();
        }
        if (submission.state() == SubmissionStore.SubmissionState.SUPERSEDED) {
            return new ProcessingResult.Ignored();
        }
        if (submission.state() != SubmissionStore.SubmissionState.RESULT_STORED
                && submission.state() != SubmissionStore.SubmissionState.FAILED_RETRYABLE
                && !isTodayOrYesterday(parsed.gameDate())) {
            submissionStore.reject(new SubmissionStore.RejectedSubmission(message.messageId(), OUTSIDE_ALLOWED_DATE_WINDOW));
            return new ProcessingResult.Rejected(OUTSIDE_ALLOWED_DATE_WINDOW);
        }

        SubmissionStore.StoredSubmission stored = submissionStore.storeResult(
                new SubmissionStore.ResultStorage(message.messageId(), result, configuredPlayerIds));
        if (stored.state() == SubmissionStore.SubmissionState.SUPERSEDED) {
            return new ProcessingResult.Ignored();
        }
        if (parsed.gameType() == GameType.GRIDWORDS && !canonicalPublisher.test(message.messageId())) {
            return new ProcessingResult.Ignored();
        }
        return new ProcessingResult.Accepted();
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
                message.messageId(),
                message.guildId(),
                message.channelId(),
                message.authorId(),
                message.content(),
                attachments,
                message.receivedAt());
    }

    private SubmissionStore.AttachmentSnapshot snapshot(int index, AttachmentMetadata attachment) {
        return new SubmissionStore.AttachmentSnapshot(
                index,
                attachment.filename(),
                attachment.contentType().isBlank()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(attachment.contentType()),
                attachment.size());
    }

    private boolean isTodayOrYesterday(LocalDate gameDate) {
        LocalDate today = clock.instant().atZone(timeZone).toLocalDate();
        return gameDate.equals(today) || gameDate.equals(today.minusDays(1));
    }

    private static String parserVersion(ParsedGameResult parsed) {
        return switch (parsed.gameType()) {
            case GRIDWORDS -> GRIDWORDS_PARSER_VERSION;
            case QUADWORDS -> QUADWORDS_PARSER_VERSION;
        };
    }
}
