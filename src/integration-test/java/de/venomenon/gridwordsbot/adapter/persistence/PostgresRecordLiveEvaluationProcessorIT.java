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
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventAppendResult;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateInitialization;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdate;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.domain.record.RecordLockVersion;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordLiveHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
    private DriverManagerDataSource source;
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

    @Test
    void correctionPartiallyReducesPublishedAggregateAndPersistsAReplacementFact() {
        List<Long> results = prepareImprovingHistory(6, 1);
        long corrected = results.getLast();
        synchronizeAnnouncements();
        int validBefore = validResultEvents(corrected);

        correctResult(corrected, true, 6, 5, RecordProcessingOrigin.NORMAL_CORRECTION);
        processor(work).process(claim());

        var announcement = announcementFor(corrected);
        assertThat(announcement.registration().desiredProjection()).isEqualTo(RecordAnnouncementProjection.EDIT);
        assertThat(announcement.registration().eventIds()).hasSize(1);
        assertThat(validResultEvents(corrected)).isLessThan(validBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_event WHERE validity='INVALIDATED'", Integer.class))
                .isGreaterThanOrEqualTo(validBefore - 1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM record_event
                WHERE validity='VALID' AND processing_origin='NORMAL_CORRECTION'
                  AND split_part(new_source_key, ':', 1)=?
                """, Integer.class, Long.toString(corrected))).isPositive();
    }

    @Test
    void correctionFullyDeletesAggregateAndFallsBackToTheNextCanonicalResult() {
        List<Long> results = prepareImprovingHistory(6, 1);
        long corrected = results.getLast();
        long fallback = results.get(results.size() - 2);
        synchronizeAnnouncements();

        correctResult(corrected, false, null, 99, RecordProcessingOrigin.NORMAL_CORRECTION);
        processor(work).process(claim());

        assertThat(announcementFor(corrected).registration().desiredProjection())
                .isEqualTo(RecordAnnouncementProjection.DELETE);
        assertThat(jdbc.queryForList("""
                SELECT source_game_result_id FROM record_state
                WHERE definition_key LIKE 'result.gridwords.%' AND scope_type='PERSONAL'
                """, Long.class)).contains(fallback).doesNotContain(corrected);
    }

    @Test
    void correctionRemovesResultStatesWhenNoCanonicalSolvedSourceRemains() {
        insertPlayerAndParticipation(1, LocalDate.of(2026, 8, 1));
        readyBootstrap();
        long resultId = insertResult(1, LocalDate.of(2026, 8, 6), true, 3, 60);
        processor(work).process(claim());

        correctResult(resultId, false, null, 99, RecordProcessingOrigin.NORMAL_CORRECTION);
        processor(work).process(claim());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM record_state WHERE definition_key LIKE 'result.gridwords.%'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_result WHERE id=?", Integer.class, resultId)).isOne();
    }

    @Test
    void historicalCorrectionReevaluatesTheLaterResultEventFamily() {
        List<Long> results = prepareImprovingHistory(7, 1);
        long corrected = results.get(5);
        long later = results.getLast();
        synchronizeAnnouncements();
        List<UUID> laterBefore = announcementFor(later).registration().eventIds();

        correctResult(corrected, false, null, 99, RecordProcessingOrigin.NORMAL_CORRECTION);
        processor(work).process(claim());

        var laterAnnouncement = announcementFor(later);
        assertThat(laterAnnouncement.registration().desiredProjection()).isEqualTo(RecordAnnouncementProjection.EDIT);
        assertThat(laterAnnouncement.registration().eventIds()).doesNotContainAnyElementsOf(laterBefore);
        assertThat(laterAnnouncement.registration().eventIds()).allSatisfy(eventId ->
                assertThat(events.find(eventId).orElseThrow().draft().newSource())
                        .isInstanceOf(de.venomenon.gridwordsbot.domain.record.RecordSourceReference.GameResult.class));
    }

    @Test
    void correctionSplitsAndReconnectsARecordStreakWithoutDuplicateFacts() {
        insertPlayerAndParticipation(1, LocalDate.of(2026, 7, 1));
        readyBootstrap();
        List<Long> results = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            results.add(insertAndProcess(1, LocalDate.of(2026, 7, day), true, 3, 60));
        }
        insertAndProcess(1, LocalDate.of(2026, 7, 8), false, null, 99);
        for (int day = 9; day <= 16; day++) {
            results.add(insertAndProcess(1, LocalDate.of(2026, 7, day), true, 3, 60));
        }
        insertAndProcess(1, LocalDate.of(2026, 7, 17), false, null, 99);
        correctResult(results.getFirst(), true, 3, 60, RecordProcessingOrigin.NORMAL_CORRECTION);
        processor(work).process(claim());
        int publicStreakFactsBefore = validPublicStreakEvents();
        assertThat(publicStreakFactsBefore).isPositive();
        assertThat(streakStateCount(LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 16))).isPositive();
        int invalidatedBefore = jdbc.queryForObject(
                "SELECT count(*) FROM record_event WHERE validity='INVALIDATED'", Integer.class);
        long middle = resultId(1, LocalDate.of(2026, 7, 12));

        correctResult(middle, false, null, 99, RecordProcessingOrigin.NORMAL_CORRECTION);
        processor(work).process(claim());
        assertThat(streakStateCount(LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 16))).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM record_event WHERE validity='INVALIDATED'", Integer.class))
                .isGreaterThan(invalidatedBefore);

        correctResult(middle, true, 3, 60, RecordProcessingOrigin.NORMAL_CORRECTION);
        processor(work).process(claim());
        assertThat(streakStateCount(LocalDate.of(2026, 7, 9), LocalDate.of(2026, 7, 16))).isPositive();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT idempotency_key FROM record_event WHERE validity='VALID'
                    GROUP BY idempotency_key HAVING count(*)>1
                ) duplicates
                """, Integer.class)).isZero();
    }

    @Test
    void correctionAfterReentryKeepsAndFallsBackToAllTimeStreaksFromTheEarlierPeriods() {
        insertPlayer(1);
        insertPlayer(2);
        insertParticipation(1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));
        insertParticipation(2, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 10));
        insertParticipation(1, LocalDate.of(2026, 8, 1), null);
        insertParticipation(2, LocalDate.of(2026, 8, 3), null);
        readyBootstrap();

        insertSolvedRun(1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 9));
        insertSolvedRun(2, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 9));
        insertSolvedRun(1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
        insertSolvedRun(2, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10));

        assertStreakSource("streak.gridwords-solved.personal", "PERSONAL", "player:1",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
        assertStreakSource("streak.gridwords-solved.server-individual", "SERVER_INDIVIDUAL", "server",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));
        assertStreakSource("streak.gridwords-solved.shared", "SHARED", "shared",
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10));

        long reentryEnd = resultId(1, LocalDate.of(2026, 8, 10));
        correctResult(reentryEnd, true, 3, 60, RecordProcessingOrigin.NORMAL_CORRECTION);
        assertThat(processor(work).process(claim()))
                .isEqualTo(RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED);
        synchronizeAnnouncements();
        List<UUID> supersededLaterFacts = jdbc.queryForList("""
                SELECT DISTINCT e.event_id
                FROM record_event e
                JOIN record_announcement_event ae ON ae.event_id=e.event_id
                WHERE e.validity='VALID' AND e.event_type='SERIES_RECORD_CROSSED'
                  AND e.definition_key LIKE 'streak.gridwords-solved.%'
                  AND e.new_streak_start_date>=DATE '2026-08-01'
                ORDER BY e.event_id
                """, UUID.class);
        List<String> laterAnnouncements = jdbc.queryForList("""
                SELECT DISTINCT a.idempotency_key
                FROM record_announcement a
                JOIN record_announcement_event ae ON ae.announcement_id=a.id
                JOIN record_event e ON e.event_id=ae.event_id
                WHERE e.validity='VALID' AND e.event_type='SERIES_RECORD_CROSSED'
                  AND e.definition_key LIKE 'streak.gridwords-solved.%'
                  AND e.new_streak_start_date>=DATE '2026-08-01'
                ORDER BY a.idempotency_key
                """, String.class);
        assertThat(supersededLaterFacts).hasSize(3);
        assertThat(laterAnnouncements).hasSize(2);

        long corrected = resultId(1, LocalDate.of(2026, 8, 5));
        correctResult(corrected, false, null, 99, RecordProcessingOrigin.NORMAL_CORRECTION);
        assertThat(processor(work).process(claim()))
                .isEqualTo(RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED);

        assertStreakSource("streak.gridwords-solved.personal", "PERSONAL", "player:1",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 9));
        assertStreakSource("streak.gridwords-solved.server-individual", "SERVER_INDIVIDUAL", "server",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 9));
        assertStreakSource("streak.gridwords-solved.shared", "SHARED", "shared",
                LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 9));
        assertThat(jdbc.queryForList("""
                SELECT event_id FROM record_event
                WHERE event_id IN (?,?,?) AND validity='INVALIDATED'
                ORDER BY event_id
                """, UUID.class, supersededLaterFacts.get(0), supersededLaterFacts.get(1),
                supersededLaterFacts.get(2))).containsExactlyInAnyOrderElementsOf(supersededLaterFacts);
        assertThat(jdbc.queryForList("""
                SELECT desired_projection FROM record_announcement
                WHERE idempotency_key IN (?,?) ORDER BY idempotency_key
                """, String.class, laterAnnouncements.get(0), laterAnnouncements.get(1)))
                .containsExactlyInAnyOrder(
                        RecordAnnouncementProjection.EDIT.name(),
                        RecordAnnouncementProjection.DELETE.name());
        assertNoDuplicateValidFacts();
    }

    @Test
    void retryAndReplayRemainIdempotentAndSilent() {
        prepareImprovingHistory(5, 1);
        long resultId = insertResult(1, LocalDate.of(2026, 8, 6), true, 1, 5);
        RecordLiveEvaluationClaim firstClaim = claim();
        PostgresRecordLiveEvaluationStore failOnce = terminalFailingStore();
        assertThatThrownBy(() -> processor(failOnce).process(firstClaim)).isInstanceOf(IllegalStateException.class);
        int afterRollback = count("record_event");

        processor(work).process(firstClaim);
        int afterRetry = count("record_event");
        assertThat(afterRetry).isGreaterThan(afterRollback);
        assertThat(processor(work).process(firstClaim))
                .isEqualTo(RecordLiveEvaluationProcessor.ProcessingResult.FENCED_OUT);
        assertThat(count("record_event")).isEqualTo(afterRetry);

        correctResult(resultId, true, 1, 5, RecordProcessingOrigin.REPLAY);
        processor(work).process(claim());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM record_announcement announcement
                WHERE announcement.idempotency_key LIKE ?
                """, Integer.class, "live-result:" + resultId + "%")).isOne();
    }

    @Test
    void silentOriginsUpdateCanonicalStateWithoutCreatingAnnouncements() {
        for (RecordProcessingOrigin origin : List.of(RecordProcessingOrigin.IMPORT,
                RecordProcessingOrigin.BACKFILL, RecordProcessingOrigin.ADMINISTRATIVE_REPAIR)) {
            clean();
            prepareImprovingHistory(5, 1);
            long resultId = insertResult(1, LocalDate.of(2026, 8, 6), true, 1, 5);
            setOpenOrigin(resultId, origin);
            processor(work).process(claim());
            assertThat(count("record_state")).isPositive();
            assertThat(count("record_announcement")).as(origin.name()).isZero();
        }
    }

    @Test
    void bootstrapReadinessSuppressesPublicProjectionPermanentlyForThatEvaluation() {
        insertPlayerAndParticipation(1, LocalDate.of(2026, 8, 1));
        for (int day = 1; day <= 6; day++) {
            insertAndProcess(1, LocalDate.of(2026, 8, day), true, 7 - day, 70 - day * 10);
        }
        assertThat(count("record_state")).isPositive();
        assertThat(count("record_announcement")).isZero();
    }

    @Test
    void externallyRemovedAnnouncementRemainsTerminalWhileItsFactsAreReconciled() {
        List<Long> results = prepareImprovingHistory(6, 1);
        long corrected = results.getLast();
        var before = announcementFor(corrected);
        RecordLeaseClaim deliveryClaim = announcements.claim(before.registration().key(),
                new RecordLeaseClaimRequest(NOW.plusSeconds(3), NOW.plusSeconds(50))).orElseThrow();
        assertThat(announcements.markExternallyRemoved(
                before.registration().key(), deliveryClaim.token(), NOW.plusSeconds(4))).isTrue();

        correctResult(corrected, true, 6, 5, RecordProcessingOrigin.NORMAL_CORRECTION);
        processor(work).process(claim());

        var after = announcements.find(before.registration().key()).orElseThrow();
        assertThat(after.state()).isEqualTo(RecordWorkState.EXTERNALLY_REMOVED);
        assertThat(after.registration().eventIds()).hasSize(1);
    }

    @Test
    void rollbackAfterEachLiveWriteStepKeepsTheCommittedCanonicalResult() {
        for (WriteFailurePoint failurePoint : List.of(
                WriteFailurePoint.STATE, WriteFailurePoint.EVENT_APPEND,
                WriteFailurePoint.ANNOUNCEMENT, WriteFailurePoint.TERMINAL)) {
            clean();
            prepareImprovingHistory(5, 1);
            String stateBefore = stateFingerprint();
            int eventsBefore = count("record_event");
            int announcementsBefore = count("record_announcement");
            long resultId = insertResult(1, LocalDate.of(2026, 8, 6), true, 1, 5);
            RecordLiveEvaluationClaim evaluationClaim = claim();

            RecordStateStore stateStore = failurePoint == WriteFailurePoint.STATE
                    ? new FailingStateStore(states) : states;
            RecordEventStore eventStore = failurePoint == WriteFailurePoint.EVENT_APPEND
                    ? new FailingEventStore(events, true, false) : events;
            RecordAnnouncementStore announcementStore = failurePoint == WriteFailurePoint.ANNOUNCEMENT
                    ? new FailingAnnouncementStore(announcements) : announcements;
            PostgresRecordLiveEvaluationStore processorWork = failurePoint == WriteFailurePoint.TERMINAL
                    ? terminalFailingStore() : work;

            assertThatThrownBy(() -> processor(processorWork, stateStore, eventStore, announcementStore,
                    transactions, new PostgresRecordLiveHistoryQuery(jdbc), bootstraps).process(evaluationClaim))
                    .as(failurePoint.name())
                    .isInstanceOf(IllegalStateException.class);

            assertThat(stateFingerprint()).as(failurePoint.name()).isEqualTo(stateBefore);
            assertThat(count("record_event")).as(failurePoint.name()).isEqualTo(eventsBefore);
            assertThat(count("record_announcement")).as(failurePoint.name()).isEqualTo(announcementsBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM game_result WHERE id=?", Integer.class, resultId))
                    .as(failurePoint.name()).isOne();
        }
    }

    @Test
    void rollbackAfterEventInvalidationKeepsTheCommittedCorrectionAndPriorProjection() {
        List<Long> results = prepareImprovingHistory(6, 1);
        long corrected = results.getLast();
        String stateBefore = stateFingerprint();
        int validBefore = jdbc.queryForObject(
                "SELECT count(*) FROM record_event WHERE validity='VALID'", Integer.class);
        correctResult(corrected, false, null, 99, RecordProcessingOrigin.NORMAL_CORRECTION);
        RecordLiveEvaluationClaim evaluationClaim = claim();
        RecordEventStore failAfterInvalidation = new FailingEventStore(events, false, true);

        assertThatThrownBy(() -> processor(work, states, failAfterInvalidation, announcements,
                transactions, new PostgresRecordLiveHistoryQuery(jdbc), bootstraps).process(evaluationClaim))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("event invalidation");

        assertThat(stateFingerprint()).isEqualTo(stateBefore);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM record_event WHERE validity='VALID'", Integer.class)).isEqualTo(validBefore);
        assertThat(jdbc.queryForObject("SELECT solved FROM game_result WHERE id=?", Boolean.class, corrected)).isFalse();
    }

    @Test
    void concurrentBetterSubmissionsUseSeparateConnectionsAndConvergeCanonically() throws Exception {
        prepareImprovingHistory(5, 1);
        insertPlayerAndParticipation(2, LocalDate.of(2026, 8, 1));
        long best = insertResult(1, LocalDate.of(2026, 8, 6), true, 1, 5);
        long contender = insertResult(2, LocalDate.of(2026, 8, 6), true, 2, 10);
        ProcessorFixture first = separateFixture();
        ProcessorFixture second = separateFixture();
        RecordLiveEvaluationClaim firstClaim = first.work().claimNext(
                new RecordLeaseClaimRequest(NOW.plusSeconds(2), NOW.plusSeconds(60))).orElseThrow();
        RecordLiveEvaluationClaim secondClaim = second.work().claimNext(
                new RecordLeaseClaimRequest(NOW.plusSeconds(2), NOW.plusSeconds(60))).orElseThrow();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        RecordLiveHistoryQuery firstHistory = new LatchingHistoryQuery(first.history(), bothRead, release);
        RecordLiveHistoryQuery secondHistory = new LatchingHistoryQuery(second.history(), bothRead, release);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<RecordLiveEvaluationProcessor.ProcessingResult> firstResult = executor.submit(() ->
                    processor(first.work(), first.states(), first.events(), first.announcements(),
                            first.transactions(), firstHistory, first.bootstraps()).process(firstClaim));
            Future<RecordLiveEvaluationProcessor.ProcessingResult> secondResult = executor.submit(() ->
                    processor(second.work(), second.states(), second.events(), second.announcements(),
                            second.transactions(), secondHistory, second.bootstraps()).process(secondClaim));
            assertThat(bothRead.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(firstResult.get(20, TimeUnit.SECONDS)).isEqualTo(
                    RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED);
            assertThat(secondResult.get(20, TimeUnit.SECONDS)).isEqualTo(
                    RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED);
        }

        assertThat(serverResultSource("fewest-attempts")).isEqualTo(best);
        assertThat(serverResultSource("fastest-solution")).isEqualTo(best);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_result WHERE id IN (?,?)",
                Integer.class, best, contender)).isEqualTo(2);
        assertNoDuplicateValidFacts();
    }

    @Test
    void staleCorrectionPlanRereadsAfterConcurrentSubmissionAndConvergesCanonically() throws Exception {
        List<Long> history = prepareImprovingHistory(6, 1);
        insertPlayerAndParticipation(2, LocalDate.of(2026, 8, 1));
        long corrected = history.getLast();
        correctResult(corrected, false, null, 99, RecordProcessingOrigin.NORMAL_CORRECTION);
        ProcessorFixture correctionFixture = separateFixture();
        RecordLiveEvaluationClaim correctionClaim = correctionFixture.work().claimNext(
                new RecordLeaseClaimRequest(NOW.plusSeconds(2), NOW.plusSeconds(60))).orElseThrow();
        CountDownLatch stalePlanRead = new CountDownLatch(1);
        CountDownLatch releaseCorrection = new CountDownLatch(1);
        RecordLiveHistoryQuery blockedCorrectionHistory = new LatchingHistoryQuery(
                correctionFixture.history(), stalePlanRead, releaseCorrection);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<RecordLiveEvaluationProcessor.ProcessingResult> correctionResult = executor.submit(() ->
                    processor(correctionFixture.work(), correctionFixture.states(), correctionFixture.events(),
                            correctionFixture.announcements(), correctionFixture.transactions(),
                            blockedCorrectionHistory, correctionFixture.bootstraps()).process(correctionClaim));
            assertThat(stalePlanRead.await(10, TimeUnit.SECONDS)).isTrue();

            long newBest = insertResult(2, LocalDate.of(2026, 8, 7), true, 1, 4);
            ProcessorFixture submissionFixture = separateFixture();
            RecordLiveEvaluationClaim submissionClaim = submissionFixture.work().claimNext(
                    new RecordLeaseClaimRequest(NOW.plusSeconds(2), NOW.plusSeconds(60))).orElseThrow();
            Future<RecordLiveEvaluationProcessor.ProcessingResult> submissionResult = executor.submit(() ->
                    processor(submissionFixture.work(), submissionFixture.states(), submissionFixture.events(),
                            submissionFixture.announcements(), submissionFixture.transactions(),
                            submissionFixture.history(), submissionFixture.bootstraps()).process(submissionClaim));
            assertThat(submissionResult.get(20, TimeUnit.SECONDS)).isEqualTo(
                    RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED);
            releaseCorrection.countDown();
            assertThat(correctionResult.get(20, TimeUnit.SECONDS)).isEqualTo(
                    RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED);

            assertThat(serverResultSource("fewest-attempts")).isEqualTo(newBest);
            assertThat(serverResultSource("fastest-solution")).isEqualTo(newBest);
        }
        assertNoDuplicateValidFacts();
    }

    private RecordLiveEvaluationProcessor processor(PostgresRecordLiveEvaluationStore processorWork) {
        return processor(processorWork, states, events, announcements, transactions,
                new PostgresRecordLiveHistoryQuery(jdbc), bootstraps);
    }

    private RecordLiveEvaluationProcessor processor(
            PostgresRecordLiveEvaluationStore processorWork,
            RecordStateStore processorStates,
            RecordEventStore processorEvents,
            RecordAnnouncementStore processorAnnouncements,
            RecordTransactionRunner processorTransactions,
            RecordLiveHistoryQuery processorHistory,
            PostgresRecordBootstrapStore processorBootstraps) {
        return new RecordLiveEvaluationProcessor(processorWork, processorHistory,
                new RecordBootstrapReadService(processorBootstraps),
                new RecordStateService(processorStates, processorEvents, processorTransactions, catalog),
                processorEvents, processorAnnouncements, processorTransactions, catalog, clock, 20);
    }

    private ProcessorFixture separateFixture() {
        DriverManagerDataSource separateSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate separateJdbc = new JdbcTemplate(separateSource);
        RecordTransactionRunner separateTransactions = transactionRunner(separateSource);
        return new ProcessorFixture(
                new PostgresRecordLiveEvaluationStore(separateJdbc, clock),
                new PostgresRecordStateStore(separateJdbc, clock),
                new PostgresRecordEventStore(separateJdbc, clock),
                new PostgresRecordAnnouncementStore(separateJdbc, clock),
                new PostgresRecordBootstrapStore(separateJdbc, clock),
                new PostgresRecordLiveHistoryQuery(separateJdbc),
                separateTransactions);
    }

    private RecordTransactionRunner transactionRunner(DriverManagerDataSource dataSource) {
        return new RecordTransactionRunner() {
            private final TransactionTemplate template =
                    new TransactionTemplate(new DataSourceTransactionManager(dataSource));

            @Override public <T> T inTransaction(java.util.function.Supplier<T> action) {
                return template.execute(status -> action.get());
            }
        };
    }

    private List<Long> prepareImprovingHistory(int days, long playerId) {
        insertPlayerAndParticipation(playerId, LocalDate.of(2026, 8, 1));
        readyBootstrap();
        List<Long> results = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            results.add(insertAndProcess(playerId, LocalDate.of(2026, 8, day), true,
                    Math.max(1, 7 - day), Math.max(5, 70 - day * 10)));
        }
        return results;
    }

    private long insertAndProcess(
            long playerId, LocalDate gameDate, boolean solved, Integer attempts, int durationSeconds) {
        long resultId = insertResult(playerId, gameDate, solved, attempts, durationSeconds);
        assertThat(processor(work).process(claim()))
                .isEqualTo(RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED);
        return resultId;
    }

    private void insertPlayerAndParticipation(long playerId, LocalDate activeFrom) {
        insertPlayer(playerId);
        jdbc.update("""
                INSERT INTO player_participation_period (player_id,game_type,active_from,inactive_from,created_at,updated_at)
                VALUES (?,'GRIDWORDS',?,NULL,?,?), (?,'QUADWORDS',?,NULL,?,?)
                """, playerId, activeFrom, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW),
                playerId, activeFrom, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    private void insertPlayer(long playerId) {
        jdbc.update("""
                INSERT INTO player (discord_user_id,display_name,active,administrator,reminder_opt_in,created_at,updated_at)
                VALUES (?,?,TRUE,FALSE,FALSE,?,?)
                """, playerId, "Player " + playerId,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    private void insertParticipation(long playerId, LocalDate activeFrom, LocalDate inactiveFrom) {
        jdbc.update("""
                INSERT INTO player_participation_period
                    (player_id,game_type,active_from,inactive_from,created_at,updated_at)
                VALUES (?,'GRIDWORDS',?,?,?,?)
                """, playerId, activeFrom, inactiveFrom,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
    }

    private void insertSolvedRun(long playerId, LocalDate first, LocalDate last) {
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            insertAndProcess(playerId, day, true, 3, 60);
        }
    }

    private long insertResult(
            long playerId, LocalDate gameDate, boolean solved, Integer attempts, int durationSeconds) {
        Long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (player_id,game_type,game_date,solved,attempts_used,max_attempts,
                    duration_seconds,normalized_board,raw_share_text,parser_version,created_at,updated_at)
                VALUES (?,'GRIDWORDS',?,?,?,?,?,'ABCDE','share','gridwords-share-v1',?,?)
                RETURNING id
                """, Long.class, playerId, gameDate, solved, attempts, 6, durationSeconds,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO submission (source_message_id,guild_id,channel_id,author_player_id,raw_message_content,
                    processing_state,game_result_id,received_at,updated_at)
                VALUES (?,10,20,?,'share','RESULT_STORED',?,?,?)
                """, 100_000L + resultId, playerId, resultId,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        return java.util.Objects.requireNonNull(resultId);
    }

    private void correctResult(
            long resultId,
            boolean solved,
            Integer attempts,
            int durationSeconds,
            RecordProcessingOrigin origin) {
        jdbc.update("""
                UPDATE game_result
                SET version=version+1,solved=?,attempts_used=?,duration_seconds=?,updated_at=?
                WHERE id=?
                """, solved, attempts, durationSeconds, java.sql.Timestamp.from(NOW), resultId);
        long playerId = jdbc.queryForObject("SELECT player_id FROM game_result WHERE id=?", Long.class, resultId);
        long version = jdbc.queryForObject("SELECT version FROM game_result WHERE id=?", Long.class, resultId);
        jdbc.update("""
                INSERT INTO submission (source_message_id,guild_id,channel_id,author_player_id,raw_message_content,
                    processing_state,game_result_id,received_at,updated_at)
                VALUES (?,10,20,?,'correction','RESULT_STORED',?,?,?)
                """, 200_000L + resultId * 10 + version, playerId, resultId,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        if (origin != RecordProcessingOrigin.NORMAL_CORRECTION) setOpenOrigin(resultId, origin);
    }

    private void setOpenOrigin(long resultId, RecordProcessingOrigin origin) {
        jdbc.update("""
                UPDATE record_live_evaluation SET processing_origin=?
                WHERE guild_id=10 AND game_result_id=? AND evaluation_state='OPEN'
                """, origin.name(), resultId);
    }

    private void readyBootstrap() {
        RecordBootstrapKey key = new RecordBootstrapKey(10, RecordDefinitionVersion.RECORDS_V1);
        bootstraps.register(key);
        var bootstrapClaim = bootstraps.claim(key,
                new RecordLeaseClaimRequest(NOW, NOW.plusSeconds(60))).orElseThrow();
        assertThat(bootstraps.markSucceeded(key, bootstrapClaim.token(), NOW.plusSeconds(1))).isTrue();
    }

    private void synchronizeAnnouncements() {
        List<RecordAnnouncementKey> keys = jdbc.query("""
                SELECT guild_id,channel_id,idempotency_key FROM record_announcement ORDER BY id
                """, (rs, row) -> new RecordAnnouncementKey(
                rs.getLong(1), rs.getLong(2), rs.getString(3)));
        for (RecordAnnouncementKey key : keys) {
            var deliveryClaim = announcements.claim(key,
                    new RecordLeaseClaimRequest(NOW, NOW.plusSeconds(60))).orElseThrow();
            assertThat(announcements.markSynchronized(key, deliveryClaim.token(), NOW.plusSeconds(1))).isTrue();
        }
    }

    private de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot announcementFor(long resultId) {
        return announcements.find(new RecordAnnouncementKey(
                10, 20, "live-result:" + resultId + ":player:1:LIVE_EVALUATION")).orElseThrow();
    }

    private int validResultEvents(long resultId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM record_event
                WHERE validity='VALID' AND event_type='RESULT_RECORD_BROKEN'
                  AND split_part(new_source_key, ':', 1)=?
                """, Integer.class, Long.toString(resultId));
    }

    private int validEventsOfType(String eventType) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM record_event WHERE validity='VALID' AND event_type=?",
                Integer.class, eventType);
    }

    private int validPublicStreakEvents() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM record_event
                WHERE validity='VALID' AND new_source_type='STREAK_RUN'
                  AND event_type <> 'RECORD_INITIALIZED'
                """, Integer.class);
    }

    private int streakStateCount(LocalDate start, LocalDate end) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM record_state
                WHERE source_type='STREAK_RUN' AND source_streak_start_date=? AND streak_end_date=?
                """, Integer.class, start, end);
    }

    private void assertStreakSource(
            String definitionKey,
            String scopeType,
            String scopeKey,
            LocalDate expectedStart,
            LocalDate expectedEnd) {
        assertThat(jdbc.queryForMap("""
                SELECT source_streak_start_date,streak_end_date FROM record_state
                WHERE guild_id=10 AND definition_key=? AND scope_type=? AND scope_key=?
                """, definitionKey, scopeType, scopeKey))
                .containsEntry("source_streak_start_date", java.sql.Date.valueOf(expectedStart))
                .containsEntry("streak_end_date", java.sql.Date.valueOf(expectedEnd));
    }

    private long resultId(long playerId, LocalDate gameDate) {
        return jdbc.queryForObject("""
                SELECT id FROM game_result WHERE player_id=? AND game_type='GRIDWORDS' AND game_date=?
                """, Long.class, playerId, gameDate);
    }

    private int count(String table) {
        if (!Set.of("record_state", "record_event", "record_announcement").contains(table)) {
            throw new IllegalArgumentException("unsupported count table");
        }
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private String stateFingerprint() {
        return jdbc.queryForObject("""
                SELECT COALESCE(string_agg(
                    definition_key || '|' || scope_key || '|' || lock_version || '|' ||
                    COALESCE(source_game_result_id::text, '') || '|' ||
                    COALESCE(source_streak_start_date::text, '') || '|' ||
                    COALESCE(streak_end_date::text, ''), ',' ORDER BY definition_key, scope_key), '')
                FROM record_state
                """, String.class);
    }

    private long serverResultSource(String metricSlug) {
        return jdbc.queryForObject("""
                SELECT source_game_result_id FROM record_state
                WHERE definition_key=? AND scope_type='SERVER_INDIVIDUAL'
                """, Long.class, "result.gridwords." + metricSlug + ".server-individual");
    }

    private void assertNoDuplicateValidFacts() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT idempotency_key FROM record_event WHERE validity='VALID'
                    GROUP BY idempotency_key HAVING count(*) > 1
                ) duplicate
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT announcement_id,event_id FROM record_announcement_event
                    GROUP BY announcement_id,event_id HAVING count(*) > 1
                ) duplicate
                """, Integer.class)).isZero();
    }

    private PostgresRecordLiveEvaluationStore terminalFailingStore() {
        return new PostgresRecordLiveEvaluationStore(jdbc, clock) {
            @Override public boolean markSucceeded(RecordLiveEvaluationKey key, UUID token, Instant completedAt) {
                return false;
            }
        };
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

    private enum WriteFailurePoint { STATE, EVENT_APPEND, ANNOUNCEMENT, TERMINAL }

    private record ProcessorFixture(
            PostgresRecordLiveEvaluationStore work,
            PostgresRecordStateStore states,
            PostgresRecordEventStore events,
            PostgresRecordAnnouncementStore announcements,
            PostgresRecordBootstrapStore bootstraps,
            PostgresRecordLiveHistoryQuery history,
            RecordTransactionRunner transactions) {}

    private static final class LatchingHistoryQuery implements RecordLiveHistoryQuery {
        private final RecordLiveHistoryQuery delegate;
        private final CountDownLatch read;
        private final CountDownLatch release;
        private boolean first = true;

        private LatchingHistoryQuery(
                RecordLiveHistoryQuery delegate, CountDownLatch read, CountDownLatch release) {
            this.delegate = delegate;
            this.read = read;
            this.release = release;
        }

        @Override public RecordHistorySnapshot loadFor(RecordLiveEvaluationKey key) {
            return loadFor(key, RecordProcessingOrigin.LIVE_SUBMISSION);
        }

        @Override public synchronized RecordHistorySnapshot loadFor(
                RecordLiveEvaluationKey key, RecordProcessingOrigin origin) {
            RecordHistorySnapshot snapshot = delegate.loadFor(key, origin);
            if (first) {
                first = false;
                read.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("history latch timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("history latch interrupted", exception);
                }
            }
            return snapshot;
        }

        @Override public boolean isCurrent(
                RecordLiveEvaluationKey key,
                RecordProcessingOrigin origin,
                RecordHistorySnapshot expected) {
            return delegate.isCurrent(key, origin, expected);
        }
    }

    private static final class FailingStateStore implements RecordStateStore {
        private final RecordStateStore delegate;
        private boolean failed;

        private FailingStateStore(RecordStateStore delegate) { this.delegate = delegate; }
        @Override public Optional<RecordStateSnapshot> find(RecordStateKey key) { return delegate.find(key); }
        @Override public List<RecordStateSnapshot> findAll(long guildId, RecordDefinitionVersion version) {
            return delegate.findAll(guildId, version);
        }
        @Override public RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write) {
            RecordStateInitialization result = delegate.initialize(key, write);
            fail();
            return result;
        }
        @Override public RecordStateUpdateResult update(RecordStateUpdate update) {
            RecordStateUpdateResult result = delegate.update(update);
            fail();
            return result;
        }
        @Override public boolean remove(RecordStateKey key, RecordLockVersion expectedLockVersion) {
            boolean result = delegate.remove(key, expectedLockVersion);
            fail();
            return result;
        }
        private void fail() {
            if (!failed) {
                failed = true;
                throw new IllegalStateException("injected state write failure");
            }
        }
    }

    private static final class FailingEventStore implements RecordEventStore {
        private final RecordEventStore delegate;
        private final boolean failAppend;
        private final boolean failInvalidate;
        private boolean failed;

        private FailingEventStore(RecordEventStore delegate, boolean failAppend, boolean failInvalidate) {
            this.delegate = delegate;
            this.failAppend = failAppend;
            this.failInvalidate = failInvalidate;
        }
        @Override public RecordEventAppendResult append(RecordEventDraft draft) {
            RecordEventAppendResult result = delegate.append(draft);
            if (failAppend) fail("event append");
            return result;
        }
        @Override public Optional<RecordEventSnapshot> find(UUID eventId) { return delegate.find(eventId); }
        @Override public List<RecordEventSnapshot> findByTriggerKey(long guildId, String triggerKey) {
            return delegate.findByTriggerKey(guildId, triggerKey);
        }
        @Override public List<RecordEventSnapshot> findBySource(long guildId, RecordSourceReference source) {
            return delegate.findBySource(guildId, source);
        }
        @Override public List<RecordEventSnapshot> findByResultId(long guildId, long resultId) {
            return delegate.findByResultId(guildId, resultId);
        }
        @Override public List<RecordEventSnapshot> findResultFamily(
                long guildId, List<RecordStateKey> families, LocalDate affectedFrom) {
            return delegate.findResultFamily(guildId, families, affectedFrom);
        }
        @Override public List<RecordEventSnapshot> findStreakFamily(
                long guildId, List<RecordStateKey> families, LocalDate affectedFrom) {
            return delegate.findStreakFamily(guildId, families, affectedFrom);
        }
        @Override public boolean invalidate(UUID eventId, Instant invalidatedAt) {
            boolean result = delegate.invalidate(eventId, invalidatedAt);
            if (failInvalidate) fail("event invalidation");
            return result;
        }
        @Override public boolean supersede(UUID eventId, UUID supersedingEventId, Instant invalidatedAt) {
            return delegate.supersede(eventId, supersedingEventId, invalidatedAt);
        }
        private void fail(String step) {
            if (!failed) {
                failed = true;
                throw new IllegalStateException("injected " + step + " failure");
            }
        }
    }

    private static final class FailingAnnouncementStore implements RecordAnnouncementStore {
        private final RecordAnnouncementStore delegate;
        private boolean failed;

        private FailingAnnouncementStore(RecordAnnouncementStore delegate) { this.delegate = delegate; }
        @Override public de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot registerOrUpdate(
                de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration registration) {
            var result = delegate.registerOrUpdate(registration);
            if (!failed) {
                failed = true;
                throw new IllegalStateException("injected announcement write failure");
            }
            return result;
        }
        @Override public Optional<de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot> find(
                RecordAnnouncementKey key) { return delegate.find(key); }
        @Override public List<de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot> findByEventId(
                UUID eventId) { return delegate.findByEventId(eventId); }
        @Override public Optional<RecordLeaseClaim> claim(
                RecordAnnouncementKey key, RecordLeaseClaimRequest request) { return delegate.claim(key, request); }
        @Override public boolean renewLease(
                RecordAnnouncementKey key, UUID token, RecordLeaseClaimRequest request) {
            return delegate.renewLease(key, token, request);
        }
        @Override public boolean replaceMessages(
                RecordAnnouncementKey key, UUID token,
                List<de.venomenon.gridwordsbot.domain.record.RecordAnnouncementMessage> messages) {
            return delegate.replaceMessages(key, token, messages);
        }
        @Override public boolean markSynchronized(RecordAnnouncementKey key, UUID token, Instant at) {
            return delegate.markSynchronized(key, token, at);
        }
        @Override public boolean markRetryableFailure(
                RecordAnnouncementKey key, UUID token,
                de.venomenon.gridwordsbot.domain.record.RecordWorkFailure failure, Instant nextRetryAt) {
            return delegate.markRetryableFailure(key, token, failure, nextRetryAt);
        }
        @Override public boolean markPermanentFailure(
                RecordAnnouncementKey key, UUID token,
                de.venomenon.gridwordsbot.domain.record.RecordWorkFailure failure, Instant completedAt) {
            return delegate.markPermanentFailure(key, token, failure, completedAt);
        }
        @Override public boolean markExternallyRemoved(RecordAnnouncementKey key, UUID token, Instant removedAt) {
            return delegate.markExternallyRemoved(key, token, removedAt);
        }
    }
}
