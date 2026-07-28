package de.venomenon.gridwordsbot.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import de.venomenon.gridwordsbot.port.out.SubmissionConflictException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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
        NormalizedBoard board = new NormalizedBoard(List.of("?????", "??????????", "??????????"));
        ParsedGameResult parsed = new ParsedGameResult(GameType.GRIDWORDS, LocalDate.of(2026, 7, 29), new ShareOutcome.Solved(attempts, 6), Duration.ofSeconds(42), OptionalInt.empty(), Optional.of(board));
        return resultFor(100L, attempts, text);
    }

    private GameResultStore.GameResultUpsert resultFor(long playerId, int attempts, String text) {
        NormalizedBoard board = new NormalizedBoard(List.of("?????", "??????????", "??????????", "?????"));
        ParsedGameResult parsed = new ParsedGameResult(GameType.GRIDWORDS, LocalDate.of(2026, 7, 29), new ShareOutcome.Solved(attempts, 6), Duration.ofSeconds(42), OptionalInt.empty(), Optional.of(board));
        return new GameResultStore.GameResultUpsert(playerId, parsed, text, "v1");
    }
}