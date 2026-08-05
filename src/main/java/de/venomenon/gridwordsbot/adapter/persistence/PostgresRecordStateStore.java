package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.record.RecordStateInitialization;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdate;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL implementation of the materialized record-state concurrency anchor. */
public final class PostgresRecordStateStore implements RecordStateStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public PostgresRecordStateStore(JdbcTemplate jdbc, Clock clock) { this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc"); this.clock = java.util.Objects.requireNonNull(clock, "clock"); }

    @Override public Optional<RecordStateSnapshot> find(RecordStateKey key) {
        java.util.Objects.requireNonNull(key, "key");
        return jdbc.query(select() + " WHERE guild_id=? AND definition_key=? AND definition_version=? AND scope_type=? AND scope_key=?", (rs, row) -> RecordJdbcMapping.state(rs),
                key.guildId(), key.definitionKey().value(), key.definitionVersion().value(), key.scope().type().name(), key.scopeKey()).stream().findFirst();
    }
    @Override public RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write) {
        java.util.Objects.requireNonNull(key, "key"); java.util.Objects.requireNonNull(write, "write");
        validateScopeHolder(key, write); Instant now = clock.instant(); Object[] value = RecordJdbcMapping.stateWriteParameters(write);
        int inserted = jdbc.update("""
                INSERT INTO record_state (guild_id,definition_key,definition_version,scope_type,scope_key,holder_player_id,value_kind,attempts,duration_millis,streak_length,streak_start_date,streak_end_date,
                    source_type,source_game_result_id,source_game_result_version,source_game_player_id,source_game_type,source_game_date,source_streak_metric,source_streak_owner_type,source_streak_owner_player_id,source_streak_start_date,running,lock_version,created_at,updated_at)
                VALUES (
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?,
                    0, ?, ?)
                ON CONFLICT (guild_id,definition_key,definition_version,scope_type,scope_key) DO NOTHING
                """, concat(new Object[] {key.guildId(), key.definitionKey().value(), key.definitionVersion().value(), key.scope().type().name(), key.scopeKey()}, value, new Object[] {RecordJdbcMapping.utc(now), RecordJdbcMapping.utc(now)}));
        RecordStateSnapshot snapshot = find(key).orElseThrow(() -> new IllegalStateException("initialized record state is missing"));
        return inserted == 1 ? new RecordStateInitialization.Created(snapshot) : new RecordStateInitialization.Existing(snapshot);
    }
    @Override public RecordStateUpdateResult update(RecordStateUpdate update) {
        java.util.Objects.requireNonNull(update, "update"); validateScopeHolder(update.key(), update.write());
        Object[] value = RecordJdbcMapping.stateWriteParameters(update.write());
        Optional<RecordStateSnapshot> changed = jdbc.query("""
                UPDATE record_state SET holder_player_id=?,value_kind=?,attempts=?,duration_millis=?,streak_length=?,streak_start_date=?,streak_end_date=?,
                    source_type=?,source_game_result_id=?,source_game_result_version=?,source_game_player_id=?,source_game_type=?,source_game_date=?,source_streak_metric=?,source_streak_owner_type=?,source_streak_owner_player_id=?,source_streak_start_date=?,running=?,lock_version=lock_version+1,updated_at=?
                WHERE guild_id=? AND definition_key=? AND definition_version=? AND scope_type=? AND scope_key=? AND lock_version=?
                  AND NOT (holder_player_id IS NOT DISTINCT FROM ? AND value_kind IS NOT DISTINCT FROM ? AND attempts IS NOT DISTINCT FROM ?
                      AND duration_millis IS NOT DISTINCT FROM ? AND streak_length IS NOT DISTINCT FROM ? AND streak_start_date IS NOT DISTINCT FROM ?
                      AND streak_end_date IS NOT DISTINCT FROM ? AND source_type IS NOT DISTINCT FROM ? AND source_game_result_id IS NOT DISTINCT FROM ?
                      AND source_game_result_version IS NOT DISTINCT FROM ? AND source_game_player_id IS NOT DISTINCT FROM ? AND source_game_type IS NOT DISTINCT FROM ?
                      AND source_game_date IS NOT DISTINCT FROM ? AND source_streak_metric IS NOT DISTINCT FROM ? AND source_streak_owner_type IS NOT DISTINCT FROM ?
                      AND source_streak_owner_player_id IS NOT DISTINCT FROM ? AND source_streak_start_date IS NOT DISTINCT FROM ? AND running IS NOT DISTINCT FROM ?)
                RETURNING guild_id,definition_key,definition_version,scope_type,scope_key,holder_player_id,value_kind,attempts,duration_millis,streak_length,streak_start_date,streak_end_date,
                    source_type,source_game_result_id,source_game_result_version,source_game_player_id,source_game_type,source_game_date,source_streak_metric,source_streak_owner_type,source_streak_owner_player_id,source_streak_start_date,running,lock_version,created_at,updated_at
                """, (rs, row) -> RecordJdbcMapping.state(rs), concat(value, new Object[] {RecordJdbcMapping.utc(clock.instant()), update.key().guildId(), update.key().definitionKey().value(), update.key().definitionVersion().value(), update.key().scope().type().name(), update.key().scopeKey(), update.expectedLockVersion().value()}, value)).stream().findFirst();
        if (changed.isPresent()) return new RecordStateUpdateResult(RecordStateUpdateResult.Status.UPDATED, changed);
        Optional<RecordStateSnapshot> unchanged = jdbc.query("""
                UPDATE record_state SET lock_version=lock_version
                WHERE guild_id=? AND definition_key=? AND definition_version=? AND scope_type=? AND scope_key=? AND lock_version=?
                  AND holder_player_id IS NOT DISTINCT FROM ? AND value_kind IS NOT DISTINCT FROM ? AND attempts IS NOT DISTINCT FROM ?
                  AND duration_millis IS NOT DISTINCT FROM ? AND streak_length IS NOT DISTINCT FROM ? AND streak_start_date IS NOT DISTINCT FROM ?
                  AND streak_end_date IS NOT DISTINCT FROM ? AND source_type IS NOT DISTINCT FROM ? AND source_game_result_id IS NOT DISTINCT FROM ?
                  AND source_game_result_version IS NOT DISTINCT FROM ? AND source_game_player_id IS NOT DISTINCT FROM ? AND source_game_type IS NOT DISTINCT FROM ?
                  AND source_game_date IS NOT DISTINCT FROM ? AND source_streak_metric IS NOT DISTINCT FROM ? AND source_streak_owner_type IS NOT DISTINCT FROM ?
                  AND source_streak_owner_player_id IS NOT DISTINCT FROM ? AND source_streak_start_date IS NOT DISTINCT FROM ? AND running IS NOT DISTINCT FROM ?
                RETURNING guild_id,definition_key,definition_version,scope_type,scope_key,holder_player_id,value_kind,attempts,duration_millis,streak_length,streak_start_date,streak_end_date,
                    source_type,source_game_result_id,source_game_result_version,source_game_player_id,source_game_type,source_game_date,source_streak_metric,source_streak_owner_type,source_streak_owner_player_id,source_streak_start_date,running,lock_version,created_at,updated_at
                """, (rs, row) -> RecordJdbcMapping.state(rs), concat(new Object[] {update.key().guildId(), update.key().definitionKey().value(), update.key().definitionVersion().value(), update.key().scope().type().name(), update.key().scopeKey(), update.expectedLockVersion().value()}, value)).stream().findFirst();
        return unchanged.map(snapshot -> new RecordStateUpdateResult(RecordStateUpdateResult.Status.UNCHANGED, Optional.of(snapshot)))
                .orElseGet(() -> new RecordStateUpdateResult(RecordStateUpdateResult.Status.VERSION_CONFLICT, Optional.empty()));
    }
    private static String select() { return "SELECT guild_id,definition_key,definition_version,scope_type,scope_key,holder_player_id,value_kind,attempts,duration_millis,streak_length,streak_start_date,streak_end_date,source_type,source_game_result_id,source_game_result_version,source_game_player_id,source_game_type,source_game_date,source_streak_metric,source_streak_owner_type,source_streak_owner_player_id,source_streak_start_date,running,lock_version,created_at,updated_at FROM record_state"; }
    private static void validateScopeHolder(RecordStateKey key, RecordStateWrite write) {
        switch (key.scope()) {
            case de.venomenon.gridwordsbot.domain.record.RecordScope.Personal personal -> { if (write.holderPlayerId().isEmpty() || write.holderPlayerId().get() != personal.playerId()) throw new IllegalArgumentException("personal state holder must be scoped player"); }
            case de.venomenon.gridwordsbot.domain.record.RecordScope.ServerIndividual ignored -> { if (write.holderPlayerId().isEmpty()) throw new IllegalArgumentException("server state needs holder"); }
            case de.venomenon.gridwordsbot.domain.record.RecordScope.Shared ignored -> { if (write.holderPlayerId().isPresent()) throw new IllegalArgumentException("shared state has no holder"); }
        }
    }
    private static Object[] concat(Object[]... parts) { return java.util.Arrays.stream(parts).flatMap(java.util.Arrays::stream).toArray(); }
}
