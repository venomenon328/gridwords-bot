package de.venomenon.gridwordsbot.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.application.submission.ProcessSharedResultService;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.out.SubmissionConflictException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import liquibase.integration.spring.SpringLiquibase;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresPersistenceAdapterIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private PostgresPersistenceAdapter adapter;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private final Instant now = Instant.parse("2026-07-29T08:00:00Z");

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        adapter = new PostgresPersistenceAdapter(jdbc, Clock.fixed(now, ZoneOffset.UTC));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void storesAnIdempotentSubmissionAndKeepsCanonicalMessageDuringCorrection() {
        PlayerStore.StoredPlayer player = adapter.upsert(new PlayerStore.PlayerUpsert(100L, "Tobias", true, true));
        assertEquals(100L, player.discordUserId());
        SubmissionStore.SubmissionRegistration registration = new SubmissionStore.SubmissionRegistration(
                900L, 200L, 300L, 100L, "share", List.of(new SubmissionStore.AttachmentSnapshot(0, "grid.png", Optional.of("image/png"), 12L)), now);
        assertEquals(SubmissionStore.SubmissionState.RECEIVED, adapter.register(registration).state());
        assertEquals(SubmissionStore.SubmissionState.RECEIVED, adapter.register(registration).state());
        assertThrows(SubmissionConflictException.class, () -> adapter.register(new SubmissionStore.SubmissionRegistration(900L, 200L, 300L, 100L, "different", List.of(), now)));

        GameResultStore.GameResultUpsert initial = result(3, "first");
        SubmissionStore.StoredSubmission stored = adapter.storeResult(new SubmissionStore.ResultStorage(900L, initial));
        assertEquals(SubmissionStore.SubmissionState.RESULT_STORED, stored.state());
        long id = stored.gameResultId().orElseThrow();
        adapter.setCanonicalMessageId(id, 777L);
        GameResultStore.StoredGameResult corrected = adapter.upsert(result(2, "correction"));
        assertEquals(id, corrected.id());
        assertEquals(777L, corrected.canonicalMessageId().orElseThrow());
        assertEquals(2, ((ShareOutcome.Solved) corrected.parsedResult().outcome()).attemptsUsed());
    }

    @Test
    void enforcesStateExpectationAndDatabaseConstraints() {
        adapter.upsert(new PlayerStore.PlayerUpsert(101L, "Georgia", true, false));
        adapter.register(new SubmissionStore.SubmissionRegistration(901L, 200L, 300L, 101L, "share", List.of(), now));
        assertFalse(adapter.transition(901L, SubmissionStore.SubmissionState.RESULT_STORED, SubmissionStore.SubmissionState.COMPLETED));
        assertTrue(adapter.transition(901L, SubmissionStore.SubmissionState.RECEIVED, SubmissionStore.SubmissionState.VALIDATED));
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO daily_status_message (guild_id, channel_id, game_date, created_at, updated_at) VALUES (-1, 1, CURRENT_DATE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"));
    }

    @Test
    void rollsBackResultAndSubmissionStateInAnActiveTransaction() {
        adapter.upsert(new PlayerStore.PlayerUpsert(102L, "Rollback", true, false));
        adapter.register(new SubmissionStore.SubmissionRegistration(902L, 200L, 300L, 102L, "share", List.of(), now));
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            adapter.storeResult(new SubmissionStore.ResultStorage(902L, resultFor(102L, 4, "rollback")));
            throw new IllegalStateException("force rollback");
        }));
        assertEquals(SubmissionStore.SubmissionState.RECEIVED, adapter.findBySourceMessageId(902L).orElseThrow().state());
        assertTrue(adapter.find(102L, GameType.GRIDWORDS, LocalDate.of(2026, 7, 29)).isEmpty());
    }
    @Test
    void keepsProspectiveLeaveActiveTodayAndSynchronizesItAfterMidnight() {
        jdbc.update("DELETE FROM player_participation_period");
        long playerId = 189L;
        PlayerStore.ProfileUpdate profile = new PlayerStore.ProfileUpdate(playerId, "Leaving", false);
        adapter.activate(new PlayerStore.ParticipationChange(profile, LocalDate.of(2026, 7, 29)));
        assertTrue(adapter.deactivate(new PlayerStore.ParticipationChange(profile, LocalDate.of(2026, 7, 30))).active());

        PostgresPersistenceAdapter tomorrow = new PostgresPersistenceAdapter(jdbc,
                Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC));
        assertFalse(tomorrow.findByDiscordUserId(playerId).orElseThrow().active());
        assertEquals(LocalDate.of(2026, 7, 30), tomorrow.findParticipationPeriods().getFirst().inactiveFrom());
    }
    @Test
    void returnsOnlyActiveOptedInPlayersWithTheirMissingGames() {
        jdbc.update("DELETE FROM player_participation_period");
        long missingQuad = 190L;
        long complete = 191L;
        long unsolvedComplete = 192L;
        adapter.upsert(new PlayerStore.PlayerUpsert(missingQuad, "Missing Quad", true, false));
        adapter.upsert(new PlayerStore.PlayerUpsert(complete, "Complete", true, false));
        adapter.upsert(new PlayerStore.PlayerUpsert(unsolvedComplete, "Unsolved Complete", true, false));
        adapter.setReminderOptIn(new PlayerStore.ProfileUpdate(missingQuad, "Missing Quad", false), true);
        adapter.setReminderOptIn(new PlayerStore.ProfileUpdate(complete, "Complete", false), true);
        adapter.setReminderOptIn(new PlayerStore.ProfileUpdate(unsolvedComplete, "Unsolved Complete", false), true);
        registerSubmission(1900L, missingQuad);
        store(1900L, resultFor(missingQuad, 3, "grid"), List.of());
        registerSubmission(1901L, complete);
        store(1901L, resultFor(complete, 3, "grid"), List.of());
        registerSubmission(1902L, complete);
        store(1902L, quadResultFor(complete, true, "quad"), List.of());
        registerSubmission(1903L, unsolvedComplete);
        store(1903L, unsolvedGridResultFor(unsolvedComplete, "grid X/6"), List.of());
        registerSubmission(1904L, unsolvedComplete);
        store(1904L, quadResultFor(unsolvedComplete, false, "quad X/9"), List.of());

        assertEquals(List.of(GameType.QUADWORDS), adapter.findReminderCandidates(LocalDate.of(2026, 7, 29)).getFirst().missingGames());
        var candidates = adapter.findReminderCandidates(LocalDate.of(2026, 7, 29));
        assertEquals(1, candidates.size());
        assertEquals(missingQuad, candidates.getFirst().discordUserId());
    }
    private void registerSubmission(long sourceMessageId, long playerId) {
        registerSubmission(sourceMessageId, playerId, now);
    }

    private void registerSubmission(long sourceMessageId, long playerId, Instant receivedAt) {
        adapter.register(new SubmissionStore.SubmissionRegistration(
                sourceMessageId, 200L, 300L, playerId, "share " + sourceMessageId, List.of(), receivedAt));
    }

    private SubmissionStore.StoredSubmission store(long sourceMessageId, GameResultStore.GameResultUpsert result) {
        return store(sourceMessageId, result, List.of());
    }
    private SubmissionStore.StoredSubmission store(
            long sourceMessageId,
            GameResultStore.GameResultUpsert result,
            List<Long> ignoredLegacyArgument) {
        return transactions.execute(status -> adapter.storeResult(
                new SubmissionStore.ResultStorage(sourceMessageId, result)));
    }

    private GameResultStore.GameResultUpsert quadResult(long playerId, int attempts, String text, String parserVersion) {
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.QUADWORDS,
                LocalDate.of(2026, 7, 29),
                new ShareOutcome.Solved(attempts, 9),
                Duration.ofSeconds(42),
                OptionalInt.empty(),
                Optional.empty(),
                Optional.of(quadBoards(attempts)));
        return new GameResultStore.GameResultUpsert(playerId, parsed, text, parserVersion);
    }

    private GameResultStore.GameResultUpsert quadResultFor(long playerId, boolean solved, String text) {
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.QUADWORDS,
                LocalDate.of(2026, 7, 29),
                solved ? new ShareOutcome.Solved(4, 9) : new ShareOutcome.Unsolved(9),
                Duration.ofSeconds(42),
                OptionalInt.empty(),
                Optional.empty(),
                Optional.of(quadBoards(solved ? 4 : 9)));
        return new GameResultStore.GameResultUpsert(playerId, parsed, text, "v1");
    }
    private GameResultStore.GameResultUpsert result(int attempts, String text) {
        NormalizedBoard board = board(attempts);
        ParsedGameResult parsed = new ParsedGameResult(GameType.GRIDWORDS, LocalDate.of(2026, 7, 29), new ShareOutcome.Solved(attempts, 6), Duration.ofSeconds(42), OptionalInt.empty(), Optional.of(board));
        return resultFor(100L, attempts, text);
    }

    private GameResultStore.GameResultUpsert unsolvedGridResultFor(long playerId, String text) {
        ParsedGameResult parsed = new ParsedGameResult(GameType.GRIDWORDS, LocalDate.of(2026, 7, 29),
                new ShareOutcome.Unsolved(6), Duration.ofSeconds(42), OptionalInt.empty(),
                Optional.of(board(6)));
        return new GameResultStore.GameResultUpsert(playerId, parsed, text, "v1");
    }
    private GameResultStore.GameResultUpsert resultFor(long playerId, int attempts, String text) {
        NormalizedBoard board = board(attempts);
        ParsedGameResult parsed = new ParsedGameResult(GameType.GRIDWORDS, LocalDate.of(2026, 7, 29), new ShareOutcome.Solved(attempts, 6), Duration.ofSeconds(42), OptionalInt.empty(), Optional.of(board));
        return new GameResultStore.GameResultUpsert(playerId, parsed, text, "v1");
    }

    private QuadWordsBoards quadBoards(int rows) {
        String blank = new String(Character.toChars(0x2B1C));
        String yellow = new String(Character.toChars(0x1F7E8));
        String green = new String(Character.toChars(0x1F7E9));
        return new QuadWordsBoards(
                new QuadWordsBoard(Collections.nCopies(rows, blank + yellow + green + blank + yellow)),
                new QuadWordsBoard(Collections.nCopies(rows, yellow + green + blank + yellow + green)),
                new QuadWordsBoard(Collections.nCopies(rows, green + blank + yellow + green + blank)),
                new QuadWordsBoard(Collections.nCopies(rows, blank + green + yellow + blank + green)));
    }

    private NormalizedBoard board(int attempts) {
        String white = new String(Character.toChars(0x2B1C)).repeat(5);
        String yellow = new String(Character.toChars(0x1F7E8)).repeat(5);
        String green = new String(Character.toChars(0x1F7E9)).repeat(5);
        return new NormalizedBoard(List.of(white, yellow, green, white, white, white).subList(0, attempts));
    }

    @Test
    void rejectsAResultForAPlayerOtherThanTheSubmissionAuthor() {
        adapter.upsert(new PlayerStore.PlayerUpsert(103L, "Author", true, false));
        adapter.upsert(new PlayerStore.PlayerUpsert(104L, "Other", true, false));
        adapter.register(new SubmissionStore.SubmissionRegistration(903L, 200L, 300L, 103L, "share", List.of(), now));
        assertThrows(SubmissionConflictException.class,
                () -> adapter.storeResult(new SubmissionStore.ResultStorage(903L, resultFor(104L, 4, "wrong player"))));
        assertEquals(SubmissionStore.SubmissionState.RECEIVED, adapter.findBySourceMessageId(903L).orElseThrow().state());
    }
    @Test
    void upsertsAPlayerIdempotently() {
        adapter.upsert(new PlayerStore.PlayerUpsert(105L, "Old name", true, false));
        PlayerStore.StoredPlayer updated = adapter.upsert(new PlayerStore.PlayerUpsert(105L, "New name", false, true));

        assertEquals("New name", updated.displayName());
        assertFalse(updated.active());
        assertTrue(updated.administrator());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM player WHERE discord_user_id = 105", Integer.class));
    }

    @Test
    void roundTripsAttachmentsAndBothGameTypes() {
        adapter.upsert(new PlayerStore.PlayerUpsert(106L, "Attachment", true, false));
        List<SubmissionStore.AttachmentSnapshot> attachments = List.of(
                new SubmissionStore.AttachmentSnapshot(0, "first.png", Optional.of("image/png"), 12L),
                new SubmissionStore.AttachmentSnapshot(1, "second.jpg", Optional.empty(), 34L));
        adapter.register(new SubmissionStore.SubmissionRegistration(906L, 200L, 300L, 106L, "share", attachments, now));
        assertEquals(attachments, adapter.findBySourceMessageId(906L).orElseThrow().attachments());

        GameResultStore.GameResultUpsert grid = resultFor(106L, 4, "grid");
        GameResultStore.GameResultUpsert quad = new GameResultStore.GameResultUpsert(106L,
                new ParsedGameResult(GameType.QUADWORDS, LocalDate.of(2026, 7, 28), new ShareOutcome.Unsolved(9),
                        Duration.ofSeconds(587), OptionalInt.of(8), Optional.empty(), Optional.of(quadBoards(9))),
                "quad", "quadwords-image-v2");
        assertEquals(grid.parsedResult(), adapter.upsert(grid).parsedResult());
        GameResultStore.StoredGameResult storedQuad = adapter.upsert(quad);
        assertEquals(quad.parsedResult(), storedQuad.parsedResult());
        assertEquals("quadwords-image-v2", storedQuad.parserVersion());
        assertEquals(quad.parsedResult().quadWordsBoards(),
                adapter.find(106L, GameType.QUADWORDS, LocalDate.of(2026, 7, 28))
                        .orElseThrow().parsedResult().quadWordsBoards());
    }

    @Test
    void keepsQuadWordsReplayIdempotentAndPersistsCorrectionsInCanonicalBoardOrder() {
        adapter.upsert(new PlayerStore.PlayerUpsert(114L, "QuadWords", true, false));
        registerSubmission(914L, 114L);

        GameResultStore.GameResultUpsert initial = quadResult(114L, 4, "initial", "quadwords-image-v2");
        SubmissionStore.StoredSubmission stored = transactions.execute(status -> adapter.storeResult(
                new SubmissionStore.ResultStorage(914L, initial)));
        long resultId = stored.gameResultId().orElseThrow();
        Integer replayVersion = jdbc.queryForObject(
                "SELECT version FROM submission WHERE source_message_id = 914", Integer.class);

        SubmissionStore.StoredSubmission replay = transactions.execute(status -> adapter.storeResult(
                new SubmissionStore.ResultStorage(914L, initial)));
        assertEquals(stored, replay);
        assertEquals(replayVersion, jdbc.queryForObject(
                "SELECT version FROM submission WHERE source_message_id = 914", Integer.class));

        registerSubmission(915L, 114L);
        GameResultStore.GameResultUpsert correction = quadResult(114L, 5, "correction", "quadwords-image-v3");
        SubmissionStore.StoredSubmission correctedSubmission = transactions.execute(status -> adapter.storeResult(
                new SubmissionStore.ResultStorage(915L, correction)));
        GameResultStore.StoredGameResult corrected = adapter.find(114L, GameType.QUADWORDS,
                LocalDate.of(2026, 7, 29)).orElseThrow();

        assertEquals(resultId, correctedSubmission.gameResultId().orElseThrow());
        assertEquals(resultId, corrected.id());
        assertEquals(correction.parsedResult().quadWordsBoards(), corrected.parsedResult().quadWordsBoards());
        assertEquals("quadwords-image-v3", corrected.parserVersion());
        assertEquals(5, ((ShareOutcome.Solved) corrected.parsedResult().outcome()).attemptsUsed());
    }

    @Test
    void recordsQuadWordsRejectionWithoutCreatingAGameResult() {
        adapter.upsert(new PlayerStore.PlayerUpsert(115L, "Rejected QuadWords", true, false));
        registerSubmission(916L, 115L);

        SubmissionStore.StoredSubmission rejected = transactions.execute(status -> adapter.reject(
                new SubmissionStore.RejectedSubmission(916L, "UNCERTAIN_IMAGE_COLOUR")));

        assertEquals(SubmissionStore.SubmissionState.PARSE_REJECTED, rejected.state());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM game_result WHERE player_id = 115 AND game_type = 'QUADWORDS'", Integer.class));
    }

    @Test
    void replaysEquivalentStoredResultsWithoutWritesAndRejectsContradictions() {
        adapter.upsert(new PlayerStore.PlayerUpsert(107L, "Replay", true, false));
        adapter.register(new SubmissionStore.SubmissionRegistration(907L, 200L, 300L, 107L, "share", List.of(), now));
        GameResultStore.GameResultUpsert original = resultFor(107L, 4, "original");
        SubmissionStore.StoredSubmission stored = transactions.execute(status -> adapter.storeResult(
                new SubmissionStore.ResultStorage(907L, original)));
        long resultId = stored.gameResultId().orElseThrow();
        Integer version = jdbc.queryForObject("SELECT version FROM submission WHERE source_message_id = 907", Integer.class);

        SubmissionStore.StoredSubmission replay = transactions.execute(status -> adapter.storeResult(
                new SubmissionStore.ResultStorage(907L, original)));
        assertEquals(stored, replay);
        assertEquals(version, jdbc.queryForObject("SELECT version FROM submission WHERE source_message_id = 907", Integer.class));
        assertThrows(SubmissionConflictException.class, () -> transactions.execute(status -> adapter.storeResult(
                new SubmissionStore.ResultStorage(907L, resultFor(107L, 3, "contradiction")))));
        assertEquals("original", jdbc.queryForObject("SELECT raw_share_text FROM game_result WHERE id = ?", String.class, resultId));
    }

    @Test
    void handlesConcurrentRegistrationsUpsertsAndTransitions() throws Exception {
        adapter.upsert(new PlayerStore.PlayerUpsert(108L, "Concurrent", true, false));
        SubmissionStore.SubmissionRegistration registration = new SubmissionStore.SubmissionRegistration(
                908L, 200L, 300L, 108L, "share", List.of(), now);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SubmissionStore.StoredSubmission> first = executor.submit(
                    () -> transactions.execute(status -> adapter.register(registration)));
            Future<SubmissionStore.StoredSubmission> second = executor.submit(
                    () -> transactions.execute(status -> adapter.register(registration)));
            assertEquals(908L, first.get().sourceMessageId());
            assertEquals(908L, second.get().sourceMessageId());

            GameResultStore.GameResultUpsert result = resultFor(108L, 4, "concurrent");
            Future<GameResultStore.StoredGameResult> upsertOne = executor.submit(() -> adapter.upsert(result));
            Future<GameResultStore.StoredGameResult> upsertTwo = executor.submit(() -> adapter.upsert(result));
            assertEquals(upsertOne.get().id(), upsertTwo.get().id());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM game_result WHERE player_id = 108", Integer.class));

            Future<Boolean> transitionOne = executor.submit(() -> adapter.transition(908L,
                    SubmissionStore.SubmissionState.RECEIVED, SubmissionStore.SubmissionState.VALIDATED));
            Future<Boolean> transitionTwo = executor.submit(() -> adapter.transition(908L,
                    SubmissionStore.SubmissionState.RECEIVED, SubmissionStore.SubmissionState.VALIDATED));
            assertTrue(transitionOne.get() ^ transitionTwo.get());
        }
    }

    @Test
    void storesACompleteValidApplicationFlowAtomically() {
        adapter.upsert(new PlayerStore.PlayerUpsert(112L, "Application", true, true));
        adapter.upsert(new PlayerStore.PlayerUpsert(113L, "Second", true, false));
        ProcessSharedResultService service = new ProcessSharedResultService(
                new GridWordsShareParser(), new QuadWordsShareParser(), Clock.fixed(now, ZoneOffset.UTC),
                ZoneId.of("Europe/Berlin"), adapter, adapter);
        InboundSharedMessage message = new InboundSharedMessage(
                200L,
                300L,
                910L,
                112L,
                "Application",
                "GridWords (29. Juli 2026) 3/6 in 1:25\n⬜⬜⬜⬜⬜\n🟨🟨🟨🟨🟨\n🟩🟩🟩🟩🟩",
                List.of(),
                now);

        ProcessingResult outcome = transactions.execute(status -> service.process(message));

        assertEquals(new ProcessingResult.Accepted(), outcome);
        SubmissionStore.StoredSubmission submission = adapter.findBySourceMessageId(910L).orElseThrow();
        assertEquals(SubmissionStore.SubmissionState.RESULT_STORED, submission.state());
        assertTrue(submission.gameResultId().isPresent());
        assertEquals(3, ((ShareOutcome.Solved) adapter.find(112L, GameType.GRIDWORDS, LocalDate.of(2026, 7, 29))
                .orElseThrow().parsedResult().outcome()).attemptsUsed());
    }

    @Test
    void claimsCanonicalPublicationAndCompletesOnlyForTheExpectedSubmissionResult() {
        adapter.upsert(new PlayerStore.PlayerUpsert(120L, "Canonical", true, false));
        adapter.register(new SubmissionStore.SubmissionRegistration(920L, 200L, 300L, 120L, "share", List.of(), now));
        SubmissionStore.StoredSubmission stored = adapter.storeResult(
                new SubmissionStore.ResultStorage(920L, resultFor(120L, 3, "canonical")));
        long resultId = stored.gameResultId().orElseThrow();

        GameResultStore.PublicationClaim claim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertTrue(adapter.claimCanonicalPublication(resultId, now.plusSeconds(60)).isEmpty());
        assertTrue(adapter.completeCanonicalPublication(920L, resultId, 1234L, claim.token()));

        assertEquals(1234L, adapter.findById(resultId).orElseThrow().canonicalMessageId().orElseThrow());
        assertEquals(
                SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                adapter.findBySourceMessageId(920L).orElseThrow().state());
        assertThrows(
                SubmissionConflictException.class,
                () -> adapter.completeCanonicalPublication(920L, resultId + 1, 1235L, java.util.UUID.randomUUID()));
    }

    @Test
    void rejectsAStalePublisherAfterItsLeaseWasTakenOver() {
        adapter.upsert(new PlayerStore.PlayerUpsert(121L, "Stale owner", true, false));
        adapter.register(new SubmissionStore.SubmissionRegistration(921L, 200L, 300L, 121L, "share", List.of(), now));
        long resultId = adapter.storeResult(new SubmissionStore.ResultStorage(921L, resultFor(121L, 3, "stale")))
                .gameResultId()
                .orElseThrow();

        GameResultStore.PublicationClaim stale = adapter.claimCanonicalPublication(resultId, now.minusSeconds(1))
                .orElseThrow();
        GameResultStore.PublicationClaim current = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertFalse(stale.token().equals(current.token()));

        assertThrows(
                SubmissionConflictException.class,
                () -> adapter.completeCanonicalPublication(921L, resultId, 1234L, stale.token()));
        assertTrue(adapter.findById(resultId).orElseThrow().canonicalMessageId().isEmpty());
        assertEquals(SubmissionStore.SubmissionState.RESULT_STORED,
                adapter.findBySourceMessageId(921L).orElseThrow().state());

        assertTrue(adapter.completeCanonicalPublication(921L, resultId, 1235L, current.token()));
        assertEquals(1235L, adapter.findById(resultId).orElseThrow().canonicalMessageId().orElseThrow());
    }

    @Test
    void grantsExactlyOneCanonicalClaimToConcurrentWorkers() throws Exception {
        adapter.upsert(new PlayerStore.PlayerUpsert(122L, "Concurrent canonical", true, false));
        adapter.register(new SubmissionStore.SubmissionRegistration(922L, 200L, 300L, 122L, "share", List.of(), now));
        long resultId = adapter.storeResult(new SubmissionStore.ResultStorage(922L, resultFor(122L, 3, "concurrent canonical")))
                .gameResultId()
                .orElseThrow();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Optional<GameResultStore.PublicationClaim>> first = executor.submit(
                    () -> adapter.claimCanonicalPublication(resultId, now.plusSeconds(60)));
            Future<Optional<GameResultStore.PublicationClaim>> second = executor.submit(
                    () -> adapter.claimCanonicalPublication(resultId, now.plusSeconds(60)));

            assertEquals(1, List.of(first.get(), second.get()).stream().filter(Optional::isPresent).count());
        }
    }
    @Test
    void replacesTheCanonicalMessageIdForALostMessageCorrection() {
        adapter.upsert(new PlayerStore.PlayerUpsert(123L, "Lost message", true, false));
        adapter.register(new SubmissionStore.SubmissionRegistration(923L, 200L, 300L, 123L, "first", List.of(), now));
        long resultId = adapter.storeResult(new SubmissionStore.ResultStorage(923L, resultFor(123L, 3, "first")))
                .gameResultId()
                .orElseThrow();
        adapter.setCanonicalMessageId(resultId, 1234L);
        adapter.register(new SubmissionStore.SubmissionRegistration(924L, 200L, 300L, 123L, "correction", List.of(), now));
        SubmissionStore.StoredSubmission correction = adapter.storeResult(
                new SubmissionStore.ResultStorage(924L, resultFor(123L, 2, "correction")));
        assertEquals(resultId, correction.gameResultId().orElseThrow());

        GameResultStore.PublicationClaim claim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertTrue(adapter.completeCanonicalPublication(924L, resultId, 5678L, claim.token()));

        assertEquals(5678L, adapter.findById(resultId).orElseThrow().canonicalMessageId().orElseThrow());
        assertEquals(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                adapter.findBySourceMessageId(924L).orElseThrow().state());
    }

    @Test
    void keepsTheNewerCorrectionCanonicalWhenAnOlderFailedSubmissionRetriesLater() {
        long playerId = 150L;
        long firstSource = 950L;
        long correctionSource = 951L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Supersession", true, false));
        registerSubmission(firstSource, playerId, now);
        SubmissionStore.StoredSubmission first = store(firstSource, resultFor(playerId, 4, "first"), List.of());
        long resultId = first.gameResultId().orElseThrow();
        adapter.markRetryableFailure(firstSource, "Discord unavailable");

        registerSubmission(correctionSource, playerId, now.plusSeconds(1));
        SubmissionStore.StoredSubmission correction = store(
                correctionSource, resultFor(playerId, 2, "correction"), List.of());

        assertEquals(resultId, correction.gameResultId().orElseThrow());
        assertEquals(SubmissionStore.SubmissionState.SUPERSEDED,
                adapter.findBySourceMessageId(firstSource).orElseThrow().state());
        assertEquals(SubmissionStore.CanonicalPublicationPreparation.SUPERSEDED,
                transactions.execute(status -> adapter.prepareCanonicalPublication(firstSource, resultId)));
        assertFalse(adapter.findGridWordsAwaitingCanonicalPublication().stream()
                .anyMatch(submission -> submission.sourceMessageId() == firstSource));

        GameResultStore.PublicationClaim correctionClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeCanonicalPublication(
                correctionSource, resultId, 5678L, correctionClaim.token())));

        assertEquals(5678L, adapter.findById(resultId).orElseThrow().canonicalMessageId().orElseThrow());
        assertEquals(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                adapter.findBySourceMessageId(correctionSource).orElseThrow().state());
        assertEquals(SubmissionStore.SubmissionState.SUPERSEDED,
                adapter.findBySourceMessageId(firstSource).orElseThrow().state());
    }

    @Test
    void doesNotOverwriteAPublishedNewerCorrectionWhenAnOlderSourceIsStoredLate() {
        long playerId = 152L;
        long olderSource = 954L;
        long newerSource = 955L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Late source", true, false));
        registerSubmission(olderSource, playerId, now);
        registerSubmission(newerSource, playerId, now.plusSeconds(1));
        SubmissionStore.StoredSubmission newer = store(newerSource, resultFor(playerId, 2, "newer"), List.of());
        long resultId = newer.gameResultId().orElseThrow();
        GameResultStore.PublicationClaim newerClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeCanonicalPublication(
                newerSource, resultId, 5680L, newerClaim.token())));

        SubmissionStore.StoredSubmission older = store(olderSource, resultFor(playerId, 4, "older"), List.of());

        assertEquals(SubmissionStore.SubmissionState.SUPERSEDED, older.state());
        assertEquals(2, ((ShareOutcome.Solved) adapter.findById(resultId).orElseThrow().parsedResult().outcome()).attemptsUsed());
        assertEquals(5680L, adapter.findById(resultId).orElseThrow().canonicalMessageId().orElseThrow());
        assertEquals(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                adapter.findBySourceMessageId(newerSource).orElseThrow().state());
    }
    @Test
    void rejectsAnInFlightOlderPublisherAfterANewerCorrectionSupersedesIt() {
        long playerId = 151L;
        long firstSource = 952L;
        long correctionSource = 953L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Supersession race", true, false));
        registerSubmission(firstSource, playerId, now);
        long resultId = store(firstSource, resultFor(playerId, 4, "first race"), List.of())
                .gameResultId()
                .orElseThrow();
        GameResultStore.PublicationClaim oldClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();

        registerSubmission(correctionSource, playerId, now.plusSeconds(1));
        store(correctionSource, resultFor(playerId, 2, "correction race"), List.of());

        assertEquals(SubmissionStore.SubmissionState.SUPERSEDED,
                adapter.findBySourceMessageId(firstSource).orElseThrow().state());
        assertThrows(SubmissionConflictException.class, () -> transactions.execute(status ->
                adapter.completeCanonicalPublication(firstSource, resultId, 5678L, oldClaim.token())));
        assertTrue(adapter.findById(resultId).orElseThrow().canonicalMessageId().isEmpty());
        adapter.releaseCanonicalPublicationClaim(resultId, oldClaim.token());

        GameResultStore.PublicationClaim correctionClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeCanonicalPublication(
                correctionSource, resultId, 5679L, correctionClaim.token())));
        assertEquals(5679L, adapter.findById(resultId).orElseThrow().canonicalMessageId().orElseThrow());
    }
    @Test
    void refreshFenceSelectsOnlyTheNewestPublishedCorrection() {
        long playerId = 153L;
        long olderSource = 956L;
        long newerSource = 957L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Refresh fence", true, false));
        registerSubmission(olderSource, playerId, now);
        long resultId = store(olderSource, resultFor(playerId, 4, "older refresh"), List.of())
                .gameResultId()
                .orElseThrow();
        GameResultStore.PublicationClaim staleClaim = adapter.claimCanonicalPublication(resultId, now.minusSeconds(1))
                .orElseThrow();
        adapter.markRetryableFailure(olderSource, "older Discord call is still returning");

        registerSubmission(newerSource, playerId, now.plusSeconds(1));
        store(newerSource, resultFor(playerId, 2, "newer refresh"), List.of());
        GameResultStore.PublicationClaim newerClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeCanonicalPublication(
                newerSource, resultId, 5681L, newerClaim.token())));

        SubmissionStore.CanonicalRefreshCandidate current = adapter.findCurrentCanonicalPublicationCandidate(resultId).orElseThrow();
        assertEquals(newerSource, current.submission().sourceMessageId());
        assertEquals(SubmissionStore.SubmissionState.SUPERSEDED,
                adapter.findBySourceMessageId(olderSource).orElseThrow().state());
        assertThrows(SubmissionConflictException.class, () -> transactions.execute(status ->
                adapter.completeCanonicalRefresh(olderSource, resultId, 9999L, staleClaim.token(), 0)));

        GameResultStore.PublicationClaim refreshClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertEquals(new SubmissionStore.CanonicalRefreshCompletion(false), transactions.execute(status ->
                adapter.completeCanonicalRefresh(newerSource, resultId, 5681L, refreshClaim.token(), 0)));
        assertEquals(5681L, adapter.findById(resultId).orElseThrow().canonicalMessageId().orElseThrow());
    }
    @Test
    void persistsRefreshReconciliationAcrossRestartAndRetainsANewerGeneration() {
        long playerId = 154L;
        long sourceMessageId = 958L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Durable refresh", true, false));
        registerSubmission(sourceMessageId, playerId, now);
        long resultId = store(sourceMessageId, resultFor(playerId, 2, "published refresh"), List.of())
                .gameResultId()
                .orElseThrow();
        GameResultStore.PublicationClaim publicationClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeCanonicalPublication(
                sourceMessageId, resultId, 5682L, publicationClaim.token())));

        adapter.requestCanonicalRefresh(resultId);
        SubmissionStore.CanonicalRefreshCandidate firstRefresh = adapter.findCanonicalRefreshCandidates().stream()
                .filter(candidate -> candidate.submission().sourceMessageId() == sourceMessageId)
                .findFirst()
                .orElseThrow();
        assertEquals(1, firstRefresh.refreshGeneration());

        GameResultStore.PublicationClaim firstClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        adapter.requestCanonicalRefresh(resultId);
        assertEquals(new SubmissionStore.CanonicalRefreshCompletion(true), transactions.execute(status ->
                adapter.completeCanonicalRefresh(sourceMessageId, resultId, 5682L, firstClaim.token(),
                        firstRefresh.refreshGeneration())));

        SubmissionStore.CanonicalRefreshCandidate secondRefresh = adapter.findCanonicalRefreshCandidates().stream()
                .filter(candidate -> candidate.submission().sourceMessageId() == sourceMessageId)
                .findFirst()
                .orElseThrow();
        assertEquals(2, secondRefresh.refreshGeneration());
        GameResultStore.PublicationClaim secondClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertEquals(new SubmissionStore.CanonicalRefreshCompletion(false), transactions.execute(status ->
                adapter.completeCanonicalRefresh(sourceMessageId, resultId, 5682L, secondClaim.token(),
                        secondRefresh.refreshGeneration())));
        assertTrue(adapter.findCanonicalRefreshCandidates().isEmpty());
    }
    @Test
    void reconstructsRetryableGridWordsSubmissionsForStartupRecovery() {
        adapter.upsert(new PlayerStore.PlayerUpsert(124L, "Retry", true, false));
        adapter.register(new SubmissionStore.SubmissionRegistration(925L, 200L, 300L, 124L, "retry", List.of(), now));
        adapter.storeResult(new SubmissionStore.ResultStorage(925L, resultFor(124L, 3, "retry")));
        adapter.markRetryableFailure(925L, "canonical publication failed");

        assertEquals(SubmissionStore.SubmissionState.FAILED_RETRYABLE,
                adapter.findBySourceMessageId(925L).orElseThrow().state());
        assertTrue(adapter.findGridWordsAwaitingCanonicalPublication().stream()
                .anyMatch(submission -> submission.sourceMessageId() == 925L));
    }

    @Test
    void persistsPublicationContextForTheActualGridWordsTransitionAndNotForACorrection() {
        jdbc.update("DELETE FROM player_participation_period");
        long tobias = 130L;
        long georgia = 131L;
                adapter.upsert(new PlayerStore.PlayerUpsert(tobias, "Context Tobias", true, false));
        adapter.upsert(new PlayerStore.PlayerUpsert(georgia, "Context Georgia", true, false));

        registerSubmission(930L, tobias);
        store(930L, quadResultFor(tobias, true, "tobias quad"));
        registerSubmission(931L, georgia);
        store(931L, resultFor(georgia, 3, "georgia grid"));
        registerSubmission(932L, georgia);
        store(932L, quadResultFor(georgia, true, "georgia quad"));
        registerSubmission(933L, tobias);

        SubmissionStore.StoredSubmission trigger = store(933L, resultFor(tobias, 3, "tobias grid"));

        assertTrue(trigger.publicationContext().personalCompleteEstablished());
        assertTrue(trigger.publicationContext().personalPerfectEstablished());
        assertTrue(trigger.publicationContext().sharedCompleteEstablished());
        assertTrue(trigger.publicationContext().sharedPerfectEstablished());

        registerSubmission(934L, tobias);
        SubmissionStore.StoredSubmission correction = store(
                934L, resultFor(tobias, 2, "tobias correction"));

        assertFalse(correction.publicationContext().personalCompleteEstablished());
        assertFalse(correction.publicationContext().personalPerfectEstablished());
        assertFalse(correction.publicationContext().sharedCompleteEstablished());
        assertFalse(correction.publicationContext().sharedPerfectEstablished());
    }

    @Test
    void publicationContextRequiresTwoGameMembershipAndTwoPlayersInTheIntersection() {
        jdbc.update("DELETE FROM player_participation_period");
        long bothPlayer = 132L;
        long gridOnlyPlayer = 133L;
        adapter.upsert(new PlayerStore.PlayerUpsert(bothPlayer, "Only member of B", false, false));
        adapter.upsert(new PlayerStore.PlayerUpsert(gridOnlyPlayer, "Grid only", false, false));

        registerSubmission(935L, bothPlayer);
        store(935L, quadResultFor(bothPlayer, true, "both player quad"));
        registerSubmission(936L, bothPlayer);
        SubmissionStore.StoredSubmission oneInIntersection = store(
                936L, resultFor(bothPlayer, 3, "both player grid"));

        assertTrue(oneInIntersection.publicationContext().personalCompleteEstablished());
        assertTrue(oneInIntersection.publicationContext().personalPerfectEstablished());
        assertFalse(oneInIntersection.publicationContext().sharedCompleteEstablished());
        assertFalse(oneInIntersection.publicationContext().sharedPerfectEstablished());

        SubmissionStore.StoredSubmission replay = store(
                936L, resultFor(bothPlayer, 3, "both player grid"));
        assertEquals(oneInIntersection.publicationContext(), replay.publicationContext());

        adapter.upsert(quadResultFor(gridOnlyPlayer, true, "stored without quad participation"));
        registerSubmission(937L, gridOnlyPlayer);
        SubmissionStore.StoredSubmission singleGame = store(
                937L, resultFor(gridOnlyPlayer, 3, "grid-only trigger"));

        assertFalse(singleGame.publicationContext().personalCompleteEstablished());
        assertFalse(singleGame.publicationContext().personalPerfectEstablished());
        assertFalse(singleGame.publicationContext().sharedCompleteEstablished());
        assertFalse(singleGame.publicationContext().sharedPerfectEstablished());
    }

    @Test
    void publishesImageBackedQuadWordsToCompletedAndKeepsTransitionContextOnlyOnTheFirstSubmission() {
        jdbc.update("DELETE FROM player_participation_period");
        long tobias = 180L;
        long georgia = 181L;
        long source = 980L;
        long correctionSource = 981L;
        long canonicalMessageId = 9800L;
                adapter.upsert(new PlayerStore.PlayerUpsert(tobias, "Quad context Tobias", true, false));
        adapter.upsert(new PlayerStore.PlayerUpsert(georgia, "Quad context Georgia", true, false));

        registerSubmission(982L, tobias);
        store(982L, resultFor(tobias, 3, "tobias grid"));
        registerSubmission(983L, georgia);
        store(983L, resultFor(georgia, 3, "georgia grid"));
        registerSubmission(984L, georgia);
        store(984L, quadResult(georgia, 4, "georgia quad", "quadwords-image-v2"));

        registerSubmission(source, tobias);
        SubmissionStore.StoredSubmission first = store(
                source, quadResult(tobias, 4, "tobias quad", "quadwords-image-v2"));
        long resultId = first.gameResultId().orElseThrow();
        assertTrue(first.publicationContext().personalCompleteEstablished());
        assertTrue(first.publicationContext().personalPerfectEstablished());
        assertTrue(first.publicationContext().sharedCompleteEstablished());
        assertTrue(first.publicationContext().sharedPerfectEstablished());

        GameResultStore.PublicationClaim firstClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        transactions.execute(status -> adapter.beginCanonicalDelivery(source, resultId, firstClaim.token()));
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeCanonicalPublication(
                source, resultId, canonicalMessageId, firstClaim.token())));
        SubmissionStore.SourceDeletionClaim firstDelete = transactions.execute(status ->
                adapter.claimOriginalSourceDeletion(source, now.plusSeconds(60)).orElseThrow());
        assertEquals(Boolean.TRUE, transactions.execute(status ->
                adapter.recordOriginalSourceDeleted(source, firstDelete.token())));
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeOriginalSourceDeletion(source)));
        assertEquals(SubmissionStore.SubmissionState.COMPLETED,
                adapter.findBySourceMessageId(source).orElseThrow().state());

        registerSubmission(correctionSource, tobias, now.plusSeconds(1));
        SubmissionStore.StoredSubmission correction = store(correctionSource,
                quadResult(tobias, 3, "tobias quad correction", "quadwords-image-v2"));
        assertEquals(resultId, correction.gameResultId().orElseThrow());
        assertFalse(correction.publicationContext().personalCompleteEstablished());
        assertFalse(correction.publicationContext().personalPerfectEstablished());
        assertFalse(correction.publicationContext().sharedCompleteEstablished());
        assertFalse(correction.publicationContext().sharedPerfectEstablished());
        assertEquals(SubmissionStore.CanonicalPublicationPreparation.PUBLISHABLE,
                transactions.execute(status -> adapter.prepareCanonicalPublication(correctionSource, resultId)));

        GameResultStore.PublicationClaim correctionClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        transactions.execute(status -> adapter.beginCanonicalDelivery(correctionSource, resultId, correctionClaim.token()));
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeCanonicalPublication(
                correctionSource, resultId, canonicalMessageId, correctionClaim.token())));
        assertEquals(canonicalMessageId, adapter.findById(resultId).orElseThrow().canonicalMessageId().orElseThrow());
        SubmissionStore.SourceDeletionClaim correctionDelete = transactions.execute(status ->
                adapter.claimOriginalSourceDeletion(correctionSource, now.plusSeconds(60)).orElseThrow());
        assertEquals(Boolean.TRUE, transactions.execute(status ->
                adapter.recordOriginalSourceDeleted(correctionSource, correctionDelete.token())));
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeOriginalSourceDeletion(correctionSource)));
        assertEquals(SubmissionStore.SubmissionState.COMPLETED,
                adapter.findBySourceMessageId(correctionSource).orElseThrow().state());
    }

    @Test
    void excludesBoardlessLegacyQuadWordsAndPermanentDeleteFailuresFromEveryRecoverySelector() {
        long legacyPlayer = 182L;
        long legacySource = 985L;
        adapter.upsert(new PlayerStore.PlayerUpsert(legacyPlayer, "Legacy quad", true, false));
        registerSubmission(legacySource, legacyPlayer);
        long legacyResultId = jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts,
                    duration_seconds, gridgames_streak, normalized_board, raw_share_text,
                    parser_version, canonical_message_id, created_at, updated_at)
                VALUES (?, 'QUADWORDS', ?, TRUE, 4, 9, 42, NULL, NULL, ?,
                    'quadwords-share-v1', 9850, ?, ?)
                RETURNING id
                """, Long.class, legacyPlayer, LocalDate.of(2026, 7, 29), "legacy quad",
                now.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC));
        jdbc.update("""
                UPDATE submission
                SET game_result_id = ?, processing_state = 'RESULT_STORED', updated_at = ?
                WHERE source_message_id = ?
                """, legacyResultId, now.atOffset(ZoneOffset.UTC), legacySource);

        assertFalse(adapter.findAwaitingCanonicalPublication(GameType.QUADWORDS).stream()
                .anyMatch(submission -> submission.sourceMessageId() == legacySource));
        adapter.requestCanonicalRefresh(legacyResultId);
        assertFalse(adapter.findCanonicalRefreshCandidates().stream()
                .anyMatch(candidate -> candidate.submission().sourceMessageId() == legacySource));
        jdbc.update("UPDATE submission SET processing_state = 'CANONICAL_MESSAGE_PUBLISHED' WHERE source_message_id = ?",
                legacySource);
        assertFalse(adapter.findAwaitingOriginalSourceDeletion(GameType.QUADWORDS).stream()
                .anyMatch(submission -> submission.sourceMessageId() == legacySource));
        assertTrue(transactions.execute(status ->
                adapter.claimOriginalSourceDeletion(legacySource, now.plusSeconds(60))).isEmpty());

        long permanentPlayer = 183L;
        long permanentSource = 986L;
        adapter.upsert(new PlayerStore.PlayerUpsert(permanentPlayer, "Permanent quad", true, false));
        registerSubmission(permanentSource, permanentPlayer);
        long permanentResultId = store(permanentSource,
                quadResult(permanentPlayer, 4, "permanent quad", "quadwords-image-v2"), List.of())
                .gameResultId().orElseThrow();
        GameResultStore.PublicationClaim publication = adapter.claimCanonicalPublication(
                permanentResultId, now.plusSeconds(60)).orElseThrow();
        transactions.execute(status -> adapter.beginCanonicalDelivery(
                permanentSource, permanentResultId, publication.token()));
        transactions.execute(status -> adapter.completeCanonicalPublication(
                permanentSource, permanentResultId, 9860L, publication.token()));
        SubmissionStore.SourceDeletionClaim deletion = transactions.execute(status ->
                adapter.claimOriginalSourceDeletion(permanentSource, now.plusSeconds(60)).orElseThrow());
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.recordOriginalSourceDeletionFailure(
                permanentSource, deletion.token(), SubmissionStore.OriginalDeletionFailure.PERMANENT,
                "source message deletion was denied permanently")));

        PostgresPersistenceAdapter restarted = new PostgresPersistenceAdapter(jdbc, Clock.fixed(now, ZoneOffset.UTC));
        assertFalse(restarted.findAwaitingOriginalSourceDeletion(GameType.QUADWORDS).stream()
                .anyMatch(submission -> submission.sourceMessageId() == permanentSource));
        assertTrue(transactions.execute(status ->
                restarted.claimOriginalSourceDeletion(permanentSource, now.plusSeconds(60))).isEmpty());
    }

    @Test
    void serializesConcurrentPublicationContextTransitionsForBothPlayers() throws Exception {
        jdbc.update("DELETE FROM player_participation_period");
        long tobias = 140L;
        long georgia = 141L;
                adapter.upsert(new PlayerStore.PlayerUpsert(tobias, "Concurrent Tobias", true, false));
        adapter.upsert(new PlayerStore.PlayerUpsert(georgia, "Concurrent Georgia", true, false));
        registerSubmission(940L, tobias);
        store(940L, quadResultFor(tobias, true, "tobias quad"));
        registerSubmission(941L, georgia);
        store(941L, quadResultFor(georgia, true, "georgia quad"));
        registerSubmission(942L, tobias);
        registerSubmission(943L, georgia);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SubmissionStore.StoredSubmission> tobiasGrid = executor.submit(
                    () -> store(942L, resultFor(tobias, 3, "tobias grid")));
            Future<SubmissionStore.StoredSubmission> georgiaGrid = executor.submit(
                    () -> store(943L, resultFor(georgia, 3, "georgia grid")));
            List<SubmissionStore.StoredSubmission> stored = List.of(tobiasGrid.get(), georgiaGrid.get());

            assertEquals(2, stored.stream().filter(submission ->
                    submission.publicationContext().personalCompleteEstablished()).count());
            assertEquals(2, stored.stream().filter(submission ->
                    submission.publicationContext().personalPerfectEstablished()).count());
            assertEquals(1, stored.stream().filter(submission ->
                    submission.publicationContext().sharedCompleteEstablished()).count());
            assertEquals(1, stored.stream().filter(submission ->
                    submission.publicationContext().sharedPerfectEstablished()).count());
        }
    }
    @Test
    void retainsAWriteAheadDeliveryAcrossRestartUntilTheCurrentPublicationIsReconciled() {
        long playerId = 160L;
        long source = 960L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Crash recovery", true, false));
        registerSubmission(source, playerId);
        long resultId = store(source, resultFor(playerId, 3, "crash recovery"), List.of()).gameResultId().orElseThrow();
        GameResultStore.PublicationClaim interrupted = adapter.claimCanonicalPublication(resultId, now.minusSeconds(1)).orElseThrow();
        assertEquals(1, transactions.execute(status -> adapter.beginCanonicalDelivery(source, resultId, interrupted.token())).refreshGeneration());

        PostgresPersistenceAdapter restarted = new PostgresPersistenceAdapter(jdbc, Clock.fixed(now, ZoneOffset.UTC));
        assertTrue(restarted.findCanonicalRefreshCandidates().stream()
                .anyMatch(candidate -> candidate.submission().sourceMessageId() == source));
        GameResultStore.PublicationClaim recovered = restarted.claimCanonicalPublication(resultId, now.plusSeconds(60)).orElseThrow();
        transactions.execute(status -> restarted.beginCanonicalDelivery(source, resultId, recovered.token()));
        assertEquals(Boolean.TRUE, transactions.execute(status -> restarted.completeCanonicalPublication(source, resultId, 9600L, recovered.token())));

        GameResultStore.PublicationClaim refresh = restarted.claimCanonicalPublication(resultId, now.plusSeconds(60)).orElseThrow();
        SubmissionStore.CanonicalDeliveryAttempt refreshAttempt = transactions.execute(status ->
                restarted.beginCanonicalDelivery(source, resultId, refresh.token()));
        assertEquals(new SubmissionStore.CanonicalRefreshCompletion(false), transactions.execute(status ->
                restarted.completeCanonicalRefresh(source, resultId, 9600L, refresh.token(), refreshAttempt.refreshGeneration())));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM canonical_delivery_attempt WHERE game_result_id = ?", Integer.class, resultId));
        assertTrue(restarted.findCanonicalRefreshCandidates().isEmpty());
    }

    @Test
    void retainsTheSlowFirstDeliveryFenceAfterLeaseTakeoverUntilDeterministicReconciliation() {
        long playerId = 161L;
        long source = 961L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Slow create", true, false));
        registerSubmission(source, playerId);
        long resultId = store(source, resultFor(playerId, 3, "slow create"), List.of()).gameResultId().orElseThrow();
        GameResultStore.PublicationClaim slow = adapter.claimCanonicalPublication(resultId, now.minusSeconds(1)).orElseThrow();
        transactions.execute(status -> adapter.beginCanonicalDelivery(source, resultId, slow.token()));
        GameResultStore.PublicationClaim takeover = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60)).orElseThrow();
        SubmissionStore.CanonicalDeliveryAttempt takeoverAttempt = transactions.execute(status ->
                adapter.beginCanonicalDelivery(source, resultId, takeover.token()));
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeCanonicalPublication(source, resultId, 9610L, takeover.token())));

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM canonical_delivery_attempt WHERE game_result_id = ?", Integer.class, resultId));
        SubmissionStore.CanonicalRefreshCandidate pending = adapter.findCanonicalRefreshCandidates().stream()
                .filter(candidate -> candidate.submission().sourceMessageId() == source).findFirst().orElseThrow();
        assertEquals(takeoverAttempt.refreshGeneration(), pending.refreshGeneration());
        GameResultStore.PublicationClaim refresh = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60)).orElseThrow();
        SubmissionStore.CanonicalDeliveryAttempt refreshAttempt = transactions.execute(status ->
                adapter.beginCanonicalDelivery(source, resultId, refresh.token()));
        assertEquals(new SubmissionStore.CanonicalRefreshCompletion(false), transactions.execute(status ->
                adapter.completeCanonicalRefresh(source, resultId, 9610L, refresh.token(), refreshAttempt.refreshGeneration())));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM canonical_delivery_attempt WHERE game_result_id = ?", Integer.class, resultId));
    }
    @Test
    void persistsTheSourceDeletionTransitionAndCompletesItAfterRestartWithoutAnotherDelete() {
        long playerId = 170L;
        long sourceMessageId = 970L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Delete recovery", true, false));
        registerSubmission(sourceMessageId, playerId);
        long resultId = store(sourceMessageId, resultFor(playerId, 3, "delete recovery"), List.of())
                .gameResultId().orElseThrow();
        GameResultStore.PublicationClaim publicationClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.completeCanonicalPublication(
                sourceMessageId, resultId, 9700L, publicationClaim.token())));

        SubmissionStore.SourceDeletionClaim deletionClaim = transactions.execute(status ->
                adapter.claimOriginalSourceDeletion(sourceMessageId, now.plusSeconds(60)).orElseThrow());
        assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.recordOriginalSourceDeleted(
                sourceMessageId, deletionClaim.token())));
        SubmissionStore.StoredSubmission deleted = adapter.findBySourceMessageId(sourceMessageId).orElseThrow();
        assertEquals(SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED, deleted.state());
        assertTrue(deleted.originalDeletedAt().isPresent());
        assertEquals(SubmissionStore.OriginalDeletionFailure.NONE, deleted.originalDeletionFailure());

        PostgresPersistenceAdapter restarted = new PostgresPersistenceAdapter(jdbc, Clock.fixed(now, ZoneOffset.UTC));
        assertTrue(restarted.findGridWordsAwaitingOriginalSourceDeletion().stream()
                .anyMatch(submission -> submission.sourceMessageId() == sourceMessageId));
        assertEquals(Boolean.TRUE, transactions.execute(status -> restarted.completeOriginalSourceDeletion(sourceMessageId)));
        assertEquals(Boolean.TRUE, transactions.execute(status -> restarted.completeOriginalSourceDeletion(sourceMessageId)));
        assertEquals(SubmissionStore.SubmissionState.COMPLETED,
                restarted.findBySourceMessageId(sourceMessageId).orElseThrow().state());
        assertFalse(restarted.findGridWordsAwaitingOriginalSourceDeletion().stream()
                .anyMatch(submission -> submission.sourceMessageId() == sourceMessageId));
    }

    @Test
    void keepsAnActiveSourceDeletionLeaseVisibleAcrossRestartUntilItCanBeClaimedAgain() {
        long playerId = 173L;
        long sourceMessageId = 974L;
        Instant leaseUntil = now.plusSeconds(60);
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Delete lease recovery", true, false));
        registerSubmission(sourceMessageId, playerId);
        long resultId = store(sourceMessageId, resultFor(playerId, 3, "delete lease recovery"), List.of())
                .gameResultId().orElseThrow();
        GameResultStore.PublicationClaim publicationClaim = adapter.claimCanonicalPublication(resultId, leaseUntil)
                .orElseThrow();
        transactions.execute(status -> adapter.completeCanonicalPublication(
                sourceMessageId, resultId, 9740L, publicationClaim.token()));
        transactions.execute(status -> adapter.claimOriginalSourceDeletion(sourceMessageId, leaseUntil).orElseThrow());

        PostgresPersistenceAdapter restarted = new PostgresPersistenceAdapter(jdbc, Clock.fixed(now, ZoneOffset.UTC));
        SubmissionStore.StoredSubmission recovered = restarted.findGridWordsAwaitingOriginalSourceDeletion().stream()
                .filter(submission -> submission.sourceMessageId() == sourceMessageId)
                .findFirst().orElseThrow();
        assertEquals(Optional.of(leaseUntil), recovered.sourceDeletionLeaseUntil());
        assertTrue(transactions.execute(status -> restarted.claimOriginalSourceDeletion(sourceMessageId, leaseUntil)).isEmpty());

        PostgresPersistenceAdapter afterLeaseExpiry = new PostgresPersistenceAdapter(
                jdbc, Clock.fixed(leaseUntil.plusSeconds(1), ZoneOffset.UTC));
        assertTrue(transactions.execute(status -> afterLeaseExpiry.claimOriginalSourceDeletion(
                sourceMessageId, leaseUntil.plusSeconds(61))).isPresent());
    }
    @Test
    void rejectsAStaleSourceDeletionOwnerAndAllowsExactlyOneConcurrentOwner() throws Exception {
        long playerId = 171L;
        long sourceMessageId = 971L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Delete owner", true, false));
        registerSubmission(sourceMessageId, playerId);
        long resultId = store(sourceMessageId, resultFor(playerId, 3, "delete owner"), List.of())
                .gameResultId().orElseThrow();
        GameResultStore.PublicationClaim publicationClaim = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        transactions.execute(status -> adapter.completeCanonicalPublication(
                sourceMessageId, resultId, 9710L, publicationClaim.token()));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Optional<SubmissionStore.SourceDeletionClaim>> first = executor.submit(() ->
                    transactions.execute(status -> adapter.claimOriginalSourceDeletion(sourceMessageId, now.plusSeconds(60))));
            Future<Optional<SubmissionStore.SourceDeletionClaim>> second = executor.submit(() ->
                    transactions.execute(status -> adapter.claimOriginalSourceDeletion(sourceMessageId, now.plusSeconds(60))));
            List<Optional<SubmissionStore.SourceDeletionClaim>> claims = List.of(first.get(), second.get());
            assertEquals(1, claims.stream().filter(Optional::isPresent).count());
            SubmissionStore.SourceDeletionClaim owner = claims.stream().flatMap(Optional::stream).findFirst().orElseThrow();

            assertEquals(Boolean.FALSE, transactions.execute(status -> adapter.recordOriginalSourceDeleted(
                    sourceMessageId, UUID.randomUUID())));
            assertEquals(SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED,
                    adapter.findBySourceMessageId(sourceMessageId).orElseThrow().state());
            assertEquals(Boolean.TRUE, transactions.execute(status -> adapter.recordOriginalSourceDeleted(sourceMessageId, owner.token())));
        }
    }

    @Test
    void keepsASupersededSourceVisibleUntilTheNewerCanonicalPublicationIsConfirmed() {
        long playerId = 172L;
        long olderSource = 972L;
        long newerSource = 973L;
        adapter.upsert(new PlayerStore.PlayerUpsert(playerId, "Superseded deletion", true, false));
        registerSubmission(olderSource, playerId, now);
        long resultId = store(olderSource, resultFor(playerId, 4, "older"), List.of()).gameResultId().orElseThrow();
        GameResultStore.PublicationClaim firstPublication = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        transactions.execute(status -> adapter.completeCanonicalPublication(
                olderSource, resultId, 9720L, firstPublication.token()));

        registerSubmission(newerSource, playerId, now.plusSeconds(1));
        store(newerSource, resultFor(playerId, 2, "newer"), List.of());
        assertEquals(SubmissionStore.CanonicalPublicationPreparation.PUBLISHABLE,
                transactions.execute(status -> adapter.prepareCanonicalPublication(newerSource, resultId)));
        assertEquals(SubmissionStore.SubmissionState.SUPERSEDED,
                adapter.findBySourceMessageId(olderSource).orElseThrow().state());
        assertFalse(adapter.findGridWordsAwaitingOriginalSourceDeletion().stream()
                .anyMatch(submission -> submission.sourceMessageId() == olderSource));

        GameResultStore.PublicationClaim newerPublication = adapter.claimCanonicalPublication(resultId, now.plusSeconds(60))
                .orElseThrow();
        transactions.execute(status -> adapter.completeCanonicalPublication(
                newerSource, resultId, 9720L, newerPublication.token()));
        assertTrue(adapter.findGridWordsAwaitingOriginalSourceDeletion().stream()
                .anyMatch(submission -> submission.sourceMessageId() == olderSource));
    }

    @Test
    void appliesTheSourceDeletionRecoveryMigrationToAnEmptyDatabase() {
        assertEquals(3, jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_name = 'submission'
                  AND column_name IN (
                      'source_delete_claim_token',
                      'source_delete_lease_until',
                      'source_delete_failure_class')
                """, Integer.class));
        String constraint = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname = 'ck_submission_source_delete_failure_class'
                """, String.class);
        assertTrue(constraint.contains("PERMANENT"));
    }

    void appliesTheCanonicalRefreshReconciliationMigrationToAnEmptyDatabase() {
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_name = 'game_result'
                  AND column_name IN ('canonical_refresh_required', 'canonical_refresh_generation')
                """, Integer.class));
    }
    @Test
    void appliesTheSupersessionStateMigrationToAnEmptyDatabase() {
        String definition = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname = 'ck_submission_state'
                """, String.class);
        assertTrue(definition.contains("SUPERSEDED"));
    }
    @Test
    void appliesThePublicationContextMigrationToAnEmptyDatabase() {
        assertEquals(4, jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_name = 'submission'
                  AND column_name IN (
                      'personal_complete_established',
                      'personal_perfect_established',
                      'shared_complete_established',
                      'shared_perfect_established')
                """, Integer.class));
    }
    @Test
    void appliesTheOwnershipMigrationToAnEmptyDatabase() {
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_name = 'game_result' AND column_name = 'canonical_publish_claim_token'
                """, Integer.class));
    }
}
