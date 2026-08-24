package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator;
import de.venomenon.gridwordsbot.application.record.RecordStateService;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapProjection;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventAppendResult;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLockVersion;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateInitialization;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdate;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordRetryableFailure;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Coordinator races use separate PostgreSQL connections and transaction managers. The latches deliberately
 * stop workers only at domain boundaries; no test simulates a state-store result in memory.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresRecordBootstrapCoordinatorIT {
    private static final long GUILD_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final RecordBootstrapKey BOOTSTRAP_KEY =
            new RecordBootstrapKey(GUILD_ID, RecordDefinitionVersion.RECORDS_V1);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private final MutableClock clock = new MutableClock(NOW);
    private JdbcTemplate jdbc;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource source = newDataSource();
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
    }

    @BeforeEach
    void cleanAndSeedCanonicalHistory() {
        clock.set(NOW);
        jdbc.update("DELETE FROM record_announcement_message");
        jdbc.update("DELETE FROM record_announcement_event");
        jdbc.update("DELETE FROM record_announcement");
        jdbc.update("DELETE FROM record_event");
        jdbc.update("DELETE FROM record_bootstrap");
        jdbc.update("DELETE FROM record_state");
        jdbc.update("DELETE FROM submission");
        jdbc.update("DELETE FROM game_result");
        jdbc.update("DELETE FROM player_participation_period");
        jdbc.update("DELETE FROM player");

        jdbc.update(
                "INSERT INTO player (discord_user_id,display_name,active,administrator,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                7L,
                "Retired record holder",
                false,
                false,
                NOW.atOffset(ZoneOffset.UTC),
                NOW.atOffset(ZoneOffset.UTC));
        Long resultId = jdbc.queryForObject(
                """
                INSERT INTO game_result (
                    player_id,game_type,game_date,solved,attempts_used,max_attempts,duration_seconds,
                    normalized_board,raw_share_text,parser_version,created_at,updated_at,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0) RETURNING id
                """,
                Long.class,
                7L,
                GameType.GRIDWORDS.name(),
                LocalDate.of(2026, 8, 4),
                true,
                3,
                6,
                50L,
                "\u2b1c",
                "GridWords fixture",
                "test",
                NOW.minusSeconds(60).atOffset(ZoneOffset.UTC),
                NOW.minusSeconds(60).atOffset(ZoneOffset.UTC));
        jdbc.update(
                """
                INSERT INTO submission (
                    source_message_id,guild_id,channel_id,author_player_id,raw_message_content,processing_state,
                    game_result_id,received_at,updated_at,original_deleted_at,version)
                VALUES (?,?,?,?,?,'COMPLETED',?,?,?,?,0)
                """,
                100L,
                GUILD_ID,
                2L,
                7L,
                "GridWords fixture",
                resultId,
                NOW.minusSeconds(60).atOffset(ZoneOffset.UTC),
                NOW.minusSeconds(60).atOffset(ZoneOffset.UTC),
                NOW.minusSeconds(60).atOffset(ZoneOffset.UTC));
    }

    @Test
    void concurrentWorkersHaveOneClaimWinnerAndMaterializeOneCanonicalProjection() throws Exception {
        CountDownLatch historyEntered = new CountDownLatch(1);
        CountDownLatch releaseHistory = new CountDownLatch(1);
        RecordHistoryQuery history = new BlockingHistoryQuery(historyQuery(), historyEntered, releaseHistory);
        Worker first = worker(history);
        Worker second = worker(history);
        CountDownLatch start = new CountDownLatch(1);
        LinkedBlockingQueue<RecordBootstrapCoordinator.BootstrapRunResult> completions = new LinkedBlockingQueue<>();

        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            Future<RecordBootstrapCoordinator.BootstrapRunResult> firstRun = pool.submit(() -> {
                await(start);
                RecordBootstrapCoordinator.BootstrapRunResult result = first.coordinator().run(GUILD_ID);
                completions.add(result);
                return result;
            });
            Future<RecordBootstrapCoordinator.BootstrapRunResult> secondRun = pool.submit(() -> {
                await(start);
                RecordBootstrapCoordinator.BootstrapRunResult result = second.coordinator().run(GUILD_ID);
                completions.add(result);
                return result;
            });

            start.countDown();
            await(historyEntered);
            assertThat(completions.poll(10, TimeUnit.SECONDS))
                    .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.NOT_CLAIMED);
            releaseHistory.countDown();

            assertThat(firstRun.get(10, TimeUnit.SECONDS))
                    .isIn(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED,
                            RecordBootstrapCoordinator.BootstrapRunResult.NOT_CLAIMED);
            assertThat(secondRun.get(10, TimeUnit.SECONDS))
                    .isIn(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED,
                            RecordBootstrapCoordinator.BootstrapRunResult.NOT_CLAIMED);
        }

        assertCanonicalCompletedBootstrap(1);
    }

    @Test
    void expiredWorkerCannotFinishAfterTakeoverAndRestartCompletesPartialProjection() throws Exception {
        CountDownLatch firstStateCommitted = new CountDownLatch(1);
        CountDownLatch releaseExpiredWorker = new CountDownLatch(1);
        Worker expiredWorker = worker(
                historyQuery(),
                (states, events, transactions) -> new BlockingAfterFirstStateService(
                        states, events, transactions, firstStateCommitted, releaseExpiredWorker));
        Worker takeoverWorker = worker(historyQuery());

        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            Future<RecordBootstrapCoordinator.BootstrapRunResult> expiredRun =
                    pool.submit(() -> expiredWorker.coordinator().run(GUILD_ID));
            await(firstStateCommitted);

            clock.set(NOW.plus(Duration.ofMinutes(2)));
            Future<RecordBootstrapCoordinator.BootstrapRunResult> takeoverRun =
                    pool.submit(() -> takeoverWorker.coordinator().run(GUILD_ID));
            assertThat(takeoverRun.get(10, TimeUnit.SECONDS))
                    .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);

            releaseExpiredWorker.countDown();
            assertThat(expiredRun.get(10, TimeUnit.SECONDS))
                    .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.LOST_LEASE);
        }

        assertCanonicalCompletedBootstrap(2);
    }

    @Test
    void expiredWorkerCannotMarkItsRetryFailureAfterAnotherWorkerTakesOver() throws Exception {
        CountDownLatch historyEntered = new CountDownLatch(1);
        CountDownLatch releaseExpiredWorker = new CountDownLatch(1);
        Worker expiredWorker = worker(new FailingAfterLatchHistoryQuery(
                historyQuery(), historyEntered, releaseExpiredWorker));
        Worker takeoverWorker = worker(historyQuery());

        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            Future<RecordBootstrapCoordinator.BootstrapRunResult> expiredRun =
                    pool.submit(() -> expiredWorker.coordinator().run(GUILD_ID));
            await(historyEntered);

            clock.set(NOW.plus(Duration.ofMinutes(2)));
            Future<RecordBootstrapCoordinator.BootstrapRunResult> takeoverRun =
                    pool.submit(() -> takeoverWorker.coordinator().run(GUILD_ID));
            assertThat(takeoverRun.get(10, TimeUnit.SECONDS))
                    .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);

            releaseExpiredWorker.countDown();
            assertThat(expiredRun.get(10, TimeUnit.SECONDS))
                    .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.LOST_LEASE);
        }

        assertCanonicalCompletedBootstrap(2);
    }

    @Test
    void retryableFailureIsRetriedByAFreshCoordinatorOnlyWhenDue() {
        AtomicBoolean failFirstLoad = new AtomicBoolean(true);
        RecordHistoryQuery failsOnce = guildId -> {
            if (failFirstLoad.compareAndSet(true, false)) {
                throw new RecordRetryableFailure("transient history read", null);
            }
            return historyQuery().load(guildId);
        };

        assertThat(worker(failsOnce).coordinator().run(GUILD_ID))
                .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.RETRY_SCHEDULED);
        assertThat(bootstrap().find(BOOTSTRAP_KEY).orElseThrow())
                .extracting(snapshot -> snapshot.state(), snapshot -> snapshot.attemptCount())
                .containsExactly(RecordWorkState.RETRYABLE, 1);
        assertNoAnnouncements();

        clock.set(NOW.plusSeconds(59));
        assertThat(worker(historyQuery()).coordinator().run(GUILD_ID))
                .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.NOT_CLAIMED);

        clock.set(NOW.plus(Duration.ofMinutes(1)));
        assertThat(worker(historyQuery()).coordinator().run(GUILD_ID))
                .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);

        assertCanonicalCompletedBootstrap(2);
    }

    @Test
    void stateAndInitializationAnchorRollbackTogetherWhenCoordinatorMaterializationFails() {
        Worker failingWorker = worker(
                historyQuery(),
                (states, events, transactions) -> new RecordStateService(
                        states,
                        new FailAfterAppendEventStore(events),
                        transactions,
                        RecordDefinitionCatalog.recordsV1()));

        assertThat(failingWorker.coordinator().run(GUILD_ID))
                .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.RETRY_SCHEDULED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_state", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_event", Integer.class)).isZero();
        assertThat(bootstrap().find(BOOTSTRAP_KEY).orElseThrow().state())
                .isEqualTo(RecordWorkState.RETRYABLE);
        assertNoAnnouncements();
    }

    @Test
    void unexpectedCoordinatorFailureRemainsVisibleInsteadOfBeingClassifiedAsRetryable() {
        Worker worker = worker(guildId -> {
            throw new IllegalStateException("unexpected mapper failure");
        });

        assertThatThrownBy(() -> worker.coordinator().run(GUILD_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("unexpected mapper failure");
        assertThat(bootstrap().find(BOOTSTRAP_KEY).orElseThrow().state())
                .isEqualTo(RecordWorkState.CLAIMED);
        assertNoAnnouncements();
    }

    @Test
    void recordsV2BootstrapsIndependentlyFromSucceededV1AndKeepsV1AuditFacts() {
        seedBothGamesParticipationAndQuadWordsResult();
        RecordDefinitionCatalog v1 = RecordDefinitionCatalog.recordsV1();
        RecordDefinitionCatalog v2 = RecordDefinitionCatalog.recordsV2();

        assertThat(worker(historyQuery(), v1).coordinator().run(GUILD_ID))
                .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        int v1States = countForVersion("record_state", v1.version());
        int v1Events = countForVersion("record_event", v1.version());

        assertThat(worker(historyQuery(), v2).coordinator().run(GUILD_ID))
                .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        assertThat(bootstrap().find(new RecordBootstrapKey(GUILD_ID, v2.version())).orElseThrow().state())
                .isEqualTo(RecordWorkState.SUCCEEDED);
        assertThat(sharedStreakStates(v2.version())).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT min(streak_length) FROM record_state
                WHERE definition_version=? AND source_type='STREAK_RUN' AND scope_type='SHARED'
                """, Integer.class, v2.version().value())).isEqualTo(1);
        assertThat(countForVersion("record_state", v1.version())).isEqualTo(v1States);
        assertThat(countForVersion("record_event", v1.version())).isEqualTo(v1Events);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM record_event
                WHERE definition_version=? AND event_type='RECORD_INITIALIZED'
                """, Integer.class, v2.version().value()))
                .isEqualTo(countForVersion("record_state", v2.version()));
        assertNoAnnouncements();

        int v2States = countForVersion("record_state", v2.version());
        int v2Events = countForVersion("record_event", v2.version());
        assertThat(worker(historyQuery(), v2).coordinator().run(GUILD_ID))
                .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.NOT_CLAIMED);
        assertThat(countForVersion("record_state", v2.version())).isEqualTo(v2States);
        assertThat(countForVersion("record_event", v2.version())).isEqualTo(v2Events);
    }

    @Test
    void concurrentStateInitializationUsesOneStateAndOneAnchorAcrossIndependentTransactions() throws Exception {
        CountDownLatch bothObservedMissingState = new CountDownLatch(2);
        CountDownLatch releaseInitializers = new CountDownLatch(1);
        StateAccess firstAccess = stateAccess(states -> new BlockingFindStateStore(
                states, true, bothObservedMissingState, releaseInitializers));
        StateAccess secondAccess = stateAccess(states -> new BlockingFindStateStore(
                states, true, bothObservedMissingState, releaseInitializers));
        RecordBootstrapProjection.Candidate candidate = candidate(3, 901L);

        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            Future<RecordStateService.RebuildResult> first = pool.submit(() -> firstAccess.service()
                    .reconcileCanonicalTarget(candidate, "1:records-v1", NOW));
            Future<RecordStateService.RebuildResult> second = pool.submit(() -> secondAccess.service()
                    .reconcileCanonicalTarget(candidate, "1:records-v1", NOW));
            await(bothObservedMissingState);
            releaseInitializers.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS))
                    .isIn(RecordStateService.RebuildResult.CREATED, RecordStateService.RebuildResult.UNCHANGED);
            assertThat(second.get(10, TimeUnit.SECONDS))
                    .isIn(RecordStateService.RebuildResult.CREATED, RecordStateService.RebuildResult.UNCHANGED);
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_state", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_event", Integer.class)).isOne();
        assertThat(new PostgresRecordStateStore(jdbc, clock).find(candidate.key()).orElseThrow().source())
                .isEqualTo(candidate.write().source());
        assertNoAnnouncements();
    }

    @Test
    void concurrentCasUpdatesReevaluateAndKeepTheBetterCanonicalState() throws Exception {
        RecordStateKey key = stateKey();
        PostgresRecordStateStore seedStates = new PostgresRecordStateStore(jdbc, clock);
        seedStates.initialize(key, write(5, 801L));

        CountDownLatch bothObservedInitialVersion = new CountDownLatch(2);
        CountDownLatch releaseUpdates = new CountDownLatch(1);
        StateAccess firstAccess = stateAccess(states -> new BlockingFindStateStore(
                states, false, bothObservedInitialVersion, releaseUpdates));
        StateAccess secondAccess = stateAccess(states -> new BlockingFindStateStore(
                states, false, bothObservedInitialVersion, releaseUpdates));
        RecordBootstrapProjection.Candidate merelyBetter = candidate(3, 802L);
        RecordBootstrapProjection.Candidate best = candidate(1, 803L);

        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            Future<RecordStateService.RebuildResult> first = pool.submit(() -> firstAccess.service()
                    .rebuild(merelyBetter, "1:records-v1", NOW));
            Future<RecordStateService.RebuildResult> second = pool.submit(() -> secondAccess.service()
                    .rebuild(best, "1:records-v1", NOW));
            await(bothObservedInitialVersion);
            releaseUpdates.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS))
                    .isIn(RecordStateService.RebuildResult.REPLACED, RecordStateService.RebuildResult.UNCHANGED);
            assertThat(second.get(10, TimeUnit.SECONDS))
                    .isIn(RecordStateService.RebuildResult.REPLACED, RecordStateService.RebuildResult.UNCHANGED);
        }

        RecordStateSnapshot canonical = seedStates.find(key).orElseThrow();
        assertThat(canonical.value()).isEqualTo(best.write().value());
        assertThat(canonical.source()).isEqualTo(best.write().source());
        assertThat(canonical.lockVersion().value()).isGreaterThanOrEqualTo(1L);
        assertThat(seedStates.update(new RecordStateUpdate(key, RecordLockVersion.initial(), merelyBetter.write())).status())
                .isEqualTo(RecordStateUpdateResult.Status.VERSION_CONFLICT);
        assertNoAnnouncements();
    }

    @Test
    void targetedCanonicalRebuildWinsOverAConcurrentLiveSafeUpdate() throws Exception {
        RecordStateKey key = stateKey();
        PostgresRecordStateStore seedStates = new PostgresRecordStateStore(jdbc, clock);
        seedStates.initialize(key, write(5, 811L));

        CountDownLatch bothObservedInitialVersion = new CountDownLatch(2);
        CountDownLatch releaseUpdates = new CountDownLatch(1);
        StateAccess recomputation = stateAccess(states -> new BlockingFindStateStore(
                states, false, bothObservedInitialVersion, releaseUpdates));
        StateAccess normalUpdate = stateAccess(states -> new BlockingFindStateStore(
                states, false, bothObservedInitialVersion, releaseUpdates));
        RecordBootstrapProjection.Candidate canonical = candidate(1, 812L);
        RecordBootstrapProjection.Candidate merelyBetter = candidate(3, 813L);

        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            Future<RecordStateService.RebuildResult> exactRebuild = pool.submit(() -> recomputation.service()
                    .reconcileCanonicalTarget(canonical, "1:records-v1", NOW));
            Future<RecordStateService.RebuildResult> safeUpdate = pool.submit(() -> normalUpdate.service()
                    .rebuild(merelyBetter, "1:records-v1", NOW));
            await(bothObservedInitialVersion);
            releaseUpdates.countDown();
            assertThat(exactRebuild.get(10, TimeUnit.SECONDS))
                    .isIn(RecordStateService.RebuildResult.REPLACED, RecordStateService.RebuildResult.UNCHANGED);
            assertThat(safeUpdate.get(10, TimeUnit.SECONDS))
                    .isIn(RecordStateService.RebuildResult.REPLACED, RecordStateService.RebuildResult.UNCHANGED);
        }

        RecordStateSnapshot persisted = seedStates.find(key).orElseThrow();
        assertThat(persisted.value()).isEqualTo(canonical.write().value());
        assertThat(persisted.source()).isEqualTo(canonical.write().source());
        assertNoAnnouncements();
    }

    private void assertCanonicalCompletedBootstrap(int expectedAttempts) {
        assertThat(bootstrap().find(BOOTSTRAP_KEY).orElseThrow())
                .extracting(snapshot -> snapshot.state(), snapshot -> snapshot.attemptCount(), snapshot -> snapshot.claimToken())
                .containsExactly(RecordWorkState.SUCCEEDED, expectedAttempts, Optional.empty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_state", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_event", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_event WHERE event_type = 'RECORD_INITIALIZED'", Integer.class))
                .isEqualTo(6);
        assertNoAnnouncements();
    }

    private void assertNoAnnouncements() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_announcement", Integer.class)).isZero();
    }

    private void seedBothGamesParticipationAndQuadWordsResult() {
        jdbc.update("""
                INSERT INTO player_participation_period
                    (player_id,game_type,active_from,inactive_from,created_at,updated_at)
                VALUES (7,'GRIDWORDS',DATE '2026-08-04',NULL,?,?),
                       (7,'QUADWORDS',DATE '2026-08-04',NULL,?,?)
                """, NOW.atOffset(ZoneOffset.UTC), NOW.atOffset(ZoneOffset.UTC),
                NOW.atOffset(ZoneOffset.UTC), NOW.atOffset(ZoneOffset.UTC));
        Long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id,game_type,game_date,solved,attempts_used,max_attempts,duration_seconds,
                    raw_share_text,parser_version,created_at,updated_at,version)
                VALUES (7,'QUADWORDS',DATE '2026-08-04',TRUE,4,9,75,'QuadWords fixture','test',?,?,0)
                RETURNING id
                """, Long.class, NOW.minusSeconds(60).atOffset(ZoneOffset.UTC),
                NOW.minusSeconds(60).atOffset(ZoneOffset.UTC));
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id,guild_id,channel_id,author_player_id,raw_message_content,processing_state,
                    game_result_id,received_at,updated_at,original_deleted_at,version)
                VALUES (101,1,2,7,'QuadWords fixture','COMPLETED',?,?,?,?,0)
                """, resultId, NOW.minusSeconds(60).atOffset(ZoneOffset.UTC),
                NOW.minusSeconds(60).atOffset(ZoneOffset.UTC), NOW.minusSeconds(60).atOffset(ZoneOffset.UTC));
    }

    private int countForVersion(String table, RecordDefinitionVersion version) {
        if (!Set.of("record_state", "record_event").contains(table)) {
            throw new IllegalArgumentException("unsupported record table");
        }
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE definition_version=?", Integer.class,
                version.value());
    }

    private int sharedStreakStates(RecordDefinitionVersion version) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM record_state
                WHERE definition_version=? AND source_type='STREAK_RUN' AND scope_type='SHARED'
                """, Integer.class, version.value());
    }

    private Worker worker(RecordHistoryQuery history) {
        return worker(history, RecordDefinitionCatalog.recordsV1());
    }

    private Worker worker(RecordHistoryQuery history, RecordDefinitionCatalog catalog) {
        return worker(history, catalog, (states, events, transactions) ->
                new RecordStateService(states, events, transactions, catalog));
    }

    private Worker worker(RecordHistoryQuery history, StateServiceFactory stateServiceFactory) {
        return worker(history, RecordDefinitionCatalog.recordsV1(), stateServiceFactory);
    }

    private Worker worker(
            RecordHistoryQuery history, RecordDefinitionCatalog catalog, StateServiceFactory stateServiceFactory) {
        DriverManagerDataSource source = newDataSource();
        JdbcTemplate workerJdbc = new JdbcTemplate(source);
        RecordBootstrapStore bootstraps = new PostgresRecordBootstrapStore(workerJdbc, clock);
        RecordStateStore states = new PostgresRecordStateStore(workerJdbc, clock);
        RecordEventStore events = new PostgresRecordEventStore(workerJdbc, clock);
        RecordTransactionRunner transactions = transactionRunner(source);
        RecordStateService stateService = stateServiceFactory.create(states, events, transactions);
        return new Worker(new RecordBootstrapCoordinator(
                bootstraps,
                history,
                stateService,
                catalog,
                clock));
    }

    private StateAccess stateAccess(StateStoreDecorator decorator) {
        DriverManagerDataSource source = newDataSource();
        RecordStateStore states = decorator.decorate(new PostgresRecordStateStore(new JdbcTemplate(source), clock));
        RecordEventStore events = new PostgresRecordEventStore(new JdbcTemplate(source), clock);
        RecordStateService service = new RecordStateService(
                states, events, transactionRunner(source), RecordDefinitionCatalog.recordsV1());
        return new StateAccess(service);
    }

    private PostgresRecordBootstrapStore bootstrap() {
        return new PostgresRecordBootstrapStore(jdbc, clock);
    }

    private RecordHistoryQuery historyQuery() {
        return new PostgresRecordHistoryQuery(new JdbcTemplate(newDataSource()));
    }

    private DriverManagerDataSource newDataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static RecordTransactionRunner transactionRunner(DataSource source) {
        TransactionTemplate template = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        return new RecordTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return template.execute(status -> work.get());
            }
        };
    }

    private static RecordBootstrapProjection.Candidate candidate(int attempts, long resultId) {
        return new RecordBootstrapProjection.Candidate(stateKey(), write(attempts, resultId));
    }

    private static RecordStateKey stateKey() {
        return new RecordStateKey(
                GUILD_ID,
                new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
                RecordDefinitionVersion.RECORDS_V1,
                new RecordScope.Personal(7L));
    }

    private static RecordStateWrite write(int attempts, long resultId) {
        return new RecordStateWrite(
                Optional.of(7L),
                new AttemptsDurationRecordValue(attempts, Duration.ofSeconds(50)),
                new RecordSourceReference.GameResult(
                        resultId,
                        0,
                        7L,
                        GameType.GRIDWORDS,
                        LocalDate.of(2026, 8, 4)),
                Optional.of(NOW.minusSeconds(60)),
                false);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("deterministic PostgreSQL race timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("deterministic PostgreSQL race interrupted", exception);
        }
    }

    private record Worker(RecordBootstrapCoordinator coordinator) {
    }

    private record StateAccess(RecordStateService service) {
    }

    @FunctionalInterface
    private interface StateServiceFactory {
        RecordStateService create(RecordStateStore states, RecordEventStore events, RecordTransactionRunner transactions);
    }

    @FunctionalInterface
    private interface StateStoreDecorator {
        RecordStateStore decorate(RecordStateStore states);
    }

    private static final class MutableClock extends Clock {
        private final java.util.concurrent.atomic.AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new java.util.concurrent.atomic.AtomicReference<>(instant);
        }

        void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    private static final class BlockingHistoryQuery implements RecordHistoryQuery {
        private final RecordHistoryQuery delegate;
        private final CountDownLatch entered;
        private final CountDownLatch release;

        private BlockingHistoryQuery(RecordHistoryQuery delegate, CountDownLatch entered, CountDownLatch release) {
            this.delegate = delegate;
            this.entered = entered;
            this.release = release;
        }

        @Override
        public de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot load(long guildId) {
            de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot loaded = delegate.load(guildId);
            entered.countDown();
            await(release);
            return loaded;
        }
    }

    private static final class FailingAfterLatchHistoryQuery implements RecordHistoryQuery {
        private final RecordHistoryQuery delegate;
        private final CountDownLatch entered;
        private final CountDownLatch release;

        private FailingAfterLatchHistoryQuery(
                RecordHistoryQuery delegate, CountDownLatch entered, CountDownLatch release) {
            this.delegate = delegate;
            this.entered = entered;
            this.release = release;
        }

        @Override
        public de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot load(long guildId) {
            entered.countDown();
            await(release);
            delegate.load(guildId);
            throw new RecordRetryableFailure("stale worker failure", null);
        }
    }

    private static final class BlockingAfterFirstStateService extends RecordStateService {
        private final CountDownLatch firstStateCommitted;
        private final CountDownLatch release;
        private final AtomicBoolean blocked = new AtomicBoolean();

        private BlockingAfterFirstStateService(
                RecordStateStore states,
                RecordEventStore events,
                RecordTransactionRunner transactions,
                CountDownLatch firstStateCommitted,
                CountDownLatch release) {
            super(states, events, transactions, RecordDefinitionCatalog.recordsV1());
            this.firstStateCommitted = firstStateCommitted;
            this.release = release;
        }

        @Override
        public RebuildResult reconcileCanonicalTarget(
                RecordBootstrapProjection.Candidate candidate, String bootstrapKey, Instant detectedAt) {
            RebuildResult result = super.reconcileCanonicalTarget(candidate, bootstrapKey, detectedAt);
            if (blocked.compareAndSet(false, true)) {
                firstStateCommitted.countDown();
                await(release);
            }
            return result;
        }
    }

    private static final class BlockingFindStateStore implements RecordStateStore {
        private final RecordStateStore delegate;
        private final boolean blockOnEmpty;
        private final CountDownLatch arrived;
        private final CountDownLatch release;
        private final AtomicBoolean blocked = new AtomicBoolean();

        private BlockingFindStateStore(
                RecordStateStore delegate,
                boolean blockOnEmpty,
                CountDownLatch arrived,
                CountDownLatch release) {
            this.delegate = delegate;
            this.blockOnEmpty = blockOnEmpty;
            this.arrived = arrived;
            this.release = release;
        }

        @Override
        public Optional<RecordStateSnapshot> find(RecordStateKey key) {
            Optional<RecordStateSnapshot> snapshot = delegate.find(key);
            if (snapshot.isEmpty() == blockOnEmpty && blocked.compareAndSet(false, true)) {
                arrived.countDown();
                await(release);
            }
            return snapshot;
        }

        @Override
        public List<RecordStateSnapshot> findAll(long guildId, RecordDefinitionVersion definitionVersion) {
            return delegate.findAll(guildId, definitionVersion);
        }

        @Override
        public RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write) {
            return delegate.initialize(key, write);
        }

        @Override
        public RecordStateUpdateResult update(RecordStateUpdate update) {
            return delegate.update(update);
        }

        @Override
        public boolean remove(RecordStateKey key, RecordLockVersion expectedLockVersion) {
            return delegate.remove(key, expectedLockVersion);
        }
    }

    private static final class FailAfterAppendEventStore implements RecordEventStore {
        private final RecordEventStore delegate;

        private FailAfterAppendEventStore(RecordEventStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public RecordEventAppendResult append(RecordEventDraft draft) {
            delegate.append(draft);
            throw new RecordRetryableFailure("simulated append interruption", null);
        }

        @Override
        public Optional<RecordEventSnapshot> find(java.util.UUID eventId) {
            return delegate.find(eventId);
        }

        @Override
        public List<RecordEventSnapshot> findByTriggerKey(long guildId, String triggerKey) {
            return delegate.findByTriggerKey(guildId, triggerKey);
        }

        @Override
        public boolean invalidate(java.util.UUID eventId, Instant invalidatedAt) {
            return delegate.invalidate(eventId, invalidatedAt);
        }

        @Override
        public boolean supersede(java.util.UUID eventId, java.util.UUID supersedingEventId, Instant invalidatedAt) {
            return delegate.supersede(eventId, supersedingEventId, invalidatedAt);
        }
    }
}
