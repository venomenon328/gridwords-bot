package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationState;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.port.out.RecordLiveEvaluationStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL work queue for crash-safe, versioned live record evaluations. */
public class PostgresRecordLiveEvaluationStore implements RecordLiveEvaluationStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresRecordLiveEvaluationStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public RecordLiveEvaluationSnapshot register(
            RecordLiveEvaluationKey key,
            RecordProcessingOrigin processingOrigin) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(processingOrigin, "processingOrigin");
        if (processingOrigin == RecordProcessingOrigin.BOOTSTRAP
                || processingOrigin == RecordProcessingOrigin.DAY_CLOSE) {
            throw new IllegalArgumentException("processing origin is not a result-live evaluation origin");
        }
        lockResult(key.gameResultId());
        Long currentVersion = jdbc.queryForObject(
                "SELECT version FROM game_result WHERE id = ?",
                Long.class,
                key.gameResultId());
        if (currentVersion == null) {
            throw new IllegalStateException("game result version is missing");
        }
        boolean superseded = currentVersion > key.gameResultVersion()
                || Boolean.TRUE.equals(jdbc.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1 FROM record_live_evaluation
                            WHERE guild_id = ? AND game_result_id = ? AND game_result_version > ?
                        )
                        """,
                        Boolean.class,
                        key.guildId(),
                        key.gameResultId(),
                        key.gameResultVersion()));
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO record_live_evaluation (
                    guild_id, game_result_id, game_result_version, processing_origin,
                    evaluation_state, attempt_count, completed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?)
                ON CONFLICT (guild_id, game_result_id, game_result_version) DO NOTHING
                """,
                key.guildId(),
                key.gameResultId(),
                key.gameResultVersion(),
                processingOrigin.name(),
                superseded ? RecordLiveEvaluationState.SUPERSEDED.name() : RecordLiveEvaluationState.OPEN.name(),
                superseded ? RecordJdbcMapping.utc(now) : null,
                RecordJdbcMapping.utc(now),
                RecordJdbcMapping.utc(now));

        if (!superseded) {
            supersedeOlderActiveWork(key, now);
        }
        RecordLiveEvaluationSnapshot registered = find(key)
                .orElseThrow(() -> new IllegalStateException("registered live evaluation is missing"));
        if (registered.processingOrigin() != processingOrigin) {
            throw new IllegalStateException("live evaluation key is already registered with a different origin");
        }
        return registered;
    }

    @Override
    public Optional<RecordLiveEvaluationSnapshot> find(RecordLiveEvaluationKey key) {
        java.util.Objects.requireNonNull(key, "key");
        return jdbc.query(
                        """
                        SELECT * FROM record_live_evaluation
                        WHERE guild_id = ? AND game_result_id = ? AND game_result_version = ?
                        """,
                        (rs, row) -> snapshot(rs),
                        key.guildId(),
                        key.gameResultId(),
                        key.gameResultVersion())
                .stream()
                .findFirst();
    }

    @Override
    public List<RecordLiveEvaluationSnapshot> findAll(long guildId, long gameResultId) {
        if (guildId <= 0 || gameResultId <= 0) {
            throw new IllegalArgumentException("guildId and gameResultId must be positive");
        }
        return jdbc.query(
                """
                SELECT * FROM record_live_evaluation
                WHERE guild_id = ? AND game_result_id = ?
                ORDER BY game_result_version
                """,
                (rs, row) -> snapshot(rs),
                guildId,
                gameResultId);
    }

    @Override
    public Optional<RecordLiveEvaluationClaim> claimNext(RecordLeaseClaimRequest request) {
        java.util.Objects.requireNonNull(request, "request");
        UUID token = UUID.randomUUID();
        return jdbc.query(
                        """
                        WITH candidate AS (
                            SELECT guild_id, game_result_id, game_result_version
                            FROM record_live_evaluation
                            WHERE evaluation_state = 'OPEN'
                               OR (evaluation_state = 'RETRYABLE' AND next_retry_at <= ?)
                               OR (evaluation_state = 'CLAIMED' AND claim_until <= ?)
                            ORDER BY created_at, guild_id, game_result_id, game_result_version
                            FOR UPDATE SKIP LOCKED
                            LIMIT 1
                        )
                        UPDATE record_live_evaluation work
                        SET evaluation_state = 'CLAIMED',
                            claim_token = ?,
                            claim_until = ?,
                            started_at = COALESCE(work.started_at, ?),
                            completed_at = NULL,
                            attempt_count = work.attempt_count + 1,
                            next_retry_at = NULL,
                            failure_category = NULL,
                            safe_error = NULL,
                            updated_at = ?
                        FROM candidate
                        WHERE work.guild_id = candidate.guild_id
                          AND work.game_result_id = candidate.game_result_id
                          AND work.game_result_version = candidate.game_result_version
                        RETURNING work.guild_id, work.game_result_id, work.game_result_version,
                                  work.processing_origin, work.claim_token, work.claim_until, work.attempt_count
                        """,
                        (rs, row) -> new RecordLiveEvaluationClaim(
                                new RecordLiveEvaluationKey(
                                        rs.getLong("guild_id"),
                                        rs.getLong("game_result_id"),
                                        rs.getLong("game_result_version")),
                                RecordProcessingOrigin.valueOf(rs.getString("processing_origin")),
                                rs.getObject("claim_token", UUID.class),
                                RecordJdbcMapping.instant(rs, "claim_until"),
                                rs.getInt("attempt_count")),
                        RecordJdbcMapping.utc(request.claimedAt()),
                        RecordJdbcMapping.utc(request.claimedAt()),
                        token,
                        RecordJdbcMapping.utc(request.leaseUntil()),
                        RecordJdbcMapping.utc(request.claimedAt()),
                        RecordJdbcMapping.utc(request.claimedAt()))
                .stream()
                .findFirst();
    }

    @Override
    public boolean fence(RecordLiveEvaluationKey key, UUID token, Instant now) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(token, "token");
        java.util.Objects.requireNonNull(now, "now");
        return jdbc.query("""
                SELECT 1 FROM record_live_evaluation work
                JOIN game_result result ON result.id=work.game_result_id
                WHERE work.guild_id=? AND work.game_result_id=? AND work.game_result_version=?
                  AND work.evaluation_state='CLAIMED' AND work.claim_token=? AND work.claim_until>?
                  AND result.version=work.game_result_version
                  AND NOT EXISTS (
                      SELECT 1 FROM record_live_evaluation newer
                      WHERE newer.guild_id=work.guild_id AND newer.game_result_id=work.game_result_id
                        AND newer.game_result_version>work.game_result_version
                        AND newer.evaluation_state<>'SUPERSEDED')
                FOR UPDATE
                """, (rs, row) -> 1, key.guildId(), key.gameResultId(), key.gameResultVersion(), token,
                RecordJdbcMapping.utc(now)).size() == 1;
    }

    @Override
    public boolean renewLease(
            RecordLiveEvaluationKey key,
            UUID token,
            RecordLeaseClaimRequest request) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(token, "token");
        java.util.Objects.requireNonNull(request, "request");
        return jdbc.update(
                        """
                        UPDATE record_live_evaluation
                        SET claim_until = ?, updated_at = ?
                        WHERE guild_id = ? AND game_result_id = ? AND game_result_version = ?
                          AND evaluation_state = 'CLAIMED' AND claim_token = ? AND claim_until > ?
                        """,
                        RecordJdbcMapping.utc(request.leaseUntil()),
                        RecordJdbcMapping.utc(request.claimedAt()),
                        key.guildId(),
                        key.gameResultId(),
                        key.gameResultVersion(),
                        token,
                        RecordJdbcMapping.utc(request.claimedAt()))
                == 1;
    }

    @Override
    public boolean markSucceeded(RecordLiveEvaluationKey key, UUID token, Instant completedAt) {
        return terminal(key, token, completedAt, RecordLiveEvaluationState.SUCCEEDED, null);
    }

    @Override
    public boolean markRetryableFailure(
            RecordLiveEvaluationKey key,
            UUID token,
            RecordWorkFailure failure,
            Instant nextRetryAt) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(token, "token");
        java.util.Objects.requireNonNull(failure, "failure");
        java.util.Objects.requireNonNull(nextRetryAt, "nextRetryAt");
        if (failure.category() == RecordWorkFailureCategory.PERMANENT) {
            throw new IllegalArgumentException("retryable failure cannot be permanent");
        }
        Instant now = clock.instant();
        return jdbc.update(
                        """
                        UPDATE record_live_evaluation
                        SET evaluation_state = 'RETRYABLE',
                            claim_token = NULL,
                            claim_until = NULL,
                            completed_at = NULL,
                            next_retry_at = ?,
                            failure_category = ?,
                            safe_error = ?,
                            updated_at = ?
                        WHERE guild_id = ? AND game_result_id = ? AND game_result_version = ?
                          AND evaluation_state = 'CLAIMED' AND claim_token = ? AND claim_until > ?
                        """,
                        RecordJdbcMapping.utc(nextRetryAt),
                        failure.category().name(),
                        failure.safeMessage(),
                        RecordJdbcMapping.utc(now),
                        key.guildId(),
                        key.gameResultId(),
                        key.gameResultVersion(),
                        token,
                        RecordJdbcMapping.utc(now))
                == 1;
    }

    @Override
    public boolean markPermanentFailure(
            RecordLiveEvaluationKey key,
            UUID token,
            RecordWorkFailure failure,
            Instant completedAt) {
        java.util.Objects.requireNonNull(failure, "failure");
        if (failure.category() != RecordWorkFailureCategory.PERMANENT) {
            throw new IllegalArgumentException("permanent failure needs PERMANENT category");
        }
        return terminal(key, token, completedAt, RecordLiveEvaluationState.FAILED_PERMANENT, failure);
    }

    private boolean terminal(
            RecordLiveEvaluationKey key,
            UUID token,
            Instant completedAt,
            RecordLiveEvaluationState state,
            RecordWorkFailure failure) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(token, "token");
        java.util.Objects.requireNonNull(completedAt, "completedAt");
        Instant now = clock.instant();
        return jdbc.update(
                        """
                        UPDATE record_live_evaluation
                        SET evaluation_state = ?,
                            claim_token = NULL,
                            claim_until = NULL,
                            completed_at = ?,
                            next_retry_at = NULL,
                            failure_category = ?,
                            safe_error = ?,
                            updated_at = ?
                        WHERE guild_id = ? AND game_result_id = ? AND game_result_version = ?
                          AND evaluation_state = 'CLAIMED' AND claim_token = ? AND claim_until > ?
                        """,
                        state.name(),
                        RecordJdbcMapping.utc(completedAt),
                        failure == null ? null : failure.category().name(),
                        failure == null ? null : failure.safeMessage(),
                        RecordJdbcMapping.utc(completedAt),
                        key.guildId(),
                        key.gameResultId(),
                        key.gameResultVersion(),
                        token,
                        RecordJdbcMapping.utc(now))
                == 1;
    }

    private void supersedeOlderActiveWork(RecordLiveEvaluationKey key, Instant completedAt) {
        jdbc.update(
                """
                UPDATE record_live_evaluation
                SET evaluation_state = 'SUPERSEDED',
                    claim_token = NULL,
                    claim_until = NULL,
                    next_retry_at = NULL,
                    failure_category = NULL,
                    safe_error = NULL,
                    completed_at = ?,
                    updated_at = ?
                WHERE guild_id = ? AND game_result_id = ? AND game_result_version < ?
                  AND evaluation_state IN ('OPEN', 'CLAIMED', 'RETRYABLE')
                """,
                RecordJdbcMapping.utc(completedAt),
                RecordJdbcMapping.utc(completedAt),
                key.guildId(),
                key.gameResultId(),
                key.gameResultVersion());
    }

    private void lockResult(long gameResultId) {
        if (jdbc.queryForList(
                        "SELECT id FROM game_result WHERE id = ? FOR UPDATE",
                        Long.class,
                        gameResultId)
                .isEmpty()) {
            throw new IllegalStateException("game result not found: " + gameResultId);
        }
    }

    private RecordLiveEvaluationSnapshot snapshot(ResultSet rs) throws SQLException {
        String category = rs.getString("failure_category");
        return new RecordLiveEvaluationSnapshot(
                new RecordLiveEvaluationKey(
                        rs.getLong("guild_id"),
                        rs.getLong("game_result_id"),
                        rs.getLong("game_result_version")),
                RecordProcessingOrigin.valueOf(rs.getString("processing_origin")),
                RecordLiveEvaluationState.valueOf(rs.getString("evaluation_state")),
                Optional.ofNullable(rs.getObject("claim_token", UUID.class)),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "claim_until")),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "started_at")),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "completed_at")),
                rs.getInt("attempt_count"),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "next_retry_at")),
                category == null
                        ? Optional.empty()
                        : Optional.of(new RecordWorkFailure(
                                RecordWorkFailureCategory.valueOf(category),
                                rs.getString("safe_error"))),
                RecordJdbcMapping.instant(rs, "created_at"),
                RecordJdbcMapping.instant(rs, "updated_at"));
    }
}
