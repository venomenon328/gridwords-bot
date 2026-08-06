package de.venomenon.gridwordsbot.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
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
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresSourceDeletionRecoveryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private final Instant now = Instant.parse("2026-07-29T08:00:00Z");
    private JdbcTemplate jdbc;
    private PostgresPersistenceAdapter persistence;
    private PostgresSourceDeletionRecoveryAdapter recovery;
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
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        persistence = new PostgresPersistenceAdapter(jdbc, clock);
        recovery = new PostgresSourceDeletionRecoveryAdapter(jdbc, clock);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @ParameterizedTest
    @EnumSource(GameType.class)
    void controlledRecoveryReactivatesOnePermanentFailureAndAllowsCompletion(GameType gameType) {
        long offset = gameType.ordinal();
        long playerId = 190L + offset;
        long sourceMessageId = 990L + offset;
        long canonicalMessageId = 9990L + offset;
        persistence.upsert(new PlayerStore.PlayerUpsert(playerId, "Recovery " + gameType, true, false));
        persistence.register(new SubmissionStore.SubmissionRegistration(
                sourceMessageId, 200L, 300L, playerId, "share", List.of(), now));
        SubmissionStore.StoredSubmission stored = persistence.storeResult(new SubmissionStore.ResultStorage(
                sourceMessageId, result(playerId, gameType)));
        long resultId = stored.gameResultId().orElseThrow();

        GameResultStore.PublicationClaim publication = persistence.claimCanonicalPublication(
                resultId, now.plusSeconds(60)).orElseThrow();
        transactions.execute(status -> persistence.beginCanonicalDelivery(sourceMessageId, resultId, publication.token()));
        assertEquals(Boolean.TRUE, transactions.execute(status -> persistence.completeCanonicalPublication(
                sourceMessageId, resultId, canonicalMessageId, publication.token())));
        SubmissionStore.SourceDeletionClaim firstDelete = transactions.execute(status ->
                persistence.claimOriginalSourceDeletion(sourceMessageId, now.plusSeconds(60)).orElseThrow());
        assertEquals(Boolean.TRUE, transactions.execute(status -> persistence.recordOriginalSourceDeletionFailure(
                sourceMessageId, firstDelete.token(), SubmissionStore.OriginalDeletionFailure.PERMANENT,
                "source message deletion was denied permanently")));
        assertTrue(persistence.findAwaitingOriginalSourceDeletion(gameType).stream()
                .noneMatch(candidate -> candidate.sourceMessageId() == sourceMessageId));
        assertEquals(List.of(resultId), recovery.findPermanentlyFailedResultIds());

        assertEquals(1, recovery.reactivatePermanentFailures(OptionalLong.of(resultId)));
        assertEquals(SubmissionStore.OriginalDeletionFailure.NONE,
                persistence.findBySourceMessageId(sourceMessageId).orElseThrow().originalDeletionFailure());
        assertTrue(persistence.findAwaitingOriginalSourceDeletion(gameType).stream()
                .anyMatch(candidate -> candidate.sourceMessageId() == sourceMessageId));

        SubmissionStore.SourceDeletionClaim retry = transactions.execute(status ->
                persistence.claimOriginalSourceDeletion(sourceMessageId, now.plusSeconds(60)).orElseThrow());
        assertEquals(Boolean.TRUE, transactions.execute(status ->
                persistence.recordOriginalSourceDeleted(sourceMessageId, retry.token())));
        assertEquals(Boolean.TRUE, transactions.execute(status ->
                persistence.completeOriginalSourceDeletion(sourceMessageId)));
        assertEquals(SubmissionStore.SubmissionState.COMPLETED,
                persistence.findBySourceMessageId(sourceMessageId).orElseThrow().state());
    }

    @Test
    void controlledRecoveryDoesNotReactivateBoardlessLegacyQuadWords() {
        long playerId = 192L;
        long sourceMessageId = 992L;
        persistence.upsert(new PlayerStore.PlayerUpsert(playerId, "Legacy recovery", true, false));
        persistence.register(new SubmissionStore.SubmissionRegistration(
                sourceMessageId, 200L, 300L, playerId, "legacy", List.of(), now));
        long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts,
                    duration_seconds, normalized_board, raw_share_text, parser_version,
                    canonical_message_id, created_at, updated_at)
                VALUES (?, 'QUADWORDS', ?, TRUE, 4, 9, 42, NULL, ?, 'quadwords-share-v1', ?, ?, ?)
                RETURNING id
                """, Long.class, playerId, LocalDate.of(2026, 7, 29), "legacy", 9992L,
                now.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC));
        jdbc.update("""
                UPDATE submission
                SET game_result_id = ?, processing_state = 'CANONICAL_MESSAGE_PUBLISHED',
                    source_delete_failure_class = 'PERMANENT', technical_error_message = 'permission denied'
                WHERE source_message_id = ?
                """, resultId, sourceMessageId);

        assertEquals(0, recovery.reactivatePermanentFailures(OptionalLong.empty()));
        assertEquals(SubmissionStore.OriginalDeletionFailure.PERMANENT,
                persistence.findBySourceMessageId(sourceMessageId).orElseThrow().originalDeletionFailure());
    }

    private GameResultStore.GameResultUpsert result(long playerId, GameType gameType) {
        ParsedGameResult parsed;
        String parserVersion;
        if (gameType == GameType.GRIDWORDS) {
            parsed = new ParsedGameResult(
                    gameType,
                    LocalDate.of(2026, 7, 29),
                    new ShareOutcome.Solved(3, 6),
                    Duration.ofSeconds(85),
                    OptionalInt.empty(),
                    Optional.of(new NormalizedBoard(List.of(
                            "⬜⬜⬜⬜⬜", "🟨🟨🟨🟨🟨", "🟩🟩🟩🟩🟩"))),
                    Optional.empty());
            parserVersion = "gridwords-share-v1";
        } else {
            QuadWordsBoard board = new QuadWordsBoard(List.of(
                    "⬜⬜⬜⬜⬜", "🟨⬜⬜⬜⬜", "🟩🟩🟩🟩🟩", "⬜⬜⬜⬜⬜"));
            parsed = new ParsedGameResult(
                    gameType,
                    LocalDate.of(2026, 7, 29),
                    new ShareOutcome.Solved(4, 9),
                    Duration.ofSeconds(222),
                    OptionalInt.empty(),
                    Optional.empty(),
                    Optional.of(new QuadWordsBoards(board, board, board, board)));
            parserVersion = "quadwords-image-v2";
        }
        return new GameResultStore.GameResultUpsert(playerId, parsed, "share", parserVersion);
    }
}
