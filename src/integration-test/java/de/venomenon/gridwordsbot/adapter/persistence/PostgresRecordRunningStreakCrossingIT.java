package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator;
import de.venomenon.gridwordsbot.application.record.RecordBootstrapReadService;
import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationProcessor;
import de.venomenon.gridwordsbot.application.record.RecordStateService;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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

/** Bootstrap/live regression coverage for the one-crossing-per-running-streak contract. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresRecordRunningStreakCrossingIT {
    private static final long GUILD_ID = 10L;
    private static final long CHANNEL_ID = 20L;
    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private Clock clock;
    private RecordTransactionRunner transactions;
    private PostgresRecordLiveEvaluationStore work;
    private PostgresRecordStateStore states;
    private PostgresRecordEventStore events;
    private PostgresRecordAnnouncementStore announcements;
    private PostgresRecordBootstrapStore bootstraps;
    private RecordDefinitionCatalog catalog;

    @BeforeAll
    void migrate() throws Exception {
        source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        transactions = new RecordTransactionRunner() {
            private final TransactionTemplate template =
                    new TransactionTemplate(new DataSourceTransactionManager(source));
            @Override public <T> T inTransaction(java.util.function.Supplier<T> action) {
                return template.execute(status -> action.get());
            }
        };
        work = new PostgresRecordLiveEvaluationStore(jdbc, clock);
        states = new PostgresRecordStateStore(jdbc, clock);
        events = new PostgresRecordEventStore(jdbc, clock);
        announcements = new PostgresRecordAnnouncementStore(jdbc, clock);
        bootstraps = new PostgresRecordBootstrapStore(jdbc, clock);
        catalog = RecordDefinitionCatalog.recordsV1();
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
        jdbc.update("DELETE FROM record_day_close");
        jdbc.update("DELETE FROM game_result_excuse_option");
        jdbc.update("DELETE FROM game_result_excuse_offer_context");
        jdbc.update("DELETE FROM game_result_excuse");
        jdbc.update("DELETE FROM channel_message_retirement");
        jdbc.update("DELETE FROM submission_attachment");
        jdbc.update("DELETE FROM submission");
        jdbc.update("DELETE FROM game_result");
        jdbc.update("DELETE FROM player_participation_period");
        jdbc.update("DELETE FROM player");
    }

    @Test
    void runningBootstrapTieStillEmitsTheFirstStrictLiveCrossingAndAggregatesIt() {
        insertPlayer(1);
        insertPlayer(2);
        insertParticipation(1);
        insertParticipation(2);
        for (long playerId : List.of(1L, 2L)) {
            insertSolvedRange(playerId, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 21), "COMPLETED");
            insertSolvedRange(playerId, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 6), "COMPLETED");
        }
        jdbc.update("DELETE FROM record_live_evaluation");

        RecordStateService stateService = bootstrap();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM record_event WHERE event_type='SERIES_RECORD_CROSSED'",
                Integer.class)).isZero();
        assertThat(stateService.consumedCrossings(GUILD_ID, RecordDefinitionVersion.RECORDS_V1)).isEmpty();

        long liveResultId = insertSolved(1, LocalDate.of(2026, 8, 7), "RESULT_STORED");
        processNext(stateService);

        assertThat(jdbc.queryForList("""
                SELECT definition_key FROM record_event
                WHERE processing_origin='LIVE_SUBMISSION'
                  AND event_type='SERIES_RECORD_CROSSED'
                  AND validity='VALID'
                  AND definition_key IN (
                    'streak.activity.personal',
                    'streak.activity.server-individual',
                    'streak.gridwords-solved.personal',
                    'streak.gridwords-solved.server-individual')
                ORDER BY definition_key
                """, String.class)).containsExactly(
                        "streak.activity.personal",
                        "streak.activity.server-individual",
                        "streak.gridwords-solved.personal",
                        "streak.gridwords-solved.server-individual");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM record_event
                WHERE processing_origin='LIVE_SUBMISSION'
                  AND event_type='SERIES_RECORD_CROSSED'
                  AND validity='VALID'
                  AND new_streak_length<>8
                """, Integer.class)).isZero();

        var announcement = announcements.find(new RecordAnnouncementKey(
                GUILD_ID,
                CHANNEL_ID,
                "live-result:" + liveResultId + ":player:1:STREAK_CROSSED"))
                .orElseThrow();
        assertThat(announcement.registration().desiredProjection()).isEqualTo(RecordAnnouncementProjection.CREATE);
        assertThat(announcement.registration().eventIds()).hasSize(4);
    }

    @Test
    void runningStreakAlreadyCanonicalAtBootstrapDoesNotEmitARetroactiveCrossingOnExtension() {
        insertPlayer(1);
        insertPlayer(2);
        insertParticipation(1);
        insertParticipation(2);
        for (long playerId : List.of(1L, 2L)) {
            insertSolvedRange(playerId, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 21), "COMPLETED");
        }
        insertSolvedRange(1, LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 6), "COMPLETED");
        jdbc.update("DELETE FROM record_live_evaluation");

        RecordStateService stateService = bootstrap();
        assertThat(stateService.consumedCrossings(GUILD_ID, RecordDefinitionVersion.RECORDS_V1))
                .extracting(key -> key.definitionKey().value())
                .contains("streak.activity.personal", "streak.activity.server-individual",
                        "streak.gridwords-solved.personal", "streak.gridwords-solved.server-individual");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM record_event WHERE event_type='SERIES_RECORD_CROSSED'",
                Integer.class)).isZero();

        long liveResultId = insertSolved(1, LocalDate.of(2026, 8, 7), "RESULT_STORED");
        processNext(stateService);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM record_event
                WHERE processing_origin='LIVE_SUBMISSION'
                  AND event_type='SERIES_RECORD_CROSSED'
                  AND validity='VALID'
                """, Integer.class)).isZero();
        assertThat(announcements.find(new RecordAnnouncementKey(
                GUILD_ID,
                CHANNEL_ID,
                "live-result:" + liveResultId + ":player:1:STREAK_CROSSED"))).isEmpty();
    }

    private RecordStateService bootstrap() {
        RecordStateService stateService = new RecordStateService(states, events, transactions, catalog);
        RecordBootstrapCoordinator bootstrap = new RecordBootstrapCoordinator(
                bootstraps, new PostgresRecordHistoryQuery(jdbc), stateService, catalog, clock);
        assertThat(bootstrap.run(GUILD_ID)).isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        return stateService;
    }

    private void processNext(RecordStateService stateService) {
        var claim = work.claimNext(new RecordLeaseClaimRequest(NOW.plusSeconds(1), NOW.plusSeconds(60)))
                .orElseThrow();
        RecordLiveEvaluationProcessor processor = new RecordLiveEvaluationProcessor(
                work,
                new PostgresRecordLiveHistoryQuery(jdbc),
                new RecordBootstrapReadService(bootstraps),
                stateService,
                events,
                announcements,
                transactions,
                catalog,
                clock,
                CHANNEL_ID);
        assertThat(processor.process(claim)).isEqualTo(RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED);
    }

    private void insertPlayer(long playerId) {
        jdbc.update("""
                INSERT INTO player (
                    discord_user_id,display_name,active,administrator,reminder_opt_in,created_at,updated_at)
                VALUES (?,?,TRUE,FALSE,FALSE,?,?)
                """, playerId, "Player " + playerId,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    private void insertParticipation(long playerId) {
        jdbc.update("""
                INSERT INTO player_participation_period (
                    player_id,game_type,active_from,inactive_from,created_at,updated_at)
                VALUES (?,'GRIDWORDS',DATE '2026-07-15',NULL,?,?)
                """, playerId, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    private void insertSolvedRange(long playerId, LocalDate first, LocalDate last, String submissionState) {
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            insertSolved(playerId, day, submissionState);
        }
    }

    private long insertSolved(long playerId, LocalDate gameDate, String submissionState) {
        Instant acceptedAt = gameDate.atTime(12, 0).toInstant(ZoneOffset.UTC);
        Long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id,game_type,game_date,solved,attempts_used,max_attempts,duration_seconds,
                    normalized_board,raw_share_text,parser_version,created_at,updated_at)
                VALUES (?,'GRIDWORDS',?,TRUE,4,6,120,'ABCDE','share','gridwords-share-v1',?,?)
                RETURNING id
                """, Long.class, playerId, gameDate,
                java.sql.Timestamp.from(acceptedAt), java.sql.Timestamp.from(acceptedAt));
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id,guild_id,channel_id,author_player_id,raw_message_content,
                    processing_state,game_result_id,received_at,updated_at,original_deleted_at)
                VALUES (?,10,20,?,'share',?,?,?, ?, CASE WHEN ?='COMPLETED' THEN ? ELSE NULL END)
                """,
                5_000_000L + resultId,
                playerId,
                submissionState,
                resultId,
                java.sql.Timestamp.from(acceptedAt),
                java.sql.Timestamp.from(acceptedAt),
                submissionState,
                java.sql.Timestamp.from(acceptedAt));
        return java.util.Objects.requireNonNull(resultId);
    }
}
