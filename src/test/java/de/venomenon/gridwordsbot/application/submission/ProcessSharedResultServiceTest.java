package de.venomenon.gridwordsbot.application.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentReference;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsImageParser;
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
import java.util.OptionalLong;
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
    void storesSolvedGridWordsForToday() {
        ProcessingResult result = service.process(message(1L, TOBIAS, gridWords(29, 3)));

        assertThat(result).isEqualTo(new ProcessingResult.Accepted());
        assertThat(store.results).hasSize(1);
        GameResultStore.GameResultUpsert stored = store.results.values().iterator().next().result();
        assertThat(stored.parserVersion()).isEqualTo(ProcessSharedResultService.GRIDWORDS_PARSER_VERSION);
        assertThat(stored.parsedResult().outcome()).isInstanceOf(ShareOutcome.Solved.class);
    }

    @Test
    void passesTheConfiguredPlayerPairToResultStorage() {
        service = new ProcessSharedResultService(
                new GridWordsShareParser(), new QuadWordsShareParser(), Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"), store, store, List.of(TOBIAS, GEORGIA), ignored -> true);

        assertThat(service.process(message(18L, TOBIAS, gridWords(29, 3))))
                .isEqualTo(new ProcessingResult.Accepted());

        assertThat(store.lastResultStorage.configuredPlayerIds()).containsExactly(TOBIAS, GEORGIA);
    }

    @Test
    void storesSolvedAndUnsolvedResults() {
        assertThat(service.process(message(2L, TOBIAS, gridWords(29, 4)))).isEqualTo(new ProcessingResult.Accepted());
        assertThat(service.process(message(3L, GEORGIA, gridWordsUnsolved(29)))).isEqualTo(new ProcessingResult.Accepted());

        assertThat(store.results).hasSize(2);
        assertThat(store.results.values()).anySatisfy(result ->
                assertThat(result.result().parsedResult().outcome()).isInstanceOf(ShareOutcome.Unsolved.class));
    }

    @Test
    void storesQuadWordsForYesterdayWithAImageAttachment() {
        AtomicReference<AttachmentMetadata> loaded = new AtomicReference<>();
        service = quadService(attachment -> {
            loaded.set(attachment);
            return new byte[] {1, 2, 3};
        }, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));
        AttachmentMetadata image = image(4L, 700L);

        ProcessingResult result = service.process(message(
                4L,
                TOBIAS,
                "QuadWords (28. Juli 2026) 9/9 in 4:18",
                List.of(new AttachmentMetadata("note.txt", "text/plain", 1L), image)));

        assertThat(result).isEqualTo(new ProcessingResult.Accepted(de.venomenon.gridwordsbot.domain.model.GameType.QUADWORDS));
        assertThat(loaded).hasValue(image);
        assertThat(store.results.values().iterator().next().result().parserVersion())
                .isEqualTo(ProcessSharedResultService.QUADWORDS_PARSER_VERSION);
        assertThat(store.results.values().iterator().next().result().parsedResult().quadWordsBoards()).isPresent();
    }

    @Test
    void rejectsMissingOrAmbiguousPlausibleQuadWordsImagesWithoutLoading() {
        AtomicInteger loads = new AtomicInteger();
        service = quadService(attachment -> {
            loads.incrementAndGet();
            return new byte[] {1};
        }, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));

        assertThat(service.process(message(20L, TOBIAS, quadWords(), List.of())))
                .isEqualTo(new ProcessingResult.Rejected("MISSING_IMAGE_ATTACHMENT"));
        assertThat(service.process(message(21L, TOBIAS, quadWords(), List.of(image(21L, 701L), image(21L, 702L)))))
                .isEqualTo(new ProcessingResult.Rejected("AMBIGUOUS_IMAGE_ATTACHMENT"));

        assertThat(loads).hasValue(0);
        assertThat(store.results).isEmpty();
        assertThat(store.submissions.get(20L).state()).isEqualTo(SubmissionStore.SubmissionState.PARSE_REJECTED);
        assertThat(store.submissions.get(21L).state()).isEqualTo(SubmissionStore.SubmissionState.PARSE_REJECTED);
    }

    @Test
    void rejectsAFactualImageParseFailureAfterLoadingTheSelectedAttachment() {
        AtomicReference<AttachmentMetadata> loaded = new AtomicReference<>();
        service = quadService(attachment -> {
            loaded.set(attachment);
            return new byte[] {1};
        }, imageParser(new QuadWordsImageParser.Parse.Invalid(de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode.INVALID_IMAGE_GEOMETRY)));
        AttachmentMetadata image = image(22L, 700L);

        assertThat(service.process(message(22L, TOBIAS, quadWords(), List.of(image))))
                .isEqualTo(new ProcessingResult.Rejected("INVALID_IMAGE_GEOMETRY"));

        assertThat(loaded).hasValue(image);
        assertThat(store.results).isEmpty();
        assertThat(store.submissions.get(22L).parserErrorCode()).contains("INVALID_IMAGE_GEOMETRY");
    }

    @Test
    void rejectsAnAttachmentThatExceedsTheLoaderLimitAsAFactualImageFailure() {
        service = quadService(attachment -> {
            throw new AttachmentContentLoader.AttachmentTooLargeException("too large");
        }, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));

        assertThat(service.process(message(27L, TOBIAS, quadWords(), List.of(image(27L, 700L)))))
                .isEqualTo(new ProcessingResult.Rejected("IMAGE_TOO_LARGE"));

        assertThat(store.results).isEmpty();
        assertThat(store.submissions.get(27L).parserErrorCode()).contains("IMAGE_TOO_LARGE");
    }

    @Test
    void keepsTransientAttachmentFailuresRetryableAndWithoutAResult() {
        AtomicInteger loads = new AtomicInteger();
        service = quadService(attachment -> {
            loads.incrementAndGet();
            throw new AttachmentContentLoader.RetryableAttachmentException("network", null);
        }, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));
        InboundSharedMessage inbound = message(23L, TOBIAS, quadWords(), List.of(image(23L, 700L)));

        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Ignored());
        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Ignored());

        assertThat(loads).hasValue(2);
        assertThat(store.retryableFailures).isEqualTo(1);
        assertThat(store.results).isEmpty();
    }

    @Test
    void doesNotReloadOrStoreAnAlreadyAcceptedQuadWordsReplay() {
        AtomicInteger loads = new AtomicInteger();
        service = quadService(attachment -> {
            loads.incrementAndGet();
            return new byte[] {1};
        }, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));
        InboundSharedMessage inbound = message(24L, TOBIAS, quadWords(), List.of(image(24L, 700L)));

        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Accepted(de.venomenon.gridwordsbot.domain.model.GameType.QUADWORDS));
        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Accepted(de.venomenon.gridwordsbot.domain.model.GameType.QUADWORDS));

        assertThat(loads).hasValue(1);
        assertThat(store.results).hasSize(1);
    }

    @Test
    void doesNotSignalSuccessForStoredQuadWordsThatAreNotPublishable() {
        InboundSharedMessage inbound = message(28L, TOBIAS, quadWords(), List.of(image(28L, 700L)));
        service = quadService(attachment -> new byte[] {1},
                imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));
        assertThat(service.process(inbound)).isEqualTo(
                new ProcessingResult.Accepted(de.venomenon.gridwordsbot.domain.model.GameType.QUADWORDS));

        AttachmentContentLoader loader = mock(AttachmentContentLoader.class);
        service = new ProcessSharedResultService(
                new GridWordsShareParser(), new QuadWordsShareParser(), loader,
                imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))),
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC), ZoneId.of("Europe/Berlin"), store, store,
                List.of(), ignored -> false);

        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Ignored());
        verifyNoInteractions(loader);
        assertThat(store.results).hasSize(1);
    }

    @Test
    void leavesGridWordsIndependentFromTheAttachmentPipeline() {
        AttachmentContentLoader loader = mock(AttachmentContentLoader.class);
        service = quadService(loader, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));

        assertThat(service.process(message(25L, TOBIAS, gridWords(29, 3))))
                .isEqualTo(new ProcessingResult.Accepted());

        verifyNoInteractions(loader);
    }

    @Test
    void validatesAnInvalidQuadWordsHeaderBeforeLoadingAnAttachment() {
        AttachmentContentLoader loader = mock(AttachmentContentLoader.class);
        service = quadService(loader, imageParser(new QuadWordsImageParser.Parse.Parsed(boards(9))));

        assertThat(service.process(message(26L, TOBIAS, "QuadWords (29. Juli 2026) 10/9 in 4:18", List.of(image(26L, 700L)))))
                .isEqualTo(new ProcessingResult.Rejected("INVALID_ATTEMPT_RESULT"));

        verifyNoInteractions(loader);
    }

    @Test
    void ignoresMessagesThatAreNotShares() {
        assertThat(service.process(message(5L, TOBIAS, "Guten Morgen"))).isEqualTo(new ProcessingResult.Ignored());

        assertThat(store.submissions).isEmpty();
        assertThat(store.results).isEmpty();
    }

    @Test
    void doesNotAccessAnyPortForNotApplicableMessages() {
        PlayerStore playerStore = mock(PlayerStore.class);
        SubmissionStore submissionStore = mock(SubmissionStore.class);
        ProcessSharedResultService isolatedService = new ProcessSharedResultService(
                new GridWordsShareParser(), new QuadWordsShareParser(), Clock.fixed(RECEIVED_AT, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"), playerStore, submissionStore);

        assertThat(isolatedService.process(message(16L, TOBIAS, "Guten Morgen")))
                .isEqualTo(new ProcessingResult.Ignored());

        verifyNoInteractions(playerStore, submissionStore);
    }

    @Test
    void persistsRecognizableInvalidGridWordsAndQuadWords() {
        ProcessingResult grid = service.process(message(6L, TOBIAS, "GridWords (29. Juli 2026) 3/6 in 1:25"));
        ProcessingResult quad = service.process(message(7L, TOBIAS, "QuadWords (29. Juli 2026) 9/9 in 4:18"));

        assertThat(grid).isEqualTo(new ProcessingResult.Rejected("MISSING_BOARD"));
        assertThat(quad).isEqualTo(new ProcessingResult.Rejected("MISSING_IMAGE_ATTACHMENT"));
        assertThat(store.results).isEmpty();
        assertThat(store.submissions.get(6L).state()).isEqualTo(SubmissionStore.SubmissionState.PARSE_REJECTED);
        assertThat(store.submissions.get(6L).parserErrorCode()).contains("MISSING_BOARD");
        assertThat(store.submissions.get(7L).parserErrorCode()).contains("MISSING_IMAGE_ATTACHMENT");
    }

    @Test
    void rejectsDatesBeforeYesterdayAndInTheFutureWithoutAResult() {
        ProcessingResult old = service.process(message(8L, TOBIAS, gridWords(27, 3)));
        ProcessingResult future = service.process(message(9L, TOBIAS, gridWords(30, 3)));

        assertThat(old).isEqualTo(new ProcessingResult.Rejected(ProcessSharedResultService.OUTSIDE_ALLOWED_DATE_WINDOW));
        assertThat(future).isEqualTo(new ProcessingResult.Rejected(ProcessSharedResultService.OUTSIDE_ALLOWED_DATE_WINDOW));
        assertThat(store.results).isEmpty();
        assertThat(store.submissions.values()).allSatisfy(submission -> {
            assertThat(submission.state()).isEqualTo(SubmissionStore.SubmissionState.PARSE_REJECTED);
            assertThat(submission.parserErrorCode()).contains(ProcessSharedResultService.OUTSIDE_ALLOWED_DATE_WINDOW);
        });
    }

    @Test
    void usesBerlinCalendarDatesNearMidnight() {
        Clock afterBerlinMidnight = Clock.fixed(Instant.parse("2026-07-29T22:30:00Z"), ZoneOffset.UTC);
        ProcessSharedResultService nearMidnight = service(afterBerlinMidnight, store);

        assertThat(nearMidnight.process(message(10L, TOBIAS, gridWords(29, 3))))
                .isEqualTo(new ProcessingResult.Accepted());
        assertThat(nearMidnight.process(message(11L, TOBIAS, gridWords(28, 3))))
                .isEqualTo(new ProcessingResult.Rejected(ProcessSharedResultService.OUTSIDE_ALLOWED_DATE_WINDOW));
    }

    @Test
    void replaysIdenticalEventsWithoutDuplicateRows() {
        InboundSharedMessage inbound = message(12L, TOBIAS, gridWords(29, 3));

        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Accepted());
        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Accepted());

        assertThat(store.submissions).hasSize(1);
        assertThat(store.results).hasSize(1);
    }

    @Test
    void updatesTheSameResultForANewCorrectionMessage() {
        assertThat(service.process(message(13L, TOBIAS, gridWords(29, 4)))).isEqualTo(new ProcessingResult.Accepted());
        assertThat(service.process(message(14L, TOBIAS, gridWords(29, 2)))).isEqualTo(new ProcessingResult.Accepted());

        assertThat(store.submissions).hasSize(2);
        assertThat(store.results).hasSize(1);
        GameResultStore.GameResultUpsert result = store.results.values().iterator().next().result();
        assertThat(((ShareOutcome.Solved) result.parsedResult().outcome()).attemptsUsed()).isEqualTo(2);
    }

    @Test
    void replaysAnAlreadyPublishedSourceWithoutTryingToStoreOrPublishAgain() {
        InboundSharedMessage inbound = message(17L, TOBIAS, gridWords(29, 3));
        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Accepted());
        SubmissionStore.StoredSubmission stored = store.submissions.get(17L);
        store.submissions.put(17L, store.with(
                stored,
                SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                stored.gameResultId(),
                Optional.empty()));

        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Accepted());
        assertThat(store.results).hasSize(1);
        assertThat(store.submissions.get(17L).state())
                .isEqualTo(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED);
    }

    @Test
    void ignoresASupersededReplayWithoutTreatingItAsAcceptedAgain() {
        InboundSharedMessage inbound = message(19L, TOBIAS, gridWords(29, 3));
        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Accepted());
        SubmissionStore.StoredSubmission stored = store.submissions.get(19L);
        store.submissions.put(19L, store.with(
                stored,
                SubmissionStore.SubmissionState.SUPERSEDED,
                stored.gameResultId(),
                Optional.empty()));

        assertThat(service.process(inbound)).isEqualTo(new ProcessingResult.Ignored());
        assertThat(store.results).hasSize(1);
        assertThat(store.submissions.get(19L).state()).isEqualTo(SubmissionStore.SubmissionState.SUPERSEDED);
    }

    @Test
    void doesNotReturnSuccessWhenPersistenceFails() {
        InMemoryStore failingStore = new InMemoryStore() {
            @Override
            public StoredSubmission register(SubmissionRegistration registration) {
                throw new IllegalStateException("database unavailable");
            }
        };
        failingStore.upsert(new PlayerStore.PlayerUpsert(TOBIAS, "Tobias", true, true));
        ProcessSharedResultService failingService = service(Clock.fixed(RECEIVED_AT, ZoneOffset.UTC), failingStore);

        assertThatThrownBy(() -> failingService.process(message(15L, TOBIAS, gridWords(29, 3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    private ProcessSharedResultService quadService(AttachmentContentLoader loader, QuadWordsImageParser parser) {
        return new ProcessSharedResultService(new GridWordsShareParser(), new QuadWordsShareParser(), loader, parser,
                Clock.fixed(RECEIVED_AT, ZoneOffset.UTC), ZoneId.of("Europe/Berlin"), store, store, List.of(), ignored -> true);
    }

    private QuadWordsImageParser imageParser(QuadWordsImageParser.Parse parse) {
        return new QuadWordsImageParser() {
            @Override
            public Parse parse(byte[] bytes, ShareOutcome outcome) {
                return parse;
            }
        };
    }

    private AttachmentMetadata image(long sourceMessageId, long attachmentId) {
        return new AttachmentMetadata("quad.png", "image/png", 42L,
                Optional.of(new AttachmentReference(12L, sourceMessageId, attachmentId)));
    }

    private QuadWordsBoards boards(int rows) {
        List<String> boardRows = java.util.stream.IntStream.range(0, rows).mapToObj(ignored -> "\u2B1C".repeat(5)).toList();
        QuadWordsBoard board = new QuadWordsBoard(boardRows);
        return new QuadWordsBoards(board, board, board, board);
    }

    private String quadWords() {
        return "QuadWords (29. Juli 2026) 9/9 in 4:18";
    }

    private ProcessSharedResultService service(Clock clock, InMemoryStore configuredStore) {
        return new ProcessSharedResultService(
                new GridWordsShareParser(), new QuadWordsShareParser(), clock, ZoneId.of("Europe/Berlin"), configuredStore,
                configuredStore);
    }

    private InboundSharedMessage message(long messageId, long authorId, String content) {
        return message(messageId, authorId, content, List.of());
    }

    private InboundSharedMessage message(
            long messageId, long authorId, String content, List<AttachmentMetadata> attachments) {
        return new InboundSharedMessage(11L, 12L, messageId, authorId, "Player", content, attachments, RECEIVED_AT);
    }

    private String gridWords(int day, int attempts) {
        return "GridWords (" + day + ". Juli 2026) " + attempts + "/6 in 1:25 🔥2\n"
                + String.join("\n", List.of("⬜⬜⬜⬜⬜", "🟨🟨🟨🟨🟨", "🟩🟩🟩🟩🟩", "⬜⬜⬜⬜⬜")
                .subList(0, attempts));
    }

    private String gridWordsUnsolved(int day) {
        return "GridWords (" + day + ". Juli 2026) X/6 in 6:42\n"
                + String.join("\n", List.of("⬜⬜⬜⬜⬜", "🟨🟨🟨🟨🟨", "🟩🟩🟩🟩🟩", "⬜⬜⬜⬜⬜", "🟨🟨🟨🟨🟨", "🟩🟩🟩🟩🟩"));
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
            StoredPlayer stored = new StoredPlayer(request.discordUserId(), request.displayName(), request.active(),
                    request.administrator(), Instant.EPOCH, Instant.EPOCH);
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
                if (existing.guildId() != registration.guildId() || existing.channelId() != registration.channelId()
                        || existing.authorPlayerId() != registration.authorPlayerId()
                        || !existing.rawMessageContent().equals(registration.rawMessageContent())
                        || !existing.attachments().equals(registration.attachments())) {
                    throw new SubmissionConflictException("conflicting source message reuse");
                }
                return existing;
            }
            StoredSubmission stored = new StoredSubmission(registration.sourceMessageId(), registration.guildId(),
                    registration.channelId(), registration.authorPlayerId(), registration.rawMessageContent(),
                    SubmissionState.RECEIVED, Optional.empty(), registration.attachments(), Optional.empty(), Optional.empty(),
                    registration.receivedAt(), registration.receivedAt());
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
            if (submission.state() != SubmissionState.RECEIVED && submission.state() != SubmissionState.VALIDATED) {
                throw new SubmissionConflictException("state does not allow result");
            }
            ResultKey key = ResultKey.of(request.result());
            StoredResult previous = results.get(key);
            long resultId = previous == null ? nextResultId++ : previous.id();
            results.put(key, new StoredResult(resultId, request.result()));
            StoredSubmission stored = with(submission, SubmissionState.RESULT_STORED, Optional.of(resultId), Optional.empty());
            submissions.put(stored.sourceMessageId(), stored);
            return stored;
        }

        @Override
        public StoredSubmission reject(RejectedSubmission request) {
            StoredSubmission submission = required(request.sourceMessageId());
            if (submission.state() == SubmissionState.PARSE_REJECTED
                    && submission.parserErrorCode().filter(request.errorCode()::equals).isPresent()) {
                return submission;
            }
            if (submission.state() != SubmissionState.RECEIVED && submission.state() != SubmissionState.VALIDATED) {
                throw new SubmissionConflictException("state does not allow rejection");
            }
            StoredSubmission rejected = with(submission, SubmissionState.PARSE_REJECTED, Optional.empty(),
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
        public boolean transition(long sourceMessageId, SubmissionState expectedState, SubmissionState targetState) {
            StoredSubmission submission = required(sourceMessageId);
            if (submission.state() != expectedState) {
                return false;
            }
            submissions.put(sourceMessageId, with(submission, targetState, submission.gameResultId(), submission.parserErrorCode()));
            return true;
        }

        private StoredSubmission required(long sourceMessageId) {
            StoredSubmission submission = submissions.get(sourceMessageId);
            if (submission == null) {
                throw new IllegalStateException("submission is missing");
            }
            return submission;
        }

        private StoredSubmission with(
                StoredSubmission submission,
                SubmissionState state,
                Optional<Long> resultId,
                Optional<String> errorCode) {
            return new StoredSubmission(submission.sourceMessageId(), submission.guildId(), submission.channelId(),
                    submission.authorPlayerId(), submission.rawMessageContent(), state, resultId, submission.attachments(),
                    errorCode, Optional.empty(), submission.receivedAt(), RECEIVED_AT);
        }
    }

    private record ResultKey(long playerId, de.venomenon.gridwordsbot.domain.model.GameType gameType, LocalDate gameDate) {
        private static ResultKey of(GameResultStore.GameResultUpsert result) {
            return new ResultKey(result.playerId(), result.parsedResult().gameType(), result.parsedResult().gameDate());
        }
    }

    private record StoredResult(long id, GameResultStore.GameResultUpsert result) {
    }
}
