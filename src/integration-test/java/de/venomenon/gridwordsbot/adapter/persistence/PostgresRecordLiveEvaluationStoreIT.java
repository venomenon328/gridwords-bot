package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationState;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class PostgresRecordLiveEvaluationStoreIT {
    private static final Instant NOW = Instant.parse("2026-08-05T21:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.6-alpine");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private PostgresRecordLiveEvaluationStore work;

    @BeforeAll
    void migrate() throws Exception {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        work = storeAt(NOW);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM record_live_evaluation");
        jdbc.update("DELETE FROM submission_attachment");
        jdbc.update("DELETE FROM submission");
        jdbc.update("DELETE FROM game_result");
        jdbc.update("DELETE FROM player_participation_period");
        jdbc.update("DELETE FROM player");
    }

    @Test
    void resultStoredTransitionRegistersOneCrashSafeLiveJob() {
        long resultId = insertResultAndReceivedSubmission(1, 100, 1000);

        transitionToResultStored(1000, resultId);
        transitionToResultStored(1000, resultId);

        List<de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationSnapshot> jobs =
                work.findAll(100, resultId);
        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.key().gameResultVersion()).isZero();
            assertThat(job.processingOrigin()).isEqualTo(RecordProcessingOrigin.LIVE_SUBMISSION);
            assertThat(job.state()).isEqualTo(RecordLiveEvaluationState.OPEN);
            assertThat(job.attemptCount()).isZero();
        });
    }

    @Test
    void newerResultVersionSupersedesClaimedWorkAndUsesCorrectionOrigin() {
        long resultId = insertResultAndReceivedSubmission(1, 100, 1000);
        transitionToResultStored(1000, resultId);
        RecordLiveEvaluationClaim stale = work.claimNext(request(NOW, NOW.plusSeconds(30))).orElseThrow();

        jdbc.update("""
                UPDATE game_result
                SET attempts_used = 2, raw_share_text = 'corrected', version = version + 1, updated_at = ?
                WHERE id = ?
                """, java.sql.Timestamp.from(NOW.plusSeconds(1)), resultId);
        jdbc.update("""
                UPDATE submission
                SET updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND processing_state = 'RESULT_STORED'
                """, java.sql.Timestamp.from(NOW.plusSeconds(1)), 1000L);

        List<de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationSnapshot> jobs =
                work.findAll(100, resultId);
        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).state()).isEqualTo(RecordLiveEvaluationState.SUPERSEDED);
        assertThat(jobs.get(0).completedAt()).isPresent();
        assertThat(jobs.get(1).key().gameResultVersion()).isEqualTo(1);
        assertThat(jobs.get(1).processingOrigin()).isEqualTo(RecordProcessingOrigin.NORMAL_CORRECTION);
        assertThat(jobs.get(1).state()).isEqualTo(RecordLiveEvaluationState.OPEN);
        assertThat(work.markSucceeded(stale.key(), stale.token(), NOW.plusSeconds(2))).isFalse();
    }

    @Test
    void concurrentWorkersClaimExactlyOnceAndExpiredLeaseCanBeTakenOver() throws Exception {
        long resultId = insertResultAndReceivedSubmission(1, 100, 1000);
        transitionToResultStored(1000, resultId);

        var firstStore = independentStoreAt(NOW);
        var secondStore = independentStoreAt(NOW);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<java.util.Optional<RecordLiveEvaluationClaim>> first = pool.submit(() -> {
                start.await();
                return firstStore.claimNext(request(NOW, NOW.plusSeconds(10)));
            });
            Future<java.util.Optional<RecordLiveEvaluationClaim>> second = pool.submit(() -> {
                start.await();
                return secondStore.claimNext(request(NOW, NOW.plusSeconds(10)));
            });
            start.countDown();
            List<RecordLiveEvaluationClaim> claims = java.util.stream.Stream.of(first.get(), second.get())
                    .flatMap(java.util.Optional::stream)
                    .toList();
            assertThat(claims).singleElement();
            RecordLiveEvaluationClaim original = claims.getFirst();

            PostgresRecordLiveEvaluationStore takeover = independentStoreAt(NOW.plusSeconds(10));
            RecordLiveEvaluationClaim replacement = takeover
                    .claimNext(request(NOW.plusSeconds(10), NOW.plusSeconds(20)))
                    .orElseThrow();
            assertThat(replacement.key()).isEqualTo(original.key());
            assertThat(replacement.token()).isNotEqualTo(original.token());
            assertThat(replacement.attemptCount()).isEqualTo(2);
            assertThat(takeover.markSucceeded(original.key(), original.token(), NOW.plusSeconds(11))).isFalse();
            assertThat(takeover.markSucceeded(replacement.key(), replacement.token(), NOW.plusSeconds(11))).isTrue();
        }
    }

    @Test
    void leaseRenewalIsTokenFencedAndKeepsTheJobUnavailableUntilTheExtendedDeadline() {
        long resultId = insertResultAndReceivedSubmission(1, 100, 1000);
        transitionToResultStored(1000, resultId);
        RecordLiveEvaluationClaim claim = work.claimNext(request(NOW, NOW.plusSeconds(10))).orElseThrow();

        assertThat(work.renewLease(
                        claim.key(),
                        claim.token(),
                        request(NOW.plusSeconds(5), NOW.plusSeconds(20))))
                .isTrue();
        assertThat(work.renewLease(
                        claim.key(),
                        java.util.UUID.randomUUID(),
                        request(NOW.plusSeconds(6), NOW.plusSeconds(21))))
                .isFalse();
        assertThat(storeAt(NOW.plusSeconds(10))
                .claimNext(request(NOW.plusSeconds(10), NOW.plusSeconds(30))))
                .isEmpty();

        RecordLiveEvaluationClaim takeover = storeAt(NOW.plusSeconds(20))
                .claimNext(request(NOW.plusSeconds(20), NOW.plusSeconds(30)))
                .orElseThrow();
        assertThat(takeover.token()).isNotEqualTo(claim.token());
        assertThat(work.markSucceeded(claim.key(), claim.token(), NOW.plusSeconds(21))).isFalse();
    }

    @Test
    void retryIsNotClaimableBeforeBackoffAndRestartsWhenDue() {
        long resultId = insertResultAndReceivedSubmission(1, 100, 1000);
        transitionToResultStored(1000, resultId);
        RecordLiveEvaluationClaim first = work.claimNext(request(NOW, NOW.plusSeconds(20))).orElseThrow();

        assertThat(work.markRetryableFailure(
                        first.key(),
                        first.token(),
                        new RecordWorkFailure(RecordWorkFailureCategory.RETRYABLE, "temporary record evaluation failure"),
                        NOW.plusSeconds(60)))
                .isTrue();
        assertThat(storeAt(NOW.plusSeconds(59))
                .claimNext(request(NOW.plusSeconds(59), NOW.plusSeconds(70))))
                .isEmpty();

        RecordLiveEvaluationClaim retry = storeAt(NOW.plusSeconds(60))
                .claimNext(request(NOW.plusSeconds(60), NOW.plusSeconds(90)))
                .orElseThrow();
        assertThat(retry.key()).isEqualTo(first.key());
        assertThat(retry.attemptCount()).isEqualTo(2);
        assertThat(storeAt(NOW.plusSeconds(60)).markPermanentFailure(
                        retry.key(),
                        retry.token(),
                        new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT, "invalid record invariant"),
                        NOW.plusSeconds(61)))
                .isTrue();
        assertThat(work.find(first.key()).orElseThrow().state())
                .isEqualTo(RecordLiveEvaluationState.FAILED_PERMANENT);
    }

    @Test
    void submissionAndJobRegistrationRollbackTogether() {
        long resultId = insertResultAndReceivedSubmission(1, 100, 1000);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    transitionToResultStored(1000, resultId);
                    throw new IllegalStateException("simulated failure after trigger");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "SELECT processing_state FROM submission WHERE source_message_id = 1000",
                String.class)).isEqualTo("RECEIVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_live_evaluation", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_result WHERE id = ?", Integer.class, resultId))
                .isOne();
    }

    @Test
    void explicitRegistrationIsIdempotentAndRejectsContradictoryOrigin() {
        long resultId = insertResultAndReceivedSubmission(1, 100, 1000);
        RecordLiveEvaluationKey key = new RecordLiveEvaluationKey(100, resultId, 0);

        assertThat(work.register(key, RecordProcessingOrigin.ADMINISTRATIVE_REPAIR).state())
                .isEqualTo(RecordLiveEvaluationState.OPEN);
        assertThat(work.register(key, RecordProcessingOrigin.ADMINISTRATIVE_REPAIR).state())
                .isEqualTo(RecordLiveEvaluationState.OPEN);
        assertThatThrownBy(() -> work.register(key, RecordProcessingOrigin.REPLAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different origin");
        assertThatThrownBy(() -> work.register(key, RecordProcessingOrigin.BOOTSTRAP))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> work.register(key, RecordProcessingOrigin.DAY_CLOSE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(work.findAll(100, resultId)).hasSize(1);
    }

    private long insertResultAndReceivedSubmission(long playerId, long guildId, long sourceMessageId) {
        jdbc.update("""
                INSERT INTO player (
                    discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, 'Player', TRUE, FALSE, FALSE, ?, ?)
                """, playerId, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        Long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts,
                    duration_seconds, normalized_board, raw_share_text, parser_version, created_at, updated_at)
                VALUES (?, 'GRIDWORDS', DATE '2026-08-05', TRUE, 3, 6, 60,
                    'ABCDE', 'share', 'gridwords-share-v1', ?, ?)
                RETURNING id
                """, Long.class, playerId, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                    processing_state, received_at, updated_at)
                VALUES (?, ?, 200, ?, 'share', 'RECEIVED', ?, ?)
                """, sourceMessageId, guildId, playerId,
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        return java.util.Objects.requireNonNull(resultId);
    }

    private void transitionToResultStored(long sourceMessageId, long resultId) {
        jdbc.update("""
                UPDATE submission
                SET game_result_id = ?, processing_state = 'RESULT_STORED',
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                """, resultId, java.sql.Timestamp.from(NOW), sourceMessageId);
    }

    private PostgresRecordLiveEvaluationStore storeAt(Instant instant) {
        return new PostgresRecordLiveEvaluationStore(
                jdbc, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private PostgresRecordLiveEvaluationStore independentStoreAt(Instant instant) {
        var independent = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        return new PostgresRecordLiveEvaluationStore(
                new JdbcTemplate(independent), Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static RecordLeaseClaimRequest request(Instant claimedAt, Instant leaseUntil) {
        return new RecordLeaseClaimRequest(claimedAt, leaseUntil);
    }
}
