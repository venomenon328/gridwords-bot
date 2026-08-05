package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Short, token-fenced PostgreSQL transitions for future bootstrap orchestration. */
public final class PostgresRecordBootstrapStore implements RecordBootstrapStore {
    private final JdbcTemplate jdbc; private final Clock clock;
    public PostgresRecordBootstrapStore(JdbcTemplate jdbc, Clock clock) { this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc"); this.clock = java.util.Objects.requireNonNull(clock, "clock"); }
    @Override public RecordBootstrapSnapshot register(RecordBootstrapKey key) {
        java.util.Objects.requireNonNull(key, "key"); Instant now = clock.instant();
        jdbc.update("INSERT INTO record_bootstrap (guild_id,definition_version,bootstrap_state,attempt_count,created_at,updated_at) VALUES (?,?,'OPEN',0,?,?) ON CONFLICT (guild_id,definition_version) DO NOTHING", key.guildId(), key.definitionVersion().value(), RecordJdbcMapping.utc(now), RecordJdbcMapping.utc(now));
        return find(key).orElseThrow(() -> new IllegalStateException("registered bootstrap is missing"));
    }
    @Override public Optional<RecordBootstrapSnapshot> find(RecordBootstrapKey key) {
        return jdbc.query("SELECT * FROM record_bootstrap WHERE guild_id=? AND definition_version=?", (rs, row) -> snapshot(rs), key.guildId(), key.definitionVersion().value()).stream().findFirst();
    }
    @Override public Optional<RecordLeaseClaim> claim(RecordBootstrapKey key, RecordLeaseClaimRequest request) {
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                UPDATE record_bootstrap SET bootstrap_state='CLAIMED',claim_token=?,claim_until=?,next_retry_at=NULL,completed_at=NULL,
                    attempt_count=attempt_count+1,started_at=COALESCE(started_at,?),updated_at=?
                WHERE guild_id=? AND definition_version=? AND (bootstrap_state='OPEN' OR (bootstrap_state='RETRYABLE' AND next_retry_at<=?)
                    OR (bootstrap_state='CLAIMED' AND claim_until<=?)) RETURNING claim_token,claim_until
                """, (rs, row) -> new RecordLeaseClaim(rs.getObject("claim_token", UUID.class), RecordJdbcMapping.instant(rs, "claim_until")), token,
                RecordJdbcMapping.utc(request.leaseUntil()), RecordJdbcMapping.utc(request.claimedAt()), RecordJdbcMapping.utc(request.claimedAt()), key.guildId(), key.definitionVersion().value(), RecordJdbcMapping.utc(request.claimedAt()), RecordJdbcMapping.utc(request.claimedAt())).stream().findFirst();
    }
    @Override public boolean renewLease(RecordBootstrapKey key, UUID token, RecordLeaseClaimRequest request) {
        return jdbc.update("UPDATE record_bootstrap SET claim_until=?,updated_at=? WHERE guild_id=? AND definition_version=? AND bootstrap_state='CLAIMED' AND claim_token=? AND claim_until>?", RecordJdbcMapping.utc(request.leaseUntil()), RecordJdbcMapping.utc(request.claimedAt()), key.guildId(), key.definitionVersion().value(), token, RecordJdbcMapping.utc(request.claimedAt())) == 1;
    }
    @Override public boolean markSucceeded(RecordBootstrapKey key, UUID token, Instant completedAt) { return terminal(key, token, completedAt, "SUCCEEDED", null); }
    @Override public boolean markRetryableFailure(RecordBootstrapKey key, UUID token, RecordWorkFailure failure, Instant nextRetryAt) {
        if (failure.category() == RecordWorkFailureCategory.PERMANENT) throw new IllegalArgumentException("retryable failure cannot be permanent");
        return jdbc.update("UPDATE record_bootstrap SET bootstrap_state='RETRYABLE',claim_token=NULL,claim_until=NULL,next_retry_at=?,failure_category=?,safe_error=?,completed_at=NULL,updated_at=? WHERE guild_id=? AND definition_version=? AND bootstrap_state='CLAIMED' AND claim_token=?", RecordJdbcMapping.utc(nextRetryAt), failure.category().name(), failure.safeMessage(), RecordJdbcMapping.utc(clock.instant()), key.guildId(), key.definitionVersion().value(), token) == 1;
    }
    @Override public boolean markPermanentFailure(RecordBootstrapKey key, UUID token, RecordWorkFailure failure, Instant completedAt) {
        if (failure.category() != RecordWorkFailureCategory.PERMANENT) throw new IllegalArgumentException("permanent failure needs PERMANENT category");
        return terminal(key, token, completedAt, "FAILED_PERMANENT", failure);
    }
    private boolean terminal(RecordBootstrapKey key, UUID token, Instant completedAt, String state, RecordWorkFailure failure) {
        return jdbc.update("UPDATE record_bootstrap SET bootstrap_state=?,claim_token=NULL,claim_until=NULL,next_retry_at=NULL,failure_category=?,safe_error=?,completed_at=?,updated_at=? WHERE guild_id=? AND definition_version=? AND bootstrap_state='CLAIMED' AND claim_token=?", state, failure == null ? null : failure.category().name(), failure == null ? null : failure.safeMessage(), RecordJdbcMapping.utc(completedAt), RecordJdbcMapping.utc(completedAt), key.guildId(), key.definitionVersion().value(), token) == 1;
    }
    private RecordBootstrapSnapshot snapshot(ResultSet rs) throws SQLException {
        RecordWorkState state = RecordWorkState.valueOf(rs.getString("bootstrap_state"));
        String category = rs.getString("failure_category");
        return new RecordBootstrapSnapshot(new RecordBootstrapKey(rs.getLong("guild_id"), new de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion(rs.getString("definition_version"))), state,
                Optional.ofNullable(rs.getObject("claim_token", UUID.class)), Optional.ofNullable(RecordJdbcMapping.instant(rs,"claim_until")), Optional.ofNullable(RecordJdbcMapping.instant(rs,"started_at")), Optional.ofNullable(RecordJdbcMapping.instant(rs,"completed_at")), rs.getInt("attempt_count"), Optional.ofNullable(RecordJdbcMapping.instant(rs,"next_retry_at")),
                category == null ? Optional.empty() : Optional.of(new RecordWorkFailure(RecordWorkFailureCategory.valueOf(category),rs.getString("safe_error"))), RecordJdbcMapping.instant(rs,"created_at"),RecordJdbcMapping.instant(rs,"updated_at"));
    }
}
