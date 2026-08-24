package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.application.achievement.AchievementReconciliationService;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvaluator;
import de.venomenon.gridwordsbot.domain.achievement.AchievementHistorySnapshot;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import de.venomenon.gridwordsbot.port.out.AchievementHistoryQuery;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresAchievementReconciliationIT {
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
    void loadsCanonicalParticipantHistoryAndPersistsOneCrossGameLiveBatch() {
        insertPlayerAndParticipation();
        insertResult(101, "GRIDWORDS", true, 1, Instant.parse("2026-08-07T21:30:00Z"));
        insertResult(102, "QUADWORDS", true, 4, Instant.parse("2026-08-07T21:31:00Z"));
        AchievementReconciliationService service = service(jdbc);

        var result = service.reconcile(liveRequest("submission:102"));

        List<AchievementHistorySnapshot.Result> history = new PostgresAchievementHistoryQuery(jdbc)
                .load(GUILD_ID, PARTICIPANT_ID).results();
        assertThat(history)
                .extracting(AchievementHistorySnapshot.Result::receivedAt)
                .containsExactly(
                        Instant.parse("2026-08-07T21:30:00Z"),
                        Instant.parse("2026-08-07T21:31:00Z"));
        assertThat(history.getFirst().gridWordsBoard()).hasValueSatisfying(board ->
                assertThat(board.canonicalText()).isEqualTo("🟩🟩🟩🟩🟩"));
        assertThat(history.get(1).quadWordsBoards()).isEmpty();
        assertThat(result.liveUnlockBatch()).isPresent();
        assertThat(state("crossgame.participation.1").write().status()).isEqualTo(AchievementAwardState.Status.ACTIVE);
        assertThat(state("crossgame.success.1").write().status()).isEqualTo(AchievementAwardState.Status.ACTIVE);
        assertThat(state("situational.crossgame.perfect_double").write().status()).isEqualTo(AchievementAwardState.Status.ACTIVE);
        assertThat(state("timing.after_2300").write().status()).isEqualTo(AchievementAwardState.Status.ACTIVE);
        assertThat(new PostgresAchievementAwardStateStore(jdbc, CLOCK).find(
                new AchievementAwardState.Key(
                        GUILD_ID, PARTICIPANT_ID,
                        new AchievementKey("situational.quadwords.consecutive_board_attempts")))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_announcement", Integer.class)).isEqualTo(1);

        AchievementAnnouncement.Snapshot announcement = pendingAnnouncement();
        assertThat(announcement.registration().type()).isEqualTo(AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH);
        assertThat(announcement.registration().channelId()).isEqualTo(CHANNEL_ID);
        assertThat(announcementItems(announcement.registration().key())).isNotEmpty();
    }

    @Test
    void concurrentFirstReconciliationsAgainstPostgresCreateOneStateEventAndBatchPerLogicalFact() throws Exception {
        insertPlayerAndParticipation();
        insertResult(101, "GRIDWORDS", true, 1, Instant.parse("2026-08-07T21:30:00Z"));
        CyclicBarrier historiesLoaded = new CyclicBarrier(2);
        AchievementHistoryQuery firstHistory = synchronizedHistory(new PostgresAchievementHistoryQuery(isolatedJdbc()), historiesLoaded);
        AchievementHistoryQuery secondHistory = synchronizedHistory(new PostgresAchievementHistoryQuery(isolatedJdbc()), historiesLoaded);
        AchievementReconciliationService first = service(isolatedJdbc(), firstHistory);
        AchievementReconciliationService second = service(isolatedJdbc(), secondHistory);

        runConcurrently(
                () -> first.reconcile(liveRequest("submission:101")),
                () -> second.reconcile(liveRequest("submission:101")));

        int activeStates = jdbc.queryForObject("SELECT count(*) FROM achievement_award_state WHERE award_status='ACTIVE'", Integer.class);
        assertThat(activeStates).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_event", Integer.class)).isEqualTo(activeStates);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_announcement", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT achievement_key FROM achievement_award_state
                    GROUP BY achievement_key HAVING count(*) > 1
                ) duplicates
                """, Integer.class)).isZero();
    }

    @Test
    void correctionReducesPendingBatchAndPreservesAppendOnlyInvalidationAudit() {
        insertPlayerAndParticipation();
        long resultId = insertResult(101, "GRIDWORDS", true, 1, Instant.parse("2026-08-07T21:30:00Z"));
        AchievementReconciliationService service = service(jdbc);
        service.reconcile(liveRequest("submission:101"));
        AchievementAnnouncement.Snapshot announcement = pendingAnnouncement();
        int initialItems = announcementItems(announcement.registration().key()).size();

        jdbc.update("UPDATE game_result SET solved=FALSE, attempts_used=NULL, updated_at=? WHERE id=?",
                Timestamp.from(NOW.plusSeconds(1)), resultId);
        var correction = service.reconcile(correctionRequest());

        assertThat(correction.transitions()).anySatisfy(transition -> {
            assertThat(transition.achievementKey().value()).isEqualTo("streak.success.1.gridwords");
            assertThat(transition.type()).isEqualTo(AchievementReconciliationService.TransitionType.INVALIDATE);
        });
        assertThat(state("streak.success.1.gridwords").write().status())
                .isEqualTo(AchievementAwardState.Status.INVALIDATED);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM achievement_event
                 WHERE achievement_key='streak.success.1.gridwords' AND event_type='INVALIDATED'
                """, Integer.class)).isEqualTo(1);
        assertThat(announcementItems(announcement.registration().key()).size()).isLessThan(initialItems);
        assertThat(pendingAnnouncement().deliveryState()).isEqualTo(AchievementAnnouncement.DeliveryState.OPEN);
    }

    @Test
    void canonicalGridBoardCorrectionInvalidatesAndReactivatesTheNewPatternAward() {
        insertPlayerAndParticipation();
        long resultId = insertResult(101, "GRIDWORDS", false, null, Instant.parse("2026-08-07T21:30:00Z"));
        String repeatedPattern = String.join("\n",
                "⬜⬜🟨⬜🟩", "⬜⬜🟨⬜🟩", "⬜⬜🟨⬜🟩",
                "🟨⬜⬜⬜⬜", "⬜🟨⬜⬜⬜", "⬜⬜🟨⬜⬜");
        jdbc.update("UPDATE game_result SET normalized_board=? WHERE id=?", repeatedPattern, resultId);
        AchievementReconciliationService service = service(jdbc);

        service.reconcile(liveRequest("submission:101"));
        assertThat(state("situational.repeated_pattern.gridwords").write().status())
                .isEqualTo(AchievementAwardState.Status.ACTIVE);

        jdbc.update("UPDATE game_result SET normalized_board=? WHERE id=?", String.join("\n",
                "⬜⬜🟨⬜🟩", "⬜⬜🟨⬜🟩", "⬜🟨🟨⬜🟩",
                "🟨⬜⬜⬜⬜", "⬜🟨⬜⬜⬜", "⬜⬜🟨⬜⬜"), resultId);
        service.reconcile(correctionRequest());
        assertThat(state("situational.repeated_pattern.gridwords").write().status())
                .isEqualTo(AchievementAwardState.Status.INVALIDATED);

        jdbc.update("UPDATE game_result SET normalized_board=? WHERE id=?", repeatedPattern, resultId);
        service.reconcile(correctionRequest());
        assertThat(state("situational.repeated_pattern.gridwords").write().status())
                .isEqualTo(AchievementAwardState.Status.ACTIVE);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM achievement_event
                 WHERE achievement_key='situational.repeated_pattern.gridwords' AND event_type='INVALIDATED'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM achievement_event
                 WHERE achievement_key='situational.repeated_pattern.gridwords' AND event_type='REACTIVATED'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void alreadySynchronizedUnlockIsNotPreparedAgainAfterReactivation() {
        insertPlayerAndParticipation();
        long resultId = insertResult(101, "GRIDWORDS", true, 1, Instant.parse("2026-08-07T21:30:00Z"));
        AchievementReconciliationService service = service(jdbc);
        service.reconcile(liveRequest("submission:101"));
        bootstrapSucceeded();
        synchronize(pendingAnnouncement());

        jdbc.update("UPDATE game_result SET solved=FALSE, attempts_used=NULL, updated_at=? WHERE id=?",
                Timestamp.from(NOW.plusSeconds(1)), resultId);
        service.reconcile(correctionRequest());
        jdbc.update("UPDATE game_result SET solved=TRUE, attempts_used=1, updated_at=? WHERE id=?",
                Timestamp.from(NOW.plusSeconds(2)), resultId);
        var reactivated = service.reconcile(liveRequest("correction:101:reactivate"));

        assertThat(reactivated.transitions()).anySatisfy(transition -> {
            assertThat(transition.achievementKey().value()).isEqualTo("streak.success.1.gridwords");
            assertThat(transition.type()).isEqualTo(AchievementReconciliationService.TransitionType.REACTIVATE);
        });
        assertThat(reactivated.liveUnlockBatch()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_announcement", Integer.class)).isEqualTo(1);
    }

    @Test
    void unknownInfrastructureFailureEscapesAndRollsBackTheCoupledProjection() {
        insertPlayerAndParticipation();
        insertResult(101, "GRIDWORDS", true, 1, Instant.parse("2026-08-07T21:30:00Z"));
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("event store unavailable");
        AchievementEventStore failingEvents = new AchievementEventStore() {
            @Override
            public de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact.AppendResult append(
                    de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact.Draft draft) {
                throw failure;
            }

            @Override
            public Optional<de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact.Snapshot> find(UUID eventId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact.Snapshot> findByIdempotencyKey(
                    String idempotencyKey) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact.Snapshot> findByParticipant(
                    long guildId, long participantId) {
                throw new UnsupportedOperationException();
            }
        };

        assertThatThrownBy(() -> service(jdbc, new PostgresAchievementHistoryQuery(jdbc), failingEvents)
                .reconcile(liveRequest("submission:101")))
                .isSameAs(failure);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_award_state", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_event", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_announcement", Integer.class)).isZero();
    }

    private AchievementReconciliationService service(JdbcTemplate template) {
        return service(template, new PostgresAchievementHistoryQuery(template));
    }

    private AchievementReconciliationService service(JdbcTemplate template, AchievementHistoryQuery history) {
        return service(template, history, new PostgresAchievementEventStore(template, CLOCK));
    }

    private AchievementReconciliationService service(
            JdbcTemplate template, AchievementHistoryQuery history, AchievementEventStore events) {
        AchievementDefinitionCatalog catalog = AchievementDefinitionCatalog.achievementsV2();
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(template.getDataSource()));
        AchievementTransactionRunner runner = new AchievementTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return transactions.execute(status -> work.get());
            }
        };
        return new AchievementReconciliationService(
                history,
                new AchievementEvaluator(catalog),
                catalog,
                new PostgresAchievementAwardStateStore(template, CLOCK),
                events,
                new PostgresAchievementAnnouncementStore(template, CLOCK),
                runner,
                CLOCK,
                ZoneId.of("Europe/Berlin"));
    }

    private AchievementHistoryQuery synchronizedHistory(AchievementHistoryQuery delegate, CyclicBarrier barrier) {
        return (guildId, participantId) -> {
            var snapshot = delegate.load(guildId, participantId);
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException("concurrent history synchronization failed", exception);
            }
            return snapshot;
        };
    }

    private AchievementReconciliationService.ReconciliationRequest liveRequest(String trigger) {
        return new AchievementReconciliationService.ReconciliationRequest(
                GUILD_ID,
                PARTICIPANT_ID,
                AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION,
                Optional.of(new AchievementReconciliationService.LiveAnnouncementTarget(CHANNEL_ID, trigger)));
    }

    private AchievementReconciliationService.ReconciliationRequest correctionRequest() {
        return new AchievementReconciliationService.ReconciliationRequest(
                GUILD_ID,
                PARTICIPANT_ID,
                AchievementEventFact.ProcessingOrigin.NORMAL_CORRECTION,
                Optional.empty());
    }

    private AchievementAwardState.Snapshot state(String key) {
        return new PostgresAchievementAwardStateStore(jdbc, CLOCK)
                .find(new AchievementAwardState.Key(GUILD_ID, PARTICIPANT_ID, new AchievementKey(key)))
                .orElseThrow();
    }

    private AchievementAnnouncement.Snapshot pendingAnnouncement() {
        return new PostgresAchievementAnnouncementStore(jdbc, CLOCK)
                .findPending(GUILD_ID, PARTICIPANT_ID)
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private List<UUID> announcementItems(AchievementAnnouncement.Key key) {
        return new PostgresAchievementAnnouncementStore(jdbc, CLOCK)
                .findItems(key)
                .stream()
                .map(AchievementAnnouncement.Item::eventId)
                .toList();
    }

    private void synchronize(AchievementAnnouncement.Snapshot announcement) {
        PostgresAchievementAnnouncementStore announcements = new PostgresAchievementAnnouncementStore(jdbc, CLOCK);
        AchievementWork.LeaseClaim claim = announcements.claim(
                announcement.registration().key(),
                new AchievementWork.LeaseClaimRequest(NOW, NOW.plusSeconds(60))).orElseThrow();
        assertThat(announcements.markDelivered(announcement.registration().key(), claim.token(), 999L, NOW)).isTrue();
        assertThat(announcements.markSynchronized(announcement.registration().key(), claim.token(), NOW)).isTrue();
    }

    private void bootstrapSucceeded() {
        PostgresAchievementBootstrapStore bootstraps = new PostgresAchievementBootstrapStore(jdbc, CLOCK);
        AchievementWork.BootstrapKey key = new AchievementWork.BootstrapKey(
                GUILD_ID, AchievementDefinitionCatalog.achievementsV2().version());
        bootstraps.register(key);
        AchievementWork.LeaseClaim claim = bootstraps.claim(
                key, new AchievementWork.LeaseClaimRequest(NOW, NOW.plusSeconds(60))).orElseThrow();
        assertThat(bootstraps.markSucceeded(key, claim.token(), NOW)).isTrue();
    }

    private void insertPlayerAndParticipation() {
        jdbc.update("""
                INSERT INTO player (discord_user_id,display_name,active,administrator,reminder_opt_in,created_at,updated_at)
                VALUES (?,?,TRUE,FALSE,TRUE,?,?)
                """, PARTICIPANT_ID, "Achievement Player", Timestamp.from(NOW), Timestamp.from(NOW));
        for (String game : List.of("GRIDWORDS", "QUADWORDS")) {
            jdbc.update("""
                    INSERT INTO player_participation_period (player_id,game_type,active_from,inactive_from,created_at,updated_at)
                    VALUES (?,?,?,NULL,?,?)
                    """, PARTICIPANT_ID, game, LocalDate.of(2026, 8, 7), Timestamp.from(NOW), Timestamp.from(NOW));
        }
    }

    private long insertResult(long sourceMessageId, String game, boolean solved, Integer attempts, Instant receivedAt) {
        int maxAttempts = game.equals("GRIDWORDS") ? 6 : 9;
        String board = game.equals("GRIDWORDS") ? "🟩🟩🟩🟩🟩" : null;
        Long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id,game_type,game_date,solved,attempts_used,max_attempts,duration_seconds,
                    normalized_board,raw_share_text,parser_version,created_at,updated_at)
                VALUES (?,?,?,?,?,?,? ,?,'share',?, ?, ?)
                RETURNING id
                """, Long.class,
                PARTICIPANT_ID, game, LocalDate.of(2026, 8, 7), solved, attempts, maxAttempts, 60,
                board, game.equals("GRIDWORDS") ? "gridwords-share-v1" : "quadwords-share-v2",
                Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id,guild_id,channel_id,author_player_id,raw_message_content,
                    processing_state,game_result_id,received_at,updated_at)
                VALUES (?,?,?,?,?,'RESULT_STORED',?,?,?)
                """, sourceMessageId, GUILD_ID, CHANNEL_ID, PARTICIPANT_ID, "share", resultId,
                Timestamp.from(receivedAt), Timestamp.from(NOW));
        return resultId;
    }

    private JdbcTemplate isolatedJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static <T> List<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<T> one = executor.submit(first);
            Future<T> two = executor.submit(second);
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }
}
