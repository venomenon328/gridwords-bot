package de.venomenon.gridwordsbot.application.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentReference;
import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsImageParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import de.venomenon.gridwordsbot.port.out.AttachmentContentLoader;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionConflictException;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessSharedResultServiceTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-07-29T10:00:00Z");
    private static final long TOBIAS = 101L;
    private static final long GEORGIA = 102L;

    private InMemoryStore store;
    private ProcessSharedResultService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        store.upsert(new PlayerStore.PlayerUpsert(TOBIAS, "Tobias", true, true));
        store.upsert(new PlayerStore.PlayerUpsert(GEORGIA, "Georgia", true, false));
        service = service(Clock.fixed(RECEIVED_AT, ZoneOffset.UTC), store);
    }

    @Test
    void storesSolvedAndUnsolvedGridWords() {
        assertThat(service.process(message(1L, TOBIAS, gridWords(29, 3))))
                .isEqualTo(new ProcessingResult.Accepted());
        assertThat(service.process(message(2L, GEORGIA, gridWordsUnsolved(29))))
                .isEqualTo(new ProcessingResult.Accepted());

        assertThat(store.results).hasSize(2);
        assertThat(store.results.values()).anySatisfy(result -> {
            assertThat(result.result().parserVersion())
                    .isEqualTo(ProcessSharedResultService.GRIDWORDS_PARSER_VERSION);
            assertThat(result.result().parsedResult().outcome())
                    .isInstanceOf(ShareOutcome.Unsolved.class);
        });
    }

    @Test
    void registersTheSharingPlayerForParticipation() {
        assertThat(service.process(message(3L, TOBIAS, gridWords(29, 3))))
                .isEqualTo(new ProcessingResult.Accepted());

        assertThat(store.lastResultStorage.playerRegistration().profile().discordUserId())
                .isEqualTo(TOBIAS);
        assertThat(store.lastResultStorage.playerRegistration().selection())
                .isEqualTo(de.venomenon.gridwordsbot.domain.model.GameParticipationSelection.GRIDWORDS);
    }

    @Test
    void storesQuadWordsWithAnAnalyzableImage() {
        AtomicReference<AttachmentMetadata> loaded = new AtomicReference<>();
        service = quadService(attachment -> {
            loaded.set(attachment);
            return new byte[] {1, 2, 3};
        }, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));
        AttachmentMetadata image = image(4L, 700L);

        assertThat(service.process(message(4L, TOBIAS, quadWords(), List.of(image))))
                .isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));

        GameResultStore.GameResultUpsert result = onlyResult();
        assertThat(loaded).hasValue(image);
        assertThat(result.parserVersion())
                .isEqualTo(ProcessSharedResultService.QUADWORDS_PARSER_VERSION);
        assertThat(result.parsedResult().quadWordsBoards()).contains(boards(9));
    }

    @Test
    void acceptsMissingAndAmbiguousImagesAsTextOnlyWithoutLoading() {
        AtomicInteger loads = new AtomicInteger();
        service = quadService(attachment -> {
            loads.incrementAndGet();
            return new byte[] {1};
        }, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));

        assertThat(service.process(message(5L, TOBIAS, quadWords(), List.of())))
                .isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));
        assertThat(service.process(message(
                6L,
                TOBIAS,
                quadWords(),
                List.of(image(6L, 701L), image(6L, 702L)))))
                .isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));

        assertThat(loads).hasValue(0);
        assertThat(store.results).hasSize(1);
        assertThat(onlyResult().parserVersion())
                .isEqualTo(ProcessSharedResultService.QUADWORDS_TEXT_ONLY_PARSER_VERSION);
        assertThat(onlyResult().parsedResult().quadWordsBoards()).isEmpty();
    }

    @Test
    void invalidImageGeometryFallsBackToTextOnly() {
        service = quadService(
                attachment -> new byte[] {1},
                imageParser(new QuadWordsImageParser.Parse.Invalid(ParseErrorCode.INVALID_IMAGE_GEOMETRY)));

        assertThat(service.process(message(7L, TOBIAS, quadWords(), List.of(image(7L, 700L)))))
                .isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));

        assertThat(onlyResult().parserVersion())
                .isEqualTo(ProcessSharedResultService.QUADWORDS_TEXT_ONLY_PARSER_VERSION);
        assertThat(onlyResult().parsedResult().quadWordsBoards()).isEmpty();
        assertThat(store.submissions.get(7L).parserErrorCode()).isEmpty();
    }

    @Test
    void oversizedImagesFallBackToTextOnlyAndTransientImagesAreRetried() {
        service = quadService(attachment -> {
            throw new AttachmentContentLoader.AttachmentTooLargeException("too large");
        }, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));
        assertThat(service.process(message(8L, TOBIAS, quadWords(), List.of(image(8L, 700L)))))
                .isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));
        assertThat(onlyResult().parserVersion())
                .isEqualTo(ProcessSharedResultService.QUADWORDS_TEXT_ONLY_PARSER_VERSION);

        store = new InMemoryStore();
        store.upsert(new PlayerStore.PlayerUpsert(TOBIAS, "Tobias", true, true));
        service = quadService(attachment -> {
            throw new AttachmentContentLoader.RetryableAttachmentException("network", null);
        }, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));
        assertThat(service.process(message(9L, TOBIAS, quadWords(), List.of(image(9L, 700L)))))
                .isEqualTo(new ProcessingResult.Ignored());

        assertThat(store.results).isEmpty();
        assertThat(store.retryableFailures).isOne();
    }

    @Test
    void replayOfAcceptedQuadWordsDoesNotReloadOrDuplicate() {
        AtomicInteger loads = new AtomicInteger();
        service = quadService(attachment -> {
            loads.incrementAndGet();
            return new byte[] {1};
        }, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));
        InboundSharedMessage inbound = message(10L, TOBIAS, quadWords(), List.of(image(10L, 700L)));

        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));
        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));

        assertThat(loads).hasValue(1);
        assertThat(store.results).hasSize(1);
        assertThat(store.submissions).hasSize(1);
    }

    @Test
    void validatesInvalidQuadWordsHeaderBeforeLoading() {
        AttachmentContentLoader loader = mock(AttachmentContentLoader.class);
        service = quadService(loader, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));

        assertThat(service.process(message(
                11L,
                TOBIAS,
                "QuadWords (29. Juli 2026) 10/9 in 4:18",
                List.of(image(11L, 700L)))))
                .isEqualTo(new ProcessingResult.Rejected("INVALID_ATTEMPT_RESULT"));

        verifyNoInteractions(loader);
    }

    @Test
    void leavesGridWordsIndependentFromAttachments() {
        AttachmentContentLoader loader = mock(AttachmentContentLoader.class);
        service = quadService(loader, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));

        assertThat(service.process(message(12L, TOBIAS, gridWords(29, 3))))
                .isEqualTo(new ProcessingResult.Accepted());

        verifyNoInteractions(loader);
    }

    @Test
    void ignoresMessagesThatAreNotSharesWithoutAccessingPorts() {
        PlayerStore players = mock(PlayerStore.class);
        SubmissionStore submissions = mock(SubmissionStore.class);
        ProcessSharedResultService isolated = new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                players,
                submissions);

        assertThat(isolated.process(message(13L, TOBIAS, "Guten Morgen")))
                .isEqualTo(new ProcessingResult.Ignored());
        verifyNoInteractions(players, submissions);
    }

    @Test
    void rejectsBoardlessGridWordsButAcceptsBoardlessQuadWords() {
        assertThat(service.process(message(
                14L, TOBIAS, "GridWords (29. Juli 2026) 3/6 in 1:25")))
                .isEqualTo(new ProcessingResult.Rejected("MISSING_BOARD"));
        assertThat(service.process(message(15L, TOBIAS, quadWords())))
                .isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));

        assertThat(store.results).hasSize(1);
        assertThat(store.submissions.get(14L).state())
                .isEqualTo(SubmissionStore.SubmissionState.PARSE_REJECTED);
        assertThat(store.submissions.get(15L).state())
                .isEqualTo(SubmissionStore.SubmissionState.RESULT_STORED);
    }

    @Test
    void acceptsTextOnlyUnsolvedQuadWords() {
        assertThat(service.process(message(
                16L,
                TOBIAS,
                "QuadWords (29. Juli 2026) X/9 in 8:45")))
                .isEqualTo(new ProcessingResult.Accepted(GameType.QUADWORDS));

        assertThat(onlyResult().parsedResult().outcome())
                .isInstanceOf(ShareOutcome.Unsolved.class);
        assertThat(onlyResult().parsedResult().quadWordsBoards()).isEmpty();
    }

    @Test
    void rejectsDatesOutsideTodayAndYesterday() {
        assertThat(service.process(message(17L, TOBIAS, gridWords(27, 3))))
                .isEqualTo(new ProcessingResult.Rejected(
                        ProcessSharedResultService.OUTSIDE_ALLOWED_DATE_WINDOW));
        assertThat(service.process(message(18L, TOBIAS, gridWords(30, 3))))
                .isEqualTo(new ProcessingResult.Rejected(
                        ProcessSharedResultService.OUTSIDE_ALLOWED_DATE_WINDOW));
        assertThat(store.results).isEmpty();
    }

    @Test
    void aNewCorrectionUpdatesTheSameBusinessResult() {
        assertThat(service.process(message(19L, TOBIAS, gridWords(29, 4))))
                .isEqualTo(new ProcessingResult.Accepted());
        assertThat(service.process(message(20L, TOBIAS, gridWords(29, 2))))
                .isEqualTo(new ProcessingResult.Accepted());

        assertThat(store.results).hasSize(1);
        ShareOutcome.Solved outcome = (ShareOutcome.Solved) onlyResult().parsedResult().outcome();
        assertThat(outcome.attemptsUsed()).isEqualTo(2);
    }

    @Test
    void statusRefreshUsesTheStoredBusinessDateAndFailureDoesNotRollBack() {
        AtomicReference<LocalDate> refreshed = new AtomicReference<>();
        service = new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                attachment -> { throw new AssertionError("no attachment expected"); },
                new QuadWordsImageParser(),
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                store,
                store,
                ignored -> true,
                ignored -> false,
                refreshed::set);

        assertThat(service.process(message(21L, TOBIAS, gridWords(29, 3))))
                .isEqualTo(new ProcessingResult.Accepted());
        assertThat(refreshed).hasValue(LocalDate.of(2026, 7, 29));

        service = new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                attachment -> { throw new AssertionError("no attachment expected"); },
                new QuadWordsImageParser(),
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                store,
                store,
                ignored -> true,
                ignored -> false,
                ignored -> { throw new IllegalStateException("status unavailable"); });
        assertThat(service.process(message(22L, GEORGIA, gridWords(29, 4))))
                .isEqualTo(new ProcessingResult.Accepted());
        assertThat(store.results).hasSize(2);
    }

    @Test
    void persistenceFailureIsNotReportedAsSuccess() {
        InMemoryStore failingStore = new InMemoryStore() {
            @Override
            public StoredSubmission register(SubmissionRegistration registration) {
                throw new IllegalStateException("database unavailable");
            }
        };
        ProcessSharedResultService failing = service(Clock.fixed(RECEIVED_AT, ZoneOffset.UTC), failingStore);

        assertThatThrownBy(() -> failing.process(message(23L, TOBIAS, gridWords(29, 3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    private ProcessSharedResultService quadService(
            AttachmentContentLoader loader,
            QuadWordsImageParser parser) {
        return new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                loader,
                parser,
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"),
                store,
                store,
                ignored -> true);
    }

    private ProcessSharedResultService service(Clock clock, InMemoryStore configuredStore) {
        return new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                clock,
                ZoneId.of("Europe/Berlin"),
                configuredStore,
                configuredStore,
                ignored -> true);
    }

    private static QuadWordsImageParser imageParser(QuadWordsImageParser.Parse parse) {
        return new QuadWordsImageParser() {
            @Override
            public Parse parse(byte[] bytes, ShareOutcome outcome) {
                return parse;
            }
        };
    }

    private static AttachmentMetadata image(long sourceMessageId, long attachmentId) {
        return new AttachmentMetadata(
                "quad.png",
                "image/png",
                42L,
                Optional.of(new AttachmentReference(12L, sourceMessageId, attachmentId)));
    }

    private static QuadWordsBoards boards(int rows) {
        List<String> boardRows = java.util.stream.IntStream.range(0, rows)
                .mapToObj(ignored -> "\u2B1C".repeat(5))
                .toList();
        QuadWordsBoard board = new QuadWordsBoard(boardRows);
        return new QuadWordsBoards(board, board, board, board);
    }

    private GameResultStore.GameResultUpsert onlyResult() {
        assertThat(store.results).hasSize(1);
        return store.results.values().iterator().next().result();
    }

    private static String quadWords() {
        return "QuadWords (29. Juli 2026) 9/9 in 4:18";
    }

    private static InboundSharedMessage message(long messageId, long authorId, String content) {
        return message(messageId, authorId, content, List.of());
    }

    private static InboundSharedMessage message(
            long messageId,
            long authorId,
            String content,
            List<AttachmentMetadata> attachments) {
        return new InboundSharedMessage(
                11L, 12L, messageId, authorId, "Player", content, attachments, RECEIVED_AT);
    }

    private static String gridWords(int day, int attempts) {
        return "GridWords (" + day + ". Juli 2026) " + attempts + "/6 in 1:25 \uD83D\uDD252\n"
                + String.join("\n", List.of(
                        "\u2B1C".repeat(5),
                        "\uD83D\uDFE8".repeat(5),
                        "\uD83D\uDFE9".repeat(5),
                        "\u2B1C".repeat(5)).subList(0, attempts));
    }

    private static String gridWordsUnsolved(int day) {
        return "GridWords (" + day + ". Juli 2026) X/6 in 6:42\n"
                + String.join("\n", List.of(
                        "\u2B1C".repeat(5),
                        "\uD83D\uDFE8".repeat(5),
                        "\uD83D\uDFE9".repeat(5),
                        "\u2B1C".repeat(5),
                        "\uD83D\uDFE8".repeat(5),
                        "\uD83D\uDFE9".repeat(5)));
    }

    private static class InMemoryStore implements PlayerStore, SubmissionStore {
        private final Map<Long, StoredPlayer> players = new HashMap<>();
        private final Map<Long, StoredSubmission> submissions = new HashMap<>();
        private final Map<ResultKey, StoredResult> results = new HashMap<>();
        private long nextResultId = 1L;
        private ResultStorage lastResultStorage;
        private int retryableFailures;

        @Override
        public StoredPlayer upsert(PlayerUpsert request) {
            StoredPlayer stored = new StoredPlayer(
                    request.discordUserId(),
                    request.displayName(),
                    request.active(),
                    request.administrator(),
                    Instant.EPOCH,
                    Instant.EPOCH);
            players.put(request.discordUserId(), stored);
            return stored;
        }

        @Override
        public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) {
            return Optional.ofNullable(players.get(discordUserId));
        }

        @Override
        public StoredSubmission register(SubmissionRegistration registration) {
            StoredSubmission existing = submissions.get(registration.sourceMessageId());
            if (existing != null) {
                if (existing.guildId() != registration.guildId()
                        || existing.channelId() != registration.channelId()
                        || existing.authorPlayerId() != registration.authorPlayerId()
                        || !existing.rawMessageContent().equals(registration.rawMessageContent())
                        || !existing.attachments().equals(registration.attachments())) {
                    throw new SubmissionConflictException("conflicting source message reuse");
                }
                return existing;
            }
            StoredSubmission stored = new StoredSubmission(
                    registration.sourceMessageId(),
                    registration.guildId(),
                    registration.channelId(),
                    registration.authorPlayerId(),
                    registration.rawMessageContent(),
                    SubmissionState.RECEIVED,
                    Optional.empty(),
                    registration.attachments(),
                    Optional.empty(),
                    Optional.empty(),
                    registration.receivedAt(),
                    registration.receivedAt());
            submissions.put(stored.sourceMessageId(), stored);
            return stored;
        }

        @Override
        public Optional<StoredSubmission> findBySourceMessageId(long sourceMessageId) {
            return Optional.ofNullable(submissions.get(sourceMessageId));
        }

        @Override
        public StoredSubmission storeResult(ResultStorage request) {
            lastResultStorage = request;
            StoredSubmission submission = required(request.sourceMessageId());
            if (submission.authorPlayerId() != request.result().playerId()) {
                throw new SubmissionConflictException("wrong player");
            }
            if (submission.state() == SubmissionState.RESULT_STORED) {
                StoredResult stored = results.get(ResultKey.of(request.result()));
                if (stored != null && stored.result().equals(request.result())) {
                    return submission;
                }
                throw new SubmissionConflictException("contradictory result replay");
            }
            ResultKey key = ResultKey.of(request.result());
            StoredResult previous = results.get(key);
            long resultId = previous == null ? nextResultId++ : previous.id();
            results.put(key, new StoredResult(resultId, request.result()));
            StoredSubmission stored = with(
                    submission, SubmissionState.RESULT_STORED, Optional.of(resultId), Optional.empty());
            submissions.put(stored.sourceMessageId(), stored);
            return stored;
        }

        @Override
        public StoredSubmission reject(RejectedSubmission request) {
            StoredSubmission rejected = with(
                    required(request.sourceMessageId()),
                    SubmissionState.PARSE_REJECTED,
                    Optional.empty(),
                    Optional.of(request.errorCode()));
            submissions.put(rejected.sourceMessageId(), rejected);
            return rejected;
        }

        @Override
        public void markRetryableFailure(long sourceMessageId, String safeTechnicalMessage) {
            required(sourceMessageId);
            retryableFailures++;
        }

        @Override
        public boolean transition(
                long sourceMessageId,
                SubmissionState expectedState,
                SubmissionState targetState) {
            StoredSubmission submission = required(sourceMessageId);
            if (submission.state() != expectedState) {
                return false;
            }
            submissions.put(sourceMessageId, with(
                    submission, targetState, submission.gameResultId(), submission.parserErrorCode()));
            return true;
        }

        private StoredSubmission required(long sourceMessageId) {
            StoredSubmission submission = submissions.get(sourceMessageId);
            if (submission == null) {
                throw new IllegalStateException("submission is missing");
            }
            return submission;
        }

        private static StoredSubmission with(
                StoredSubmission submission,
                SubmissionState state,
                Optional<Long> resultId,
                Optional<String> errorCode) {
            return new StoredSubmission(
                    submission.sourceMessageId(),
                    submission.guildId(),
                    submission.channelId(),
                    submission.authorPlayerId(),
                    submission.rawMessageContent(),
                    state,
                    resultId,
                    submission.attachments(),
                    errorCode,
                    Optional.empty(),
                    submission.receivedAt(),
                    RECEIVED_AT);
        }
    }

    private record ResultKey(long playerId, GameType gameType, LocalDate gameDate) {
        private static ResultKey of(GameResultStore.GameResultUpsert result) {
            return new ResultKey(
                    result.playerId(),
                    result.parsedResult().gameType(),
                    result.parsedResult().gameDate());
        }
    }

    private record StoredResult(long id, GameResultStore.GameResultUpsert result) {
    }
}
