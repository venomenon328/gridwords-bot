package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.record.RecordDayCloseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseKey;
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordDayCloseStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL implementation of the small, day-specific record close work queue. */
public final class PostgresRecordDayCloseStore implements RecordDayCloseStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresRecordDayCloseStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RecordDayCloseSnapshot register(RecordDayCloseKey key) {
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO record_day_close (guild_id,definition_version,game_date,close_state,attempt_count,created_at,updated_at)
                VALUES (?,? ,?,'OPEN',0,?,?) ON CONFLICT (guild_id,definition_version,game_date) DO NOTHING
                """, key.guildId(), key.definitionVersion().value(), key.gameDate(),
                RecordJdbcMapping.utc(now), RecordJdbcMapping.utc(now));
        return find(key).orElseThrow(() -> new IllegalStateException("registered day close is missing"));
    }

    @Override
    public Optional<RecordDayCloseSnapshot> find(RecordDayCloseKey key) {
        return jdbc.query("SELECT * FROM record_day_close WHERE guild_id=? AND definition_version=? AND game_date=?",
                (rs, row) -> snapshot(rs), key.guildId(), key.definitionVersion().value(), key.gameDate()).stream().findFirst();
    }

    @Override
    public Optional<LocalDate> latestSucceededDate(long guildId, String definitionVersion) {
        if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
        if (definitionVersion == null || definitionVersion.isBlank()) {
            throw new IllegalArgumentException("definitionVersion is invalid");
        }
        LocalDate latest = jdbc.query(
                "SELECT max(game_date) FROM record_day_close WHERE guild_id=? AND definition_version=? AND close_state='SUCCEEDED'",
                rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null,
                guildId,
                definitionVersion);
        return Optional.ofNullable(latest);
    }

    @Override
    public Optional<RecordDayCloseClaim> claim(RecordDayCloseKey key, RecordLeaseClaimRequest request) {
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                UPDATE record_day_close SET close_state='CLAIMED',claim_token=?,claim_until=?,next_retry_at=NULL,
                    completed_at=NULL,failure_category=NULL,safe_error=NULL,
                    attempt_count=attempt_count+1,started_at=COALESCE(started_at,?),updated_at=?
                WHERE guild_id=? AND definition_version=? AND game_date=?
                  AND (close_state='OPEN' OR (close_state='RETRYABLE' AND next_retry_at<=?)
                       OR (close_state='CLAIMED' AND claim_until<=?))
                RETURNING claim_token,claim_until,attempt_count
                """, (rs, row) -> new RecordDayCloseClaim(key, rs.getObject("claim_token", UUID.class),
                        RecordJdbcMapping.instant(rs, "claim_until"), rs.getInt("attempt_count")),
                token, RecordJdbcMapping.utc(request.leaseUntil()), RecordJdbcMapping.utc(request.claimedAt()),
                RecordJdbcMapping.utc(request.claimedAt()), key.guildId(), key.definitionVersion().value(), key.gameDate(),
                RecordJdbcMapping.utc(request.claimedAt()), RecordJdbcMapping.utc(request.claimedAt())).stream().findFirst();
    }

    @Override
    public boolean fence(RecordDayCloseKey key, UUID token, Instant now) {
        return jdbc.query("""
                SELECT 1 FROM record_day_close
                WHERE guild_id=? AND definition_version=? AND game_date=? AND close_state='CLAIMED'
                  AND claim_token=? AND claim_until>?
                FOR UPDATE
                """, (rs, row) -> 1, key.guildId(), key.definitionVersion().value(), key.gameDate(), token,
                RecordJdbcMapping.utc(now)).size() == 1;
    }

    @Override
    public boolean renewLease(RecordDayCloseKey key, UUID token, RecordLeaseClaimRequest request) {
        return jdbc.update("""
                UPDATE record_day_close SET claim_until=?,updated_at=?
                WHERE guild_id=? AND definition_version=? AND game_date=? AND close_state='CLAIMED'
                  AND claim_token=? AND claim_until>?
                """, RecordJdbcMapping.utc(request.leaseUntil()), RecordJdbcMapping.utc(request.claimedAt()),
                key.guildId(), key.definitionVersion().value(), key.gameDate(), token,
                RecordJdbcMapping.utc(request.claimedAt())) == 1;
    }

    @Override
    public boolean markSucceeded(RecordDayCloseKey key, UUID token, Instant completedAt) {
        return terminal(key, token, completedAt, RecordWorkState.SUCCEEDED, null);
    }

    @Override
    public boolean markRetryableFailure(RecordDayCloseKey key, UUID token, RecordWorkFailure failure, Instant nextRetryAt) {
        if (failure.category() == RecordWorkFailureCategory.PERMANENT) {
            throw new IllegalArgumentException("retryable failure cannot be permanent");
        }
        Instant now = clock.instant();
        return jdbc.update("""
                UPDATE record_day_close SET close_state='RETRYABLE',claim_token=NULL,claim_until=NULL,completed_at=NULL,
                    next_retry_at=?,failure_category=?,safe_error=?,updated_at=?
                WHERE guild_id=? AND definition_version=? AND game_date=? AND close_state='CLAIMED'
                  AND claim_token=? AND claim_until>?
                """, RecordJdbcMapping.utc(nextRetryAt), failure.category().name(), failure.safeMessage(),
                RecordJdbcMapping.utc(now), key.guildId(), key.definitionVersion().value(), key.gameDate(), token,
                RecordJdbcMapping.utc(now)) == 1;
    }

    @Override
    public boolean markPermanentFailure(RecordDayCloseKey key, UUID token, RecordWorkFailure failure, Instant completedAt) {
        if (failure.category() != RecordWorkFailureCategory.PERMANENT) {
            throw new IllegalArgumentException("permanent failure needs PERMANENT category");
        }
        return terminal(key, token, completedAt, RecordWorkState.FAILED_PERMANENT, failure);
    }

    private boolean terminal(RecordDayCloseKey key, UUID token, Instant completedAt, RecordWorkState state,
            RecordWorkFailure failure) {
        Instant now = clock.instant();
        return jdbc.update("""
                UPDATE record_day_close SET close_state=?,claim_token=NULL,claim_until=NULL,next_retry_at=NULL,
                    failure_category=?,safe_error=?,completed_at=?,updated_at=?
                WHERE guild_id=? AND definition_version=? AND game_date=? AND close_state='CLAIMED'
                  AND claim_token=? AND claim_until>?
                """, state.name(), failure == null ? null : failure.category().name(),
                failure == null ? null : failure.safeMessage(), RecordJdbcMapping.utc(completedAt),
                RecordJdbcMapping.utc(completedAt), key.guildId(), key.definitionVersion().value(), key.gameDate(), token,
                RecordJdbcMapping.utc(now)) == 1;
    }

    private static RecordDayCloseSnapshot snapshot(ResultSet rs) throws SQLException {
        String category = rs.getString("failure_category");
        return new RecordDayCloseSnapshot(new RecordDayCloseKey(rs.getLong("guild_id"),
                new RecordDefinitionVersion(rs.getString("definition_version")), rs.getObject("game_date", LocalDate.class)),
                RecordWorkState.valueOf(rs.getString("close_state")), Optional.ofNullable(rs.getObject("claim_token", UUID.class)),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "claim_until")),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "started_at")),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "completed_at")), rs.getInt("attempt_count"),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "next_retry_at")), category == null ? Optional.empty()
                        : Optional.of(new RecordWorkFailure(RecordWorkFailureCategory.valueOf(category), rs.getString("safe_error"))),
                RecordJdbcMapping.instant(rs, "created_at"), RecordJdbcMapping.instant(rs, "updated_at"));
    }
}
