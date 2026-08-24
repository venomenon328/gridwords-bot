package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.achievement.AchievementReconciliationService;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvaluator;
import de.venomenon.gridwordsbot.domain.achievement.AchievementHistorySnapshot;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.port.out.AchievementHistoryQuery;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresAchievementStaleReconciliationIT {
    private static final long GUILD_ID = 10L;
    private static final long CHANNEL_ID = 20L;
    private static final long PARTICIPANT_ID = 30L;
    private static final Instant NOW = Instant.parse("2026-08-08T07:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    void migrate() throws Exception {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE player CASCADE");
        jdbc.update("DELETE FROM achievement_announcement_item");
        jdbc.update("DELETE FROM achievement_announcement");
        jdbc.update("DELETE FROM achievement_event");
        jdbc.update("DELETE FROM achievement_award_state");
    }

    @Test
    void staleEvaluationCannotReactivateStateAfterNewerCorrection() throws Exception {
        insertPlayerAndParticipation();
        long resultId = insertSolvedGridWordsResult();
        service(jdbc, new PostgresAchievementHistoryQuery(jdbc)).reconcile(liveRequest());

        CountDownLatch staleLoaded = new CountDownLatch(1);
        CountDownLatch releaseStale = new CountDownLatch(1);
        AtomicBoolean firstLoad = new AtomicBoolean(true);
        AchievementHistoryQuery delegate = new PostgresAchievementHistoryQuery(isolatedJdbc());
        AchievementHistoryQuery staleHistory = (guildId, participantId) -> {
            AchievementHistorySnapshot snapshot = delegate.load(guildId, participantId);
            if (firstLoad.compareAndSet(true, false)) {
                staleLoaded.countDown();
                try {
                    if (!releaseStale.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to release stale achievement history");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while holding stale achievement history", exception);
                }
            }
            return snapshot;
        };
        AchievementReconciliationService staleService = service(isolatedJdbc(), staleHistory);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<AchievementReconciliationService.ReconciliationResult> stale = executor.submit(
                    () -> staleService.reconcile(replayRequest()));
            assertThat(staleLoaded.await(10, TimeUnit.SECONDS)).isTrue();

            jdbc.update("UPDATE game_result SET solved=FALSE, attempts_used=NULL, updated_at=? WHERE id=?",
                    Timestamp.from(NOW.plusSeconds(1)), resultId);
            service(jdbc, new PostgresAchievementHistoryQuery(jdbc)).reconcile(correctionRequest());

            assertThat(state("streak.success.1.gridwords").write().status())
                    .isEqualTo(AchievementAwardState.Status.INVALIDATED);

            releaseStale.countDown();
            stale.get(20, TimeUnit.SECONDS);
        } finally {
            releaseStale.countDown();
        }

        assertThat(state("streak.success.1.gridwords").write().status())
                .isEqualTo(AchievementAwardState.Status.INVALIDATED);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM achievement_event
                 WHERE achievement_key='streak.success.1.gridwords' AND event_type='REACTIVATED'
                """, Integer.class)).isZero();
    }

    private AchievementReconciliationService service(JdbcTemplate template, AchievementHistoryQuery history) {
        AchievementDefinitionCatalog catalog = AchievementDefinitionCatalog.achievementsV2();
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(template.getDataSource()));
        AchievementTransactionRunner runner = new AchievementTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return transactions.execute(status -> work.get());
            }

            @Override
            public <T> T inParticipantTransaction(long participantId, java.util.function.Supplier<T> work) {
                return transactions.execute(status -> {
                    Long lockedParticipant = template.queryForObject(
                            "SELECT discord_user_id FROM player WHERE discord_user_id=? FOR UPDATE",
                            Long.class,
                            participantId);
                    if (lockedParticipant == null || lockedParticipant.longValue() != participantId) {
                        throw new IllegalStateException("achievement participant lock could not be acquired");
                    }
                    return work.get();
                });
            }
        };
        return new AchievementReconciliationService(
                history,
                new AchievementEvaluator(catalog),
                catalog,
                new PostgresAchievementAwardStateStore(template, CLOCK),
                new PostgresAchievementEventStore(template, CLOCK),
                new PostgresAchievementAnnouncementStore(template, CLOCK),
                runner,
                CLOCK,
                ZoneId.of("Europe/Berlin"));
    }

    private AchievementReconciliationService.ReconciliationRequest liveRequest() {
        return new AchievementReconciliationService.ReconciliationRequest(
                GUILD_ID,
                PARTICIPANT_ID,
                AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION,
                Optional.of(new AchievementReconciliationService.LiveAnnouncementTarget(CHANNEL_ID, "submission:101")));
    }

    private AchievementReconciliationService.ReconciliationRequest correctionRequest() {
        return new AchievementReconciliationService.ReconciliationRequest(
                GUILD_ID,
                PARTICIPANT_ID,
                AchievementEventFact.ProcessingOrigin.NORMAL_CORRECTION,
                Optional.empty());
    }

    private AchievementReconciliationService.ReconciliationRequest replayRequest() {
        return new AchievementReconciliationService.ReconciliationRequest(
                GUILD_ID,
                PARTICIPANT_ID,
                AchievementEventFact.ProcessingOrigin.REPLAY,
                Optional.empty());
    }

    private AchievementAwardState.Snapshot state(String key) {
        return new PostgresAchievementAwardStateStore(jdbc, CLOCK)
                .find(new AchievementAwardState.Key(GUILD_ID, PARTICIPANT_ID, new AchievementKey(key)))
                .orElseThrow();
    }

    private void insertPlayerAndParticipation() {
        jdbc.update("""
                INSERT INTO player (discord_user_id,display_name,active,administrator,reminder_opt_in,created_at,updated_at)
                VALUES (?,?,TRUE,FALSE,TRUE,?,?)
                """, PARTICIPANT_ID, "Achievement Player", Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO player_participation_period (player_id,game_type,active_from,inactive_from,created_at,updated_at)
                VALUES (?,'GRIDWORDS',?,NULL,?,?)
                """, PARTICIPANT_ID, LocalDate.of(2026, 8, 7), Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private long insertSolvedGridWordsResult() {
        Long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id,game_type,game_date,solved,attempts_used,max_attempts,duration_seconds,
                    normalized_board,raw_share_text,parser_version,created_at,updated_at)
                VALUES (?,'GRIDWORDS',?,TRUE,1,6,60,'🟩🟩🟩🟩🟩','share','gridwords-share-v1',?,?)
                RETURNING id
                """, Long.class, PARTICIPANT_ID, LocalDate.of(2026, 8, 7), Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id,guild_id,channel_id,author_player_id,raw_message_content,
                    processing_state,game_result_id,received_at,updated_at)
                VALUES (101,?,?,?,'share','RESULT_STORED',?,?,?)
                """, GUILD_ID, CHANNEL_ID, PARTICIPANT_ID, resultId,
                Timestamp.from(Instant.parse("2026-08-07T21:30:00Z")), Timestamp.from(NOW));
        return resultId;
    }

    private JdbcTemplate isolatedJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }
}
