package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.application.achievement.AchievementBootstrapCoordinator;
import de.venomenon.gridwordsbot.application.achievement.AchievementResultLifecycle;
import de.venomenon.gridwordsbot.application.achievement.AchievementReconciliationService;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvaluator;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementBootstrapStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
class PostgresAchievementBootstrapIT {
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
        jdbc.update("DELETE FROM achievement_bootstrap_state");
    }

    @Test
    void bootstrapReconstructsInactiveHistoricalParticipantsAndKeepsOneRefreshableIntroduction() {
        insertPlayer(PARTICIPANT_ID, false);
        insertParticipation(PARTICIPANT_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2));
        insertSolvedGridWords(PARTICIPANT_ID, 101L, LocalDate.of(2026, 8, 1));

        AchievementBootstrapCoordinator coordinator = coordinator(jdbc, new PostgresPersistenceAdapter(jdbc, CLOCK, berlin()));

        assertThat(coordinator.run(GUILD_ID, CHANNEL_ID))
                .isEqualTo(AchievementBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        assertThat(coordinator.run(GUILD_ID, CHANNEL_ID))
                .isEqualTo(AchievementBootstrapCoordinator.BootstrapRunResult.NOT_CLAIMED);

        AchievementWork.BootstrapSnapshot bootstrap = bootstrapStore(jdbc).find(bootstrapKey()).orElseThrow();
        assertThat(bootstrap.state()).isEqualTo(AchievementWork.State.SUCCEEDED);
        assertThat(activeAwardCount()).isPositive();
        assertThat(countAnnouncements(AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION)).isEqualTo(1);

        AchievementAnnouncement.Snapshot introduction = introduction();
        assertThat(announcementStore(jdbc).findItems(introduction.registration().key()))
                .extracting(AchievementAnnouncement.Item::eventId)
                .hasSize(activeAwardCount());
        assertThat(announcementKeys(introduction)).containsExactlyElementsOf(activeKeysInCatalogOrder());
    }

    @Test
    void v2BootstrapReconcilesBoardPatternsSilentlyAndPreservesV1AuditAndOpenIntroduction() {
        insertPlayer(PARTICIPANT_ID, true);
        insertParticipation(PARTICIPANT_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 7), null);
        long resultId = insertSolvedGridWords(PARTICIPANT_ID, 101L, LocalDate.of(2026, 8, 7));
        jdbc.update("UPDATE game_result SET solved=FALSE, attempts_used=NULL, normalized_board=? WHERE id=?", String.join("\n",
                "⬜⬜🟨⬜🟩", "⬜⬜🟨⬜🟩", "⬜⬜🟨⬜🟩",
                "🟨⬜⬜⬜⬜", "⬜🟨⬜⬜⬜", "🟩🟩🟩🟩🟩"), resultId);

        AchievementBootstrapCoordinator v1 = coordinator(jdbc, new PostgresPersistenceAdapter(jdbc, CLOCK, berlin()));
        assertThat(v1.run(GUILD_ID, CHANNEL_ID)).isEqualTo(AchievementBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        AchievementAnnouncement.Snapshot v1Introduction = introduction();
        List<String> v1Items = announcementKeys(v1Introduction);
        int v1Events = jdbc.queryForObject(
                "SELECT count(*) FROM achievement_event WHERE definition_version='achievements-v1'", Integer.class);

        AchievementDefinitionCatalog v2Catalog = AchievementDefinitionCatalog.achievementsV2();
        AchievementTransactionRunner v2Runner = runner(jdbc);
        AchievementBootstrapStore bootstraps = bootstrapStore(jdbc);
        AchievementBootstrapCoordinator v2 = new AchievementBootstrapCoordinator(
                bootstraps,
                v2Runner,
                new PostgresPersistenceAdapter(jdbc, CLOCK, berlin()),
                lifecycle(jdbc, noSubmissions(), bootstraps, v2Runner, v2Catalog),
                v2Catalog,
                CLOCK,
                Duration.ofMinutes(2),
                Duration.ofMinutes(1));

        assertThat(v2.run(GUILD_ID, CHANNEL_ID)).isEqualTo(AchievementBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        assertThat(v2.run(GUILD_ID, CHANNEL_ID)).isEqualTo(AchievementBootstrapCoordinator.BootstrapRunResult.NOT_CLAIMED);
        assertThat(bootstraps.find(bootstrapKey(AchievementDefinitionCatalog.achievementsV1())).orElseThrow().state())
                .isEqualTo(AchievementWork.State.SUCCEEDED);
        assertThat(bootstraps.find(bootstrapKey(v2Catalog)).orElseThrow().state())
                .isEqualTo(AchievementWork.State.SUCCEEDED);
        assertThat(new PostgresAchievementAwardStateStore(jdbc, CLOCK).find(new AchievementAwardState.Key(
                GUILD_ID, PARTICIPANT_ID, new AchievementKey("situational.repeated_pattern.gridwords"))))
                .hasValueSatisfying(state -> assertThat(state.write().status()).isEqualTo(AchievementAwardState.Status.ACTIVE));
        assertThat(countAnnouncements(AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION)).isEqualTo(1);
        assertThat(introduction().registration().definitionVersion())
                .isEqualTo(AchievementDefinitionCatalog.achievementsV1().version());
        assertThat(announcementKeys(introduction())).containsExactlyElementsOf(v1Items);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM achievement_event WHERE definition_version='achievements-v1'", Integer.class))
                .isEqualTo(v1Events);
        assertThat(countAnnouncements(AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH)).isZero();
    }

    @Test
    void restartAfterPartialBootstrapStartsFromTheFrontWithoutDuplicatingIntroductions() {
        insertPlayer(PARTICIPANT_ID, true);
        insertPlayer(PARTICIPANT_ID + 1, false);
        insertParticipation(PARTICIPANT_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 7), null);
        insertParticipation(PARTICIPANT_ID + 1, GameType.GRIDWORDS, LocalDate.of(2026, 8, 7), null);
        insertSolvedGridWords(PARTICIPANT_ID, 101L, LocalDate.of(2026, 8, 7));
        insertSolvedGridWords(PARTICIPANT_ID + 1, 102L, LocalDate.of(2026, 8, 7));

        AchievementBootstrapStore bootstraps = bootstrapStore(jdbc);
        AchievementTransactionRunner failingFence = failOnSecondBootstrapFence(runner(jdbc));
        AchievementResultLifecycle firstLifecycle = lifecycle(jdbc, noSubmissions(), bootstraps, failingFence);
        AchievementBootstrapCoordinator interrupted = new AchievementBootstrapCoordinator(
                bootstraps,
                failingFence,
                new PostgresPersistenceAdapter(jdbc, CLOCK, berlin()),
                firstLifecycle,
                AchievementDefinitionCatalog.achievementsV1(),
                CLOCK,
                Duration.ofMinutes(2),
                Duration.ofMinutes(1));

        assertThatThrownBy(() -> interrupted.run(GUILD_ID, CHANNEL_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected bootstrap fence failure");
        assertThat(bootstraps.find(bootstrapKey()).orElseThrow().state())
                .isEqualTo(AchievementWork.State.RETRYABLE);
        assertThat(countAnnouncements(AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION)).isEqualTo(1);

        Clock retryClock = Clock.offset(CLOCK, Duration.ofMinutes(2));
        AchievementTransactionRunner retryRunner = runner(jdbc);
        AchievementBootstrapCoordinator restarted = new AchievementBootstrapCoordinator(
                bootstraps,
                retryRunner,
                new PostgresPersistenceAdapter(jdbc, CLOCK, berlin()),
                lifecycle(jdbc, noSubmissions(), bootstraps, retryRunner),
                AchievementDefinitionCatalog.achievementsV1(),
                retryClock,
                Duration.ofMinutes(2),
                Duration.ofMinutes(1));

        assertThat(restarted.run(GUILD_ID, CHANNEL_ID))
                .isEqualTo(AchievementBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        assertThat(countAnnouncements(AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_event", Integer.class))
                .isEqualTo(activeAwardCount());
    }

    @Test
    void normalResultBeforeBootstrapSuccessUpdatesHistoricalIntroductionWithoutLiveBatch() {
        insertPlayer(PARTICIPANT_ID, true);
        insertParticipation(PARTICIPANT_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 7), null);
        long resultId = insertSolvedGridWords(PARTICIPANT_ID, 101L, LocalDate.of(2026, 8, 7));
        AchievementResultLifecycle lifecycle = lifecycle(jdbc, noSubmissions());

        lifecycle.reconcileNormal(new SubmissionStore.ResultStorageOutcome(
                submission(101L, resultId), SubmissionStore.ResultStorageKind.NEW_RESULT));

        assertThat(countAnnouncements(AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH)).isZero();
        assertThat(countAnnouncements(AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION)).isEqualTo(1);
        assertThat(announcementStore(jdbc).findItems(introduction().registration().key())).isNotEmpty();

        AchievementBootstrapCoordinator coordinator = coordinator(jdbc, new PostgresPersistenceAdapter(jdbc, CLOCK, berlin()));
        assertThat(coordinator.run(GUILD_ID, CHANNEL_ID))
                .isEqualTo(AchievementBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        assertThat(countAnnouncements(AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION)).isEqualTo(1);
        assertThat(countAnnouncements(AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH)).isZero();
    }

    @Test
    void restartRecoveryFromResultStoredReconcilesSilentlyAfterSuccessfulBootstrap() {
        insertPlayer(PARTICIPANT_ID, true);
        insertParticipation(PARTICIPANT_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 7), null);
        long resultId = insertSolvedGridWords(PARTICIPANT_ID, 101L, LocalDate.of(2026, 8, 7));
        markBootstrapSucceeded();

        AchievementResultLifecycle lifecycle = lifecycle(jdbc, new PostgresPersistenceAdapter(jdbc, CLOCK, berlin()));
        lifecycle.recoverPendingResults();
        lifecycle.recoverPendingResults();

        assertThat(activeAwardCount()).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_event", Integer.class))
                .isEqualTo(activeAwardCount());
        assertThat(countAnnouncements(AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH)).isZero();
        assertThat(countAnnouncements(AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION)).isZero();
        assertThat(submission(101L, resultId).state()).isEqualTo(SubmissionStore.SubmissionState.RESULT_STORED);
    }

    @Test
    void normalCorrectionAfterBootstrapInvalidatesWithoutCreatingARevocationProjection() {
        insertPlayer(PARTICIPANT_ID, true);
        insertParticipation(PARTICIPANT_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 7), null);
        long resultId = insertSolvedGridWords(PARTICIPANT_ID, 101L, LocalDate.of(2026, 8, 7));
        AchievementBootstrapCoordinator coordinator = coordinator(jdbc, new PostgresPersistenceAdapter(jdbc, CLOCK, berlin()));
        assertThat(coordinator.run(GUILD_ID, CHANNEL_ID))
                .isEqualTo(AchievementBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        int activeBeforeCorrection = activeAwardCount();

        jdbc.update("UPDATE game_result SET solved=FALSE, attempts_used=NULL WHERE id=?", resultId);
        lifecycle(jdbc, noSubmissions()).reconcileNormal(new SubmissionStore.ResultStorageOutcome(
                submission(101L, resultId), SubmissionStore.ResultStorageKind.REPLACED_RESULT));

        assertThat(activeAwardCount()).isLessThan(activeBeforeCorrection);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_event WHERE event_type='INVALIDATED'", Integer.class))
                .isPositive();
        assertThat(countAnnouncements(AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH)).isZero();
    }

    @Test
    void postgresFenceAssignsConcurrentEndEdgeTriggerToExactlyOneLiveProjection() throws Exception {
        insertPlayer(PARTICIPANT_ID, true);
        insertParticipation(PARTICIPANT_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 7), null);
        long resultId = insertSolvedGridWords(PARTICIPANT_ID, 101L, LocalDate.of(2026, 8, 7));

        CountDownLatch successEntered = new CountDownLatch(1);
        CountDownLatch releaseSuccess = new CountDownLatch(1);
        JdbcTemplate bootstrapTemplate = isolatedJdbc();
        AchievementBootstrapStore delayedBootstraps = delayingSuccessStore(
                bootstrapStore(bootstrapTemplate), successEntered, releaseSuccess);
        AchievementTransactionRunner bootstrapRunner = runner(bootstrapTemplate);
        AchievementResultLifecycle bootstrapLifecycle = lifecycle(
                bootstrapTemplate, noSubmissions(), delayedBootstraps, bootstrapRunner);
        AchievementBootstrapCoordinator coordinator = new AchievementBootstrapCoordinator(
                delayedBootstraps,
                bootstrapRunner,
                noBootstrapParticipants(),
                bootstrapLifecycle,
                AchievementDefinitionCatalog.achievementsV1(),
                CLOCK,
                Duration.ofMinutes(2),
                Duration.ofMinutes(1));
        AchievementResultLifecycle normalLifecycle = lifecycle(isolatedJdbc(), noSubmissions());

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AchievementBootstrapCoordinator.BootstrapRunResult> bootstrap = executor.submit(
                    () -> coordinator.run(GUILD_ID, CHANNEL_ID));
            assertThat(successEntered.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> normal = executor.submit(() -> normalLifecycle.reconcileNormal(
                    new SubmissionStore.ResultStorageOutcome(
                            submission(101L, resultId), SubmissionStore.ResultStorageKind.NEW_RESULT)));
            releaseSuccess.countDown();

            assertThat(bootstrap.get(20, TimeUnit.SECONDS))
                    .isEqualTo(AchievementBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
            normal.get(20, TimeUnit.SECONDS);
        } finally {
            releaseSuccess.countDown();
        }

        assertThat(countAnnouncements(AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION)).isZero();
        assertThat(countAnnouncements(AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH)).isEqualTo(1);
        assertThat(activeAwardCount()).isPositive();
    }

    @Test
    void technicalBootstrapFailureStaysRetryableAndCannotBecomeSuccessful() {
        AchievementBootstrapStore store = bootstrapStore(jdbc);
        PlayerStore unavailablePlayers = new PlayerStore() {
            @Override public StoredPlayer upsert(PlayerUpsert request) { throw new UnsupportedOperationException(); }
            @Override public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) { return Optional.empty(); }
            @Override public List<StoredPlayer> findAllPlayers() { throw new IllegalStateException("database unavailable"); }
        };
        AchievementBootstrapCoordinator coordinator = coordinator(jdbc, unavailablePlayers);

        assertThatThrownBy(() -> coordinator.run(GUILD_ID, CHANNEL_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        AchievementWork.BootstrapSnapshot snapshot = store.find(bootstrapKey()).orElseThrow();
        assertThat(snapshot.state()).isEqualTo(AchievementWork.State.RETRYABLE);
        assertThat(snapshot.failure()).hasValueSatisfying(failure ->
                assertThat(failure.category()).isEqualTo(AchievementWork.FailureCategory.UNKNOWN));
    }

    private AchievementBootstrapCoordinator coordinator(JdbcTemplate template, PlayerStore players) {
        AchievementTransactionRunner runner = runner(template);
        AchievementBootstrapStore bootstraps = bootstrapStore(template);
        AchievementResultLifecycle lifecycle = lifecycle(template, noSubmissions(), bootstraps, runner);
        return new AchievementBootstrapCoordinator(
                bootstraps,
                runner,
                players,
                lifecycle,
                AchievementDefinitionCatalog.achievementsV1(),
                CLOCK,
                Duration.ofMinutes(2),
                Duration.ofMinutes(1));
    }

    private AchievementResultLifecycle lifecycle(JdbcTemplate template, SubmissionStore submissions) {
        return lifecycle(template, submissions, bootstrapStore(template), runner(template));
    }

    private AchievementResultLifecycle lifecycle(
            JdbcTemplate template,
            SubmissionStore submissions,
            AchievementBootstrapStore bootstraps,
            AchievementTransactionRunner runner) {
        return lifecycle(template, submissions, bootstraps, runner, AchievementDefinitionCatalog.achievementsV1());
    }

    private AchievementResultLifecycle lifecycle(
            JdbcTemplate template,
            SubmissionStore submissions,
            AchievementBootstrapStore bootstraps,
            AchievementTransactionRunner runner,
            AchievementDefinitionCatalog catalog) {
        AchievementAwardStateStore awards = new PostgresAchievementAwardStateStore(template, CLOCK);
        AchievementEventStore events = new PostgresAchievementEventStore(template, CLOCK);
        AchievementAnnouncementStore announcements = new PostgresAchievementAnnouncementStore(template, CLOCK);
        AchievementReconciliationService reconciliation = new AchievementReconciliationService(
                new PostgresAchievementHistoryQuery(template),
                new AchievementEvaluator(catalog),
                catalog,
                awards,
                events,
                announcements,
                runner,
                CLOCK,
                berlin());
        return new AchievementResultLifecycle(
                bootstraps, runner, reconciliation, catalog, awards, events, announcements, submissions, CLOCK);
    }

    private AchievementTransactionRunner runner(JdbcTemplate template) {
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(template.getDataSource()));
        return new AchievementTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return transactions.execute(status -> work.get());
            }

            @Override
            public <T> T inParticipantTransaction(long participantId, java.util.function.Supplier<T> work) {
                return transactions.execute(status -> {
                    template.queryForObject("SELECT discord_user_id FROM player WHERE discord_user_id=? FOR UPDATE",
                            Long.class, participantId);
                    return work.get();
                });
            }

            @Override
            public <T> T inBootstrapFenceTransaction(
                    AchievementWork.BootstrapKey key, java.util.function.Supplier<T> work) {
                return transactions.execute(status -> {
                    template.queryForObject("""
                            SELECT 1 FROM achievement_bootstrap_state
                             WHERE guild_id=? AND definition_version=? FOR UPDATE
                            """, Integer.class, key.guildId(), key.definitionVersion().value());
                    return work.get();
                });
            }
        };
    }

    private AchievementTransactionRunner failOnSecondBootstrapFence(AchievementTransactionRunner delegate) {
        AtomicInteger fences = new AtomicInteger();
        return new AchievementTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return delegate.inTransaction(work);
            }

            @Override
            public <T> T inParticipantTransaction(long participantId, java.util.function.Supplier<T> work) {
                return delegate.inParticipantTransaction(participantId, work);
            }

            @Override
            public <T> T inBootstrapFenceTransaction(
                    AchievementWork.BootstrapKey key, java.util.function.Supplier<T> work) {
                if (fences.incrementAndGet() == 2) {
                    throw new IllegalStateException("injected bootstrap fence failure");
                }
                return delegate.inBootstrapFenceTransaction(key, work);
            }
        };
    }

    private AchievementBootstrapStore delayingSuccessStore(
            AchievementBootstrapStore delegate,
            CountDownLatch successEntered,
            CountDownLatch releaseSuccess) {
        return new AchievementBootstrapStore() {
            @Override public AchievementWork.BootstrapSnapshot register(AchievementWork.BootstrapKey key) { return delegate.register(key); }
            @Override public Optional<AchievementWork.BootstrapSnapshot> find(AchievementWork.BootstrapKey key) { return delegate.find(key); }
            @Override public Optional<AchievementWork.LeaseClaim> claim(
                    AchievementWork.BootstrapKey key, AchievementWork.LeaseClaimRequest request) { return delegate.claim(key, request); }
            @Override public boolean renewLease(
                    AchievementWork.BootstrapKey key, UUID token, AchievementWork.LeaseClaimRequest request) {
                return delegate.renewLease(key, token, request);
            }
            @Override public boolean markSucceeded(AchievementWork.BootstrapKey key, UUID token, Instant completedAt) {
                successEntered.countDown();
                try {
                    if (!releaseSuccess.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to release bootstrap success");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while delaying bootstrap success", exception);
                }
                return delegate.markSucceeded(key, token, completedAt);
            }
            @Override public boolean markRetryableFailure(
                    AchievementWork.BootstrapKey key, UUID token, AchievementWork.Failure failure, Instant nextRetryAt) {
                return delegate.markRetryableFailure(key, token, failure, nextRetryAt);
            }
            @Override public boolean markPermanentFailure(
                    AchievementWork.BootstrapKey key, UUID token, AchievementWork.Failure failure, Instant completedAt) {
                return delegate.markPermanentFailure(key, token, failure, completedAt);
            }
        };
    }

    private PlayerStore noBootstrapParticipants() {
        return new PlayerStore() {
            @Override public StoredPlayer upsert(PlayerUpsert request) { throw new UnsupportedOperationException(); }
            @Override public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) { return Optional.empty(); }
            @Override public List<StoredPlayer> findAllPlayers() { return List.of(); }
        };
    }

    private void markBootstrapSucceeded() {
        AchievementBootstrapStore store = bootstrapStore(jdbc);
        store.register(bootstrapKey());
        AchievementWork.LeaseClaim claim = store.claim(bootstrapKey(), leaseRequest()).orElseThrow();
        assertThat(store.markSucceeded(bootstrapKey(), claim.token(), NOW)).isTrue();
    }

    private AchievementWork.BootstrapKey bootstrapKey() {
        return bootstrapKey(AchievementDefinitionCatalog.achievementsV1());
    }

    private AchievementWork.BootstrapKey bootstrapKey(AchievementDefinitionCatalog catalog) {
        return new AchievementWork.BootstrapKey(GUILD_ID, catalog.version());
    }

    private AchievementWork.LeaseClaimRequest leaseRequest() {
        return new AchievementWork.LeaseClaimRequest(NOW, NOW.plus(Duration.ofMinutes(2)));
    }

    private PostgresAchievementBootstrapStore bootstrapStore(JdbcTemplate template) {
        return new PostgresAchievementBootstrapStore(template, CLOCK);
    }

    private PostgresAchievementAnnouncementStore announcementStore(JdbcTemplate template) {
        return new PostgresAchievementAnnouncementStore(template, CLOCK);
    }

    private AchievementAnnouncement.Snapshot introduction() {
        return jdbc.query("SELECT * FROM achievement_announcement WHERE announcement_type='HISTORICAL_INTRODUCTION'",
                AchievementJdbcMapping::announcement).stream().findFirst().orElseThrow();
    }

    private int countAnnouncements(AchievementAnnouncement.Type type) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM achievement_announcement WHERE announcement_type=?",
                Integer.class, type.name());
        return count == null ? 0 : count;
    }

    private int activeAwardCount() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM achievement_award_state WHERE award_status='ACTIVE'", Integer.class);
        return count == null ? 0 : count;
    }

    private List<String> announcementKeys(AchievementAnnouncement.Snapshot announcement) {
        return announcementStore(jdbc).findItems(announcement.registration().key()).stream()
                .map(item -> new PostgresAchievementEventStore(jdbc, CLOCK).find(item.eventId()).orElseThrow())
                .map(event -> event.fact().awardKey().achievementKey().value())
                .toList();
    }

    private List<String> activeKeysInCatalogOrder() {
        List<AchievementKey> active = new PostgresAchievementAwardStateStore(jdbc, CLOCK)
                .findAll(GUILD_ID, PARTICIPANT_ID).stream()
                .filter(state -> state.write().status() == AchievementAwardState.Status.ACTIVE)
                .map(state -> state.key().achievementKey()).toList();
        return AchievementDefinitionCatalog.achievementsV1().definitions().stream()
                .map(definition -> definition.key())
                .filter(active::contains)
                .map(AchievementKey::value)
                .toList();
    }

    private void insertPlayer(long participantId, boolean active) {
        jdbc.update("""
                INSERT INTO player (discord_user_id,display_name,active,administrator,reminder_opt_in,created_at,updated_at)
                VALUES (?,?,?,FALSE,TRUE,?,?)
                """, participantId, "Achievement Player " + participantId, active, Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private void insertParticipation(long participantId, GameType gameType, LocalDate start, LocalDate end) {
        jdbc.update("""
                INSERT INTO player_participation_period (player_id,game_type,active_from,inactive_from,created_at,updated_at)
                VALUES (?,?,?,?,?,?)
                """, participantId, gameType.name(), start, end, Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private long insertSolvedGridWords(long participantId, long sourceMessageId, LocalDate gameDate) {
        Long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id,game_type,game_date,solved,attempts_used,max_attempts,duration_seconds,
                    normalized_board,raw_share_text,parser_version,created_at,updated_at)
                VALUES (?,'GRIDWORDS',?,TRUE,1,6,60,'🟩🟩🟩🟩🟩','share','gridwords-share-v1',?,?)
                RETURNING id
                """, Long.class, participantId, gameDate, Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id,guild_id,channel_id,author_player_id,raw_message_content,
                    processing_state,game_result_id,received_at,updated_at)
                VALUES (?,?,?,?,?,'RESULT_STORED',?,?,?)
                """, sourceMessageId, GUILD_ID, CHANNEL_ID, participantId, "share", resultId,
                Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW));
        return resultId;
    }

    private SubmissionStore.StoredSubmission submission(long sourceMessageId, long resultId) {
        return new PostgresPersistenceAdapter(jdbc, CLOCK, berlin()).findBySourceMessageId(sourceMessageId)
                .filter(stored -> stored.gameResultId().orElseThrow() == resultId)
                .orElseThrow();
    }

    private SubmissionStore noSubmissions() {
        return new SubmissionStore() {
            @Override public StoredSubmission register(SubmissionRegistration registration) { throw new UnsupportedOperationException(); }
            @Override public Optional<StoredSubmission> findBySourceMessageId(long sourceMessageId) { return Optional.empty(); }
            @Override public StoredSubmission storeResult(ResultStorage request) { throw new UnsupportedOperationException(); }
            @Override public StoredSubmission reject(RejectedSubmission request) { throw new UnsupportedOperationException(); }
            @Override public boolean transition(long sourceMessageId, SubmissionState expectedState, SubmissionState targetState) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private JdbcTemplate isolatedJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static ZoneId berlin() {
        return ZoneId.of("Europe/Berlin");
    }
}
