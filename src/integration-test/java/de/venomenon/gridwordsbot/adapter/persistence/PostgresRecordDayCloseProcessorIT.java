package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.record.RecordBootstrapReadService;
import de.venomenon.gridwordsbot.application.record.RecordDayCloseService;
import de.venomenon.gridwordsbot.application.record.RecordStateService;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
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

/**
 * Focused real-PostgreSQL acceptance for one normal close, chronological
 * multi-day restart recovery and rollback after a lost terminal marker.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresRecordDayCloseProcessorIT {
    private static final long GUILD_ID = 10L;
    private static final long CHANNEL_ID = 20L;
    private static final long PLAYER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-06T04:01:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
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
        catalog = RecordDefinitionCatalog.recordsV1();
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM record_announcement_message");
        jdbc.update("DELETE FROM record_announcement_event");
        jdbc.update("DELETE FROM record_announcement");
        jdbc.update("DELETE FROM record_event");
        jdbc.update("DELETE FROM record_state");
        jdbc.update("DELETE FROM record_day_close");
        jdbc.update("DELETE FROM record_bootstrap");
        jdbc.update("DELETE FROM record_live_evaluation");
        jdbc.update("DELETE FROM submission_attachment");
        jdbc.update("DELETE FROM submission");
        jdbc.update("DELETE FROM game_result");
        jdbc.update("DELETE FROM player_participation_period");
        jdbc.update("DELETE FROM player");
    }

    @Test
    void commitsStateEventAnnouncementAndMarkerForOneClosedDay() {
        prepareTwoSevenDaySolvedRuns();
        PostgresRecordDayCloseStore work = new PostgresRecordDayCloseStore(jdbc, CLOCK);
        seedSucceeded(work, LocalDate.of(2026, 7, 7), NOW.minusSeconds(2));

        assertThat(service(work, CLOCK).reconcileThrough(GUILD_ID, LocalDate.of(2026, 7, 8))).isEqualTo(1);

        RecordDayCloseKey closed = key(LocalDate.of(2026, 7, 8));
        assertThat(work.find(closed).orElseThrow().state()).isEqualTo(RecordWorkState.SUCCEEDED);
        assertThat(count("record_state")).isPositive();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM record_event WHERE trigger_key='day-close:2026-07-08'",
                Integer.class)).isPositive();
        assertThat(count("record_announcement")).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_announcement_event", Integer.class)).isPositive();
    }

    @Test
    void failedSecondCatchUpDayDoesNotMaterializeLaterDaysAndRestartContinuesChronologically() {
        insertPlayer();
        insertParticipation("GRIDWORDS", LocalDate.of(2026, 8, 1));
        insertParticipation("QUADWORDS", LocalDate.of(2026, 8, 1));
        readyBootstrap(CLOCK);

        PostgresRecordDayCloseStore seedStore = new PostgresRecordDayCloseStore(jdbc, CLOCK);
        seedSucceeded(seedStore, LocalDate.of(2026, 7, 31), NOW.minusSeconds(2));
        FailSecondTerminalStore failSecond = new FailSecondTerminalStore(jdbc, CLOCK);

        assertThat(service(failSecond, CLOCK).reconcileThrough(GUILD_ID, LocalDate.of(2026, 8, 3))).isEqualTo(1);

        assertThat(withoutPerfectEnd()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(seedStore.find(key(LocalDate.of(2026, 8, 1))).orElseThrow().state())
                .isEqualTo(RecordWorkState.SUCCEEDED);
        assertThat(seedStore.find(key(LocalDate.of(2026, 8, 2))).orElseThrow().state())
                .isEqualTo(RecordWorkState.CLAIMED);
        assertThat(seedStore.find(key(LocalDate.of(2026, 8, 3)))).isEmpty();

        Clock restartedClock = Clock.fixed(NOW.plusSeconds(181), ZoneOffset.UTC);
        PostgresRecordDayCloseStore restarted = new PostgresRecordDayCloseStore(jdbc, restartedClock);
        assertThat(service(restarted, restartedClock).reconcileThrough(GUILD_ID, LocalDate.of(2026, 8, 3)))
                .isEqualTo(2);

        assertThat(withoutPerfectEnd()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(restarted.find(key(LocalDate.of(2026, 8, 2))).orElseThrow().state())
                .isEqualTo(RecordWorkState.SUCCEEDED);
        assertThat(restarted.find(key(LocalDate.of(2026, 8, 3))).orElseThrow().state())
                .isEqualTo(RecordWorkState.SUCCEEDED);
    }

    @Test
    void rejectedTerminalMarkerRollsBackAllRecordWrites() {
        prepareTwoSevenDaySolvedRuns();
        PostgresRecordDayCloseStore seedStore = new PostgresRecordDayCloseStore(jdbc, CLOCK);
        seedSucceeded(seedStore, LocalDate.of(2026, 7, 7), NOW.minusSeconds(2));
        PostgresRecordDayCloseStore failing = new PostgresRecordDayCloseStore(jdbc, CLOCK) {
            @Override
            public boolean markSucceeded(RecordDayCloseKey key, UUID token, Instant completedAt) {
                return false;
            }
        };

        assertThat(service(failing, CLOCK).reconcileThrough(GUILD_ID, LocalDate.of(2026, 7, 8))).isZero();

        assertThat(count("record_state")).isZero();
        assertThat(count("record_event")).isZero();
        assertThat(count("record_announcement")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_announcement_event", Integer.class)).isZero();
        assertThat(seedStore.find(key(LocalDate.of(2026, 7, 8))).orElseThrow().state())
                .isEqualTo(RecordWorkState.CLAIMED);
    }

    private RecordDayCloseService service(PostgresRecordDayCloseStore work, Clock clock) {
        RecordTransactionRunner transactions = transactions();
        PostgresRecordStateStore stateStore = new PostgresRecordStateStore(jdbc, clock);
        PostgresRecordEventStore eventStore = new PostgresRecordEventStore(jdbc, clock);
        RecordStateService states = new RecordStateService(stateStore, eventStore, transactions, catalog);
        return new RecordDayCloseService(
                work,
                new PostgresRecordHistoryQuery(jdbc),
                new RecordBootstrapReadService(new PostgresRecordBootstrapStore(jdbc, clock)),
                states,
                eventStore,
                new PostgresRecordAnnouncementStore(jdbc, clock),
                transactions,
                catalog,
                clock,
                CHANNEL_ID);
    }

    private RecordTransactionRunner transactions() {
        TransactionTemplate template = new TransactionTemplate(new DataSourceTransactionManager(source));
        return new RecordTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> action) {
                return template.execute(status -> action.get());
            }
        };
    }

    private void prepareTwoSevenDaySolvedRuns() {
        insertPlayer();
        insertParticipation("GRIDWORDS", LocalDate.of(2026, 6, 1));
        insertSolvedRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), 1000L);
        insertSolvedRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7), 2000L);
        readyBootstrap(CLOCK);
    }

    private void insertPlayer() {
        jdbc.update("""
                INSERT INTO player
                    (discord_user_id,display_name,active,administrator,reminder_opt_in,created_at,updated_at)
                VALUES (?,?,TRUE,FALSE,FALSE,?,?)
                """, PLAYER_ID, "Player", java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    private void insertParticipation(String gameType, LocalDate activeFrom) {
        jdbc.update("""
                INSERT INTO player_participation_period
                    (player_id,game_type,active_from,inactive_from,created_at,updated_at)
                VALUES (?,?,?,NULL,?,?)
                """, PLAYER_ID, gameType, activeFrom,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    private void insertSolvedRange(LocalDate first, LocalDate last, long sourceBase) {
        long offset = 0;
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            insertSolved(day, sourceBase + offset++);
        }
    }

    private void insertSolved(LocalDate gameDate, long sourceMessageId) {
        Long resultId = jdbc.queryForObject("""
                INSERT INTO game_result
                    (player_id,game_type,game_date,solved,attempts_used,max_attempts,duration_seconds,
                     normalized_board,raw_share_text,parser_version,created_at,updated_at)
                VALUES (?,'GRIDWORDS',?,TRUE,3,6,60,'ABCDE','share','gridwords-share-v1',?,?)
                RETURNING id
                """, Long.class, PLAYER_ID, gameDate,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO submission
                    (source_message_id,guild_id,channel_id,author_player_id,raw_message_content,
                     processing_state,game_result_id,received_at,updated_at)
                VALUES (?,?,?,?,?,'RESULT_STORED',?,?,?)
                """, sourceMessageId, GUILD_ID, CHANNEL_ID, PLAYER_ID, "share", resultId,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    private void readyBootstrap(Clock clock) {
        PostgresRecordBootstrapStore bootstraps = new PostgresRecordBootstrapStore(jdbc, clock);
        RecordBootstrapKey key = new RecordBootstrapKey(GUILD_ID, catalog.version());
        bootstraps.register(key);
        var claim = bootstraps.claim(key,
                new RecordLeaseClaimRequest(clock.instant(), clock.instant().plusSeconds(60))).orElseThrow();
        assertThat(bootstraps.markSucceeded(key, claim.token(), clock.instant().plusSeconds(1))).isTrue();
    }

    private void seedSucceeded(PostgresRecordDayCloseStore work, LocalDate date, Instant claimedAt) {
        RecordDayCloseKey key = key(date);
        work.register(key);
        var claim = work.claim(key, new RecordLeaseClaimRequest(claimedAt, claimedAt.plusSeconds(60))).orElseThrow();
        assertThat(work.markSucceeded(key, claim.token(), claimedAt.plusSeconds(1))).isTrue();
    }

    private RecordDayCloseKey key(LocalDate date) {
        return new RecordDayCloseKey(GUILD_ID, catalog.version(), date);
    }

    private LocalDate withoutPerfectEnd() {
        return jdbc.queryForObject("""
                SELECT streak_end_date FROM record_state
                WHERE guild_id=? AND definition_key='streak.without-perfect-day.personal'
                  AND scope_type='PERSONAL' AND scope_key='player:1'
                """, (rs, row) -> rs.getObject(1, LocalDate.class), GUILD_ID);
    }

    private int count(String table) {
        if (!java.util.Set.of("record_state", "record_event", "record_announcement").contains(table)) {
            throw new IllegalArgumentException("unsupported table");
        }
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private final class FailSecondTerminalStore extends PostgresRecordDayCloseStore {
        private int calls;

        private FailSecondTerminalStore(JdbcTemplate jdbc, Clock clock) {
            super(jdbc, clock);
        }

        @Override
        public boolean markSucceeded(RecordDayCloseKey key, UUID token, Instant completedAt) {
            calls++;
            return calls == 2 ? false : super.markSucceeded(key, token, completedAt);
        }
    }
}
