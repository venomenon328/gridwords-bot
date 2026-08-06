package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.application.record.RecordBootstrapReadService;
import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationProcessor;
import de.venomenon.gridwordsbot.application.record.RecordStateService;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL commit/rollback boundary for one 12.6-B processor invocation. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresRecordLiveEvaluationProcessorIT {
    private static final Instant NOW = Instant.parse("2026-08-06T08:00:00Z");
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private JdbcTemplate jdbc;
    private Clock clock;
    private RecordTransactionRunner transactions;
    private PostgresRecordLiveEvaluationStore work;
    private PostgresRecordStateStore states;
    private PostgresRecordEventStore events;
    private PostgresRecordAnnouncementStore announcements;
    private PostgresRecordBootstrapStore bootstraps;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        transactions = new RecordTransactionRunner() {
            private final TransactionTemplate template = new TransactionTemplate(new DataSourceTransactionManager(source));
            @Override public <T> T inTransaction(java.util.function.Supplier<T> action) {
                return template.execute(status -> action.get());
            }
        };
        work = new PostgresRecordLiveEvaluationStore(jdbc, clock);
        states = new PostgresRecordStateStore(jdbc, clock);
        events = new PostgresRecordEventStore(jdbc, clock);
        announcements = new PostgresRecordAnnouncementStore(jdbc, clock);
        bootstraps = new PostgresRecordBootstrapStore(jdbc, clock);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM record_announcement_message");
        jdbc.update("DELETE FROM record_announcement_event");
        jdbc.update("DELETE FROM record_announcement");
        jdbc.update("DELETE FROM record_event");
        jdbc.update("DELETE FROM record_state");
        jdbc.update("DELETE FROM record_bootstrap");
        jdbc.update("DELETE FROM record_live_evaluation");
        jdbc.update("DELETE FROM submission_attachment");
        jdbc.update("DELETE FROM submission");
        jdbc.update("DELETE FROM game_result");
        jdbc.update("DELETE FROM player_participation_period");
        jdbc.update("DELETE FROM player");
    }

    @Test
    void processorCommitsStateAuditAndTerminalWorkForOneClaim() {
        long resultId = insertReadyClaim();

        RecordLiveEvaluationProcessor.ProcessingResult outcome = processor(work).process(claim());

        assertThat(outcome).isEqualTo(RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED);
        assertThat(work.findAll(10, resultId)).singleElement().satisfies(snapshot ->
                assertThat(snapshot.state().name()).isEqualTo("SUCCEEDED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_state", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_event", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_announcement", Integer.class)).isZero();
    }

    @Test
    void terminalFailureRollsBackRecordWritesButKeepsTheAlreadyCommittedCanonicalResult() {
        long resultId = insertReadyClaim();
        PostgresRecordLiveEvaluationStore failingTerminalWork = new PostgresRecordLiveEvaluationStore(jdbc, clock) {
            @Override public boolean markSucceeded(
                    de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey key,
                    java.util.UUID token,
                    Instant completedAt) {
                return false;
            }
        };

        assertThatThrownBy(() -> processor(failingTerminalWork).process(claim()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease was lost");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_state", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_announcement", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_result WHERE id=?", Integer.class, resultId)).isOne();
    }

    private RecordLiveEvaluationProcessor processor(PostgresRecordLiveEvaluationStore processorWork) {
        RecordDefinitionCatalog catalog = RecordDefinitionCatalog.recordsV1();
        return new RecordLiveEvaluationProcessor(processorWork, new PostgresRecordLiveHistoryQuery(jdbc),
                new RecordBootstrapReadService(bootstraps),
                new RecordStateService(states, events, transactions, catalog), events, announcements,
                transactions, catalog, clock, 20);
    }

    private long insertReadyClaim() {
        jdbc.update("""
                INSERT INTO player (discord_user_id,display_name,active,administrator,reminder_opt_in,created_at,updated_at)
                VALUES (1,'Player',TRUE,FALSE,FALSE,?,?)
                """, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO player_participation_period (player_id,game_type,active_from,inactive_from,created_at,updated_at)
                VALUES (1,'GRIDWORDS',DATE '2026-08-01',NULL,?,?),
                       (1,'QUADWORDS',DATE '2026-08-01',NULL,?,?)
                """, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        Long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (player_id,game_type,game_date,solved,attempts_used,max_attempts,
                    duration_seconds,normalized_board,raw_share_text,parser_version,created_at,updated_at)
                VALUES (1,'GRIDWORDS',DATE '2026-08-06',TRUE,3,6,60,'ABCDE','share','gridwords-share-v1',?,?)
                RETURNING id
                """, Long.class, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO submission (source_message_id,guild_id,channel_id,author_player_id,raw_message_content,
                    processing_state,game_result_id,received_at,updated_at)
                VALUES (100,10,20,1,'share','RESULT_STORED',?,?,?)
                """, resultId, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        RecordBootstrapKey bootstrapKey = new RecordBootstrapKey(10, RecordDefinitionVersion.RECORDS_V1);
        bootstraps.register(bootstrapKey);
        var bootstrapClaim = bootstraps.claim(bootstrapKey, new RecordLeaseClaimRequest(NOW, NOW.plusSeconds(60))).orElseThrow();
        bootstraps.markSucceeded(bootstrapKey, bootstrapClaim.token(), NOW.plusSeconds(1));
        return java.util.Objects.requireNonNull(resultId);
    }

    private RecordLiveEvaluationClaim claim() {
        return work.claimNext(new RecordLeaseClaimRequest(NOW.plusSeconds(2), NOW.plusSeconds(60))).orElseThrow();
    }
}
