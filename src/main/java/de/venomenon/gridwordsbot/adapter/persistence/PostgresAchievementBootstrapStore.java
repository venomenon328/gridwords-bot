package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import de.venomenon.gridwordsbot.port.out.AchievementBootstrapStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresAchievementBootstrapStore implements AchievementBootstrapStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresAchievementBootstrapStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AchievementWork.BootstrapSnapshot register(AchievementWork.BootstrapKey key) {
        Objects.requireNonNull(key, "key");
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO achievement_bootstrap_state (
                    guild_id, definition_version, bootstrap_state, attempt_count, created_at, updated_at)
                VALUES (?, ?, 'OPEN', 0, ?, ?)
                ON CONFLICT (guild_id, definition_version) DO NOTHING
                """, key.guildId(), key.definitionVersion().value(), Timestamp.from(now), Timestamp.from(now));
        return find(key).orElseThrow();
    }

    @Override
    public Optional<AchievementWork.BootstrapSnapshot> find(AchievementWork.BootstrapKey key) {
        Objects.requireNonNull(key, "key");
        return jdbc.query("""
                SELECT * FROM achievement_bootstrap_state
                 WHERE guild_id=? AND definition_version=?
                """, AchievementJdbcMapping::bootstrap, key.guildId(), key.definitionVersion().value()).stream().findFirst();
    }

    @Override
    public Optional<AchievementWork.LeaseClaim> claim(
            AchievementWork.BootstrapKey key, AchievementWork.LeaseClaimRequest request) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(request, "request");
        UUID token = UUID.randomUUID();
        int updated = jdbc.update("""
                UPDATE achievement_bootstrap_state
                   SET bootstrap_state='CLAIMED', claim_token=?, claim_until=?,
                       started_at=COALESCE(started_at, ?), attempt_count=attempt_count+1,
                       next_retry_at=NULL, failure_category=NULL, safe_error=NULL, completed_at=NULL, updated_at=?
                 WHERE guild_id=? AND definition_version=?
                   AND (
                       bootstrap_state='OPEN'
                       OR (bootstrap_state='RETRYABLE' AND next_retry_at <= ?)
                       OR (bootstrap_state='CLAIMED' AND claim_until <= ?)
                   )
                """, token, Timestamp.from(request.leaseUntil()), Timestamp.from(request.claimedAt()),
                Timestamp.from(request.claimedAt()), key.guildId(), key.definitionVersion().value(),
                Timestamp.from(request.claimedAt()), Timestamp.from(request.claimedAt()));
        return updated == 1 ? Optional.of(new AchievementWork.LeaseClaim(token, request.leaseUntil())) : Optional.empty();
    }

    @Override
    public boolean renewLease(
            AchievementWork.BootstrapKey key, UUID token, AchievementWork.LeaseClaimRequest request) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(request, "request");
        return jdbc.update("""
                UPDATE achievement_bootstrap_state
                   SET claim_until=?, updated_at=?
                 WHERE guild_id=? AND definition_version=? AND bootstrap_state='CLAIMED'
                   AND claim_token=? AND claim_until > ?
                """, Timestamp.from(request.leaseUntil()), Timestamp.from(request.claimedAt()),
                key.guildId(), key.definitionVersion().value(), token, Timestamp.from(request.claimedAt())) == 1;
    }

    @Override
    public boolean markSucceeded(AchievementWork.BootstrapKey key, UUID token, Instant completedAt) {
        Objects.requireNonNull(completedAt, "completedAt");
        return finish(key, token, "SUCCEEDED", null, completedAt);
    }

    @Override
    public boolean markRetryableFailure(
            AchievementWork.BootstrapKey key,
            UUID token,
            AchievementWork.Failure failure,
            Instant nextRetryAt) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(nextRetryAt, "nextRetryAt");
        if (failure.category() == AchievementWork.FailureCategory.PERMANENT) {
            throw new IllegalArgumentException("permanent failure cannot be scheduled for retry");
        }
        Instant now = clock.instant();
        return jdbc.update("""
                UPDATE achievement_bootstrap_state
                   SET bootstrap_state='RETRYABLE', claim_token=NULL, claim_until=NULL, completed_at=NULL,
                       next_retry_at=?, failure_category=?, safe_error=?, updated_at=?
                 WHERE guild_id=? AND definition_version=? AND bootstrap_state='CLAIMED'
                   AND claim_token=? AND claim_until > ?
                """, Timestamp.from(nextRetryAt), failure.category().name(), failure.safeError(), Timestamp.from(now),
                key.guildId(), key.definitionVersion().value(), token, Timestamp.from(now)) == 1;
    }

    @Override
    public boolean markPermanentFailure(
            AchievementWork.BootstrapKey key,
            UUID token,
            AchievementWork.Failure failure,
            Instant completedAt) {
        Objects.requireNonNull(failure, "failure");
        if (failure.category() != AchievementWork.FailureCategory.PERMANENT) {
            throw new IllegalArgumentException("permanent completion requires PERMANENT failure category");
        }
        return finish(key, token, "FAILED_PERMANENT", failure, completedAt);
    }

    private boolean finish(
            AchievementWork.BootstrapKey key,
            UUID token,
            String state,
            AchievementWork.Failure failure,
            Instant completedAt) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(completedAt, "completedAt");
        return jdbc.update("""
                UPDATE achievement_bootstrap_state
                   SET bootstrap_state=?, claim_token=NULL, claim_until=NULL, completed_at=?, next_retry_at=NULL,
                       failure_category=?, safe_error=?, updated_at=?
                 WHERE guild_id=? AND definition_version=? AND bootstrap_state='CLAIMED'
                   AND claim_token=? AND claim_until > ?
                """, state, Timestamp.from(completedAt), failure == null ? null : failure.category().name(),
                failure == null ? null : failure.safeError(), Timestamp.from(completedAt),
                key.guildId(), key.definitionVersion().value(), token, Timestamp.from(completedAt)) == 1;
    }
}
