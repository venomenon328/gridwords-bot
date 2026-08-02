package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresBoardlessQuadWordsIT {
    private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");
    private static final LocalDate GAME_DATE = LocalDate.of(2026, 7, 29);
    private static final long PLAYER_ID = 8100L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;

    private PostgresPersistenceAdapter adapter;
    private TransactionTemplate transactions;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        adapter = new PostgresPersistenceAdapter(jdbc, clock);

        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE player RESTART IDENTITY CASCADE");
        adapter.upsert(new PlayerStore.PlayerUpsert(PLAYER_ID, "Tobias", true, true));
    }

    @Test
    void storesARealNullBoardResultAndMakesOnlyTheCurrentTextVersionRecoverable() {
        register(8101L, "text only", NOW);

        SubmissionStore.StoredSubmission stored = store(8101L, boardless("text only"));

        assertThat(stored.state()).isEqualTo(SubmissionStore.SubmissionState.RESULT_STORED);
        assertThat(adapter.find(PLAYER_ID, GameType.QUADWORDS, GAME_DATE).orElseThrow()
                .parsedResult().quadWordsBoards()).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT parser_version = 'quadwords-share-v2'
                    AND quadwords_top_left_board IS NULL
                    AND quadwords_top_right_board IS NULL
                    AND quadwords_bottom_left_board IS NULL
                    AND quadwords_bottom_right_board IS NULL
                FROM game_result
                WHERE id = ?
                """, Boolean.class, stored.gameResultId().orElseThrow())).isTrue();
        assertThat(adapter.findAwaitingCanonicalPublication(GameType.QUADWORDS))
                .extracting(SubmissionStore.StoredSubmission::sourceMessageId)
                .containsExactly(8101L);

        SubmissionStore.StoredSubmission replay = store(8101L, boardless("text only"));
        assertThat(replay).isEqualTo(stored);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_result", Integer.class)).isEqualTo(1);
    }

    @Test
    void aLaterImageEnrichesTheSameResultAndALaterBoardlessReplayKeepsThoseBoards() {
        register(8110L, "first boardless", NOW);
        long resultId = store(8110L, boardless("first boardless")).gameResultId().orElseThrow();

        register(8111L, "image correction", NOW.plusSeconds(1));
        QuadWordsBoards boards = boards(4);
        long enrichedResultId = store(8111L, withBoards(
                "image correction", boards, "quadwords-image-v3")).gameResultId().orElseThrow();

        assertThat(enrichedResultId).isEqualTo(resultId);
        assertThat(adapter.findById(resultId).orElseThrow().parsedResult().quadWordsBoards()).contains(boards);
        assertThat(adapter.findById(resultId).orElseThrow().parserVersion()).isEqualTo("quadwords-image-v3");

        register(8112L, "later boardless", NOW.plusSeconds(2));
        long preservedResultId = store(8112L, boardless("later boardless")).gameResultId().orElseThrow();

        assertThat(preservedResultId).isEqualTo(resultId);
        assertThat(adapter.findById(resultId).orElseThrow().parsedResult().quadWordsBoards()).contains(boards);
        assertThat(adapter.findById(resultId).orElseThrow().parserVersion()).isEqualTo("quadwords-image-v3");
    }

    @Test
    void directBoardlessUpsertCannotEraseStoredBoardsOrDowngradeParserVersion() {
        QuadWordsBoards boards = boards(4);
        GameResultStore.StoredGameResult imageBacked = adapter.upsert(withBoards(
                "image-backed", boards, "quadwords-image-v3"));

        GameResultStore.StoredGameResult boardlessReplay = adapter.upsert(boardless("later boardless"));

        assertThat(boardlessReplay.id()).isEqualTo(imageBacked.id());
        assertThat(boardlessReplay.parsedResult().quadWordsBoards()).contains(boards);
        assertThat(boardlessReplay.parserVersion()).isEqualTo("quadwords-image-v3");
        assertThat(boardlessReplay.rawShareText()).isEqualTo("later boardless");
    }

    @Test
    void rejectsPartiallyPopulatedQuadWordsBoards() {
        register(8115L, "partial", NOW);
        long resultId = store(8115L, boardless("partial")).gameResultId().orElseThrow();

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE game_result
                SET quadwords_top_left_board = '[]'
                WHERE id = ?
                """, resultId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(adapter.findById(resultId).orElseThrow().parsedResult().quadWordsBoards()).isEmpty();
    }

    @Test
    void legacyBoardlessRowsStayReadableButAreExcludedFromAutomaticPublicationAndDeletion() {
        register(8120L, "legacy", NOW);
        long resultId = store(8120L, withBoards("legacy", boards(4))).gameResultId().orElseThrow();
        jdbc.update("""
                UPDATE game_result
                SET quadwords_top_left_board = NULL,
                    quadwords_top_right_board = NULL,
                    quadwords_bottom_left_board = NULL,
                    quadwords_bottom_right_board = NULL,
                    parser_version = 'quadwords-share-v1',
                    canonical_message_id = 99120
                WHERE id = ?
                """, resultId);
        jdbc.update("""
                UPDATE submission
                SET processing_state = 'CANONICAL_MESSAGE_PUBLISHED'
                WHERE source_message_id = 8120
                """);

        assertThat(adapter.findById(resultId).orElseThrow().parsedResult().quadWordsBoards()).isEmpty();
        assertThat(adapter.findAwaitingCanonicalPublication(GameType.QUADWORDS)).isEmpty();
        assertThat(adapter.findAwaitingOriginalSourceDeletion(GameType.QUADWORDS)).isEmpty();
        Optional<SubmissionStore.SourceDeletionClaim> deletionClaim = transactions.execute(status ->
                adapter.claimOriginalSourceDeletion(8120L, NOW.plusSeconds(60)));
        assertThat(deletionClaim).isEmpty();
    }

    @Test
    void currentBoardlessPublicationCanSafelyClaimOriginalSourceDeletion() {
        register(8130L, "publishable boardless", NOW);
        long resultId = store(8130L, boardless("publishable boardless")).gameResultId().orElseThrow();
        GameResultStore.PublicationClaim claim = adapter.claimCanonicalPublication(resultId, NOW.plusSeconds(60))
                .orElseThrow();
        Boolean completed = transactions.execute(status -> adapter.completeCanonicalPublication(
                8130L, resultId, 99130L, claim.token()));
        assertThat(completed).isTrue();

        assertThat(adapter.findAwaitingOriginalSourceDeletion(GameType.QUADWORDS))
                .extracting(SubmissionStore.StoredSubmission::sourceMessageId)
                .containsExactly(8130L);
        Optional<SubmissionStore.SourceDeletionClaim> deletionClaim = transactions.execute(status ->
                adapter.claimOriginalSourceDeletion(8130L, NOW.plusSeconds(120)));
        assertThat(deletionClaim).isPresent();
    }

    private void register(long sourceMessageId, String rawText, Instant receivedAt) {
        adapter.register(new SubmissionStore.SubmissionRegistration(
                sourceMessageId,
                200L,
                300L,
                PLAYER_ID,
                rawText,
                List.of(),
                receivedAt));
    }

    private SubmissionStore.StoredSubmission store(
            long sourceMessageId,
            GameResultStore.GameResultUpsert result) {
        return transactions.execute(status -> adapter.storeResult(
                new SubmissionStore.ResultStorage(sourceMessageId, result)));
    }

    private static GameResultStore.GameResultUpsert boardless(String rawText) {
        return new GameResultStore.GameResultUpsert(
                PLAYER_ID,
                parsed(Optional.empty()),
                rawText,
                "quadwords-share-v2");
    }

    private static GameResultStore.GameResultUpsert withBoards(String rawText, QuadWordsBoards boards) {
        return withBoards(rawText, boards, "quadwords-image-v2");
    }

    private static GameResultStore.GameResultUpsert withBoards(
            String rawText,
            QuadWordsBoards boards,
            String parserVersion) {
        return new GameResultStore.GameResultUpsert(
                PLAYER_ID,
                parsed(Optional.of(boards)),
                rawText,
                parserVersion);
    }

    private static ParsedGameResult parsed(Optional<QuadWordsBoards> boards) {
        return new ParsedGameResult(
                GameType.QUADWORDS,
                GAME_DATE,
                new ShareOutcome.Solved(4, 9),
                Duration.ofSeconds(245),
                OptionalInt.empty(),
                Optional.empty(),
                boards);
    }

    private static QuadWordsBoards boards(int rows) {
        String row = "⬜🟨🟩⬜🟨";
        QuadWordsBoard board = new QuadWordsBoard(Collections.nCopies(rows, row));
        return new QuadWordsBoards(board, board, board, board);
    }
}
