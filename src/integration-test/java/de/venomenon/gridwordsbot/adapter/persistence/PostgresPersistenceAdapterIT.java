package de.venomenon.gridwordsbot.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.application.submission.ConfiguredPlayer;
import de.venomenon.gridwordsbot.application.submission.ConfiguredPlayerSynchronizer;
import de.venomenon.gridwordsbot.application.submission.ProcessSharedResultService;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
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
import java.util.List;
import java.util.Optional;
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
    private GameResultStore.GameResultUpsert result(int attempts, String text) {
        NormalizedBoard board = board(attempts);
        ParsedGameResult parsed = new ParsedGameResult(GameType.GRIDWORDS, LocalDate.of(2026, 7, 29), new ShareOutcome.Solved(attempts, 6), Duration.ofSeconds(42), OptionalInt.empty(), Optional.of(board));
        return resultFor(100L, attempts, text);
    }

    private GameResultStore.GameResultUpsert resultFor(long playerId, int attempts, String text) {
        NormalizedBoard board = board(attempts);
        ParsedGameResult parsed = new ParsedGameResult(GameType.GRIDWORDS, LocalDate.of(2026, 7, 29), new ShareOutcome.Solved(attempts, 6), Duration.ofSeconds(42), OptionalInt.empty(), Optional.of(board));
        return new GameResultStore.GameResultUpsert(playerId, parsed, text, "v1");
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
                        Duration.ofSeconds(587), OptionalInt.of(8), Optional.empty()), "quad", "v2");
        assertEquals(grid.parsedResult(), adapter.upsert(grid).parsedResult());
        assertEquals(quad.parsedResult(), adapter.upsert(quad).parsedResult());
        assertEquals("quad", adapter.find(106L, GameType.QUADWORDS, LocalDate.of(2026, 7, 28)).orElseThrow().rawShareText());
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
    void synchronizesConfiguredPlayersIdempotentlyWithAdministratorFlags() {
        ConfiguredPlayerSynchronizer synchronizer = new ConfiguredPlayerSynchronizer(List.of(
                new ConfiguredPlayer(109L, "Tobias", true),
                new ConfiguredPlayer(110L, "Georgia", false)), adapter);

        synchronizer.synchronize();
        synchronizer.synchronize();

        assertTrue(adapter.findByDiscordUserId(109L).orElseThrow().administrator());
        assertFalse(adapter.findByDiscordUserId(110L).orElseThrow().administrator());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM player WHERE discord_user_id = 109", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM player WHERE discord_user_id = 110", Integer.class));
    }

    @Test
    void persistsAndRoundTripsFinalRejectionsWithoutCreatingAResult() {
        adapter.upsert(new PlayerStore.PlayerUpsert(111L, "Rejected", true, false));
        SubmissionStore.SubmissionRegistration registration = new SubmissionStore.SubmissionRegistration(
                909L, 200L, 300L, 111L, "invalid share", List.of(), now);
        adapter.register(registration);

        SubmissionStore.StoredSubmission rejected = transactions.execute(status -> adapter.reject(
                new SubmissionStore.RejectedSubmission(909L, "OUTSIDE_ALLOWED_DATE_WINDOW")));
        SubmissionStore.StoredSubmission replay = transactions.execute(status -> adapter.reject(
                new SubmissionStore.RejectedSubmission(909L, "OUTSIDE_ALLOWED_DATE_WINDOW")));

        assertEquals(SubmissionStore.SubmissionState.PARSE_REJECTED, rejected.state());
        assertEquals(Optional.of("OUTSIDE_ALLOWED_DATE_WINDOW"), rejected.parserErrorCode());
        assertEquals(rejected, replay);
        assertTrue(adapter.find(111L, GameType.GRIDWORDS, LocalDate.of(2026, 7, 29)).isEmpty());
        assertThrows(SubmissionConflictException.class, () -> transactions.execute(status -> adapter.reject(
                new SubmissionStore.RejectedSubmission(909L, "MISSING_BOARD"))));
    }

    @Test
    void storesACompleteValidApplicationFlowAtomically() {
        new ConfiguredPlayerSynchronizer(List.of(
                new ConfiguredPlayer(112L, "Application", true),
                new ConfiguredPlayer(113L, "Second", false)), adapter).synchronize();
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
    void appliesTheOwnershipMigrationToAnEmptyDatabase() {
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_name = 'game_result' AND column_name = 'canonical_publish_claim_token'
                """, Integer.class));
    }
}
