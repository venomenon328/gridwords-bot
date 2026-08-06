package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.record.RecordEventAppendResult;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordEventValidity;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordValue;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordEventIdempotencyConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Append-only PostgreSQL audit store.  Invalidated facts remain readable forever. */
public final class PostgresRecordEventStore implements RecordEventStore {
    private final JdbcTemplate jdbc; private final Clock clock;
    public PostgresRecordEventStore(JdbcTemplate jdbc, Clock clock) { this.jdbc=java.util.Objects.requireNonNull(jdbc,"jdbc"); this.clock=java.util.Objects.requireNonNull(clock,"clock"); }
    @Override public RecordEventAppendResult append(RecordEventDraft draft) {
        java.util.Objects.requireNonNull(draft,"draft"); Instant now=clock.instant();
        RecordJdbcMapping.ValueColumns previous=draft.previousValue().map(RecordJdbcMapping::valueColumns).orElse(new RecordJdbcMapping.ValueColumns(null,null,null,null,null,null));
        RecordJdbcMapping.ValueColumns next=RecordJdbcMapping.valueColumns(draft.newValue());
        int inserted=jdbc.update("""
                INSERT INTO record_event (event_id,idempotency_key,guild_id,definition_key,definition_version,scope_type,scope_key,event_type,previous_holder_player_id,new_holder_player_id,
                    previous_value_kind,previous_attempts,previous_duration_millis,previous_streak_length,previous_streak_start_date,previous_streak_end_date,
                    new_value_kind,new_attempts,new_duration_millis,new_streak_length,new_streak_start_date,new_streak_end_date,
                    previous_source_type,previous_source_key,new_source_type,new_source_key,trigger_key,processing_origin,detected_at,validity,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'VALID',?,?) ON CONFLICT (idempotency_key) DO NOTHING
                """, draft.eventId(),draft.idempotencyKey(),draft.stateKey().guildId(),draft.stateKey().definitionKey().value(),draft.stateKey().definitionVersion().value(),draft.stateKey().scope().type().name(),draft.stateKey().scopeKey(),draft.type().name(),draft.previousHolderPlayerId().orElse(null),draft.newHolderPlayerId().orElse(null),
                previous.kind(),previous.attempts(),previous.durationMillis(),previous.streakLength(),previous.streakStart(),previous.streakEnd(),next.kind(),next.attempts(),next.durationMillis(),next.streakLength(),next.streakStart(),next.streakEnd(),
                draft.previousSource().map(source -> source.sourceType().name()).orElse(null),draft.previousSource().map(RecordJdbcMapping::sourceKey).orElse(null),draft.newSource().sourceType().name(),RecordJdbcMapping.sourceKey(draft.newSource()),draft.triggerKey(),draft.processingOrigin().name(),RecordJdbcMapping.utc(draft.detectedAt()),RecordJdbcMapping.utc(now),RecordJdbcMapping.utc(now));
        RecordEventSnapshot persisted=findByIdempotency(draft.idempotencyKey()).orElseThrow(() -> new IllegalStateException("appended record event is missing"));
        if (inserted==0 && !persisted.draft().equals(draft)) throw new RecordEventIdempotencyConflictException(draft.idempotencyKey());
        return new RecordEventAppendResult(inserted==1,persisted);
    }
    @Override public Optional<RecordEventSnapshot> find(UUID eventId) { return jdbc.query(select()+" WHERE event_id=?",(rs,row)->snapshot(rs),eventId).stream().findFirst(); }
    @Override public List<RecordEventSnapshot> findByTriggerKey(long guildId,String triggerKey) {
        if(guildId<=0) throw new IllegalArgumentException("guildId must be positive"); if(triggerKey==null||triggerKey.isBlank()) throw new IllegalArgumentException("triggerKey is invalid");
        return jdbc.query(select()+" WHERE guild_id=? AND trigger_key=? ORDER BY created_at,event_id",(rs,row)->snapshot(rs),guildId,triggerKey);
    }
    @Override public List<RecordEventSnapshot> findBySource(long guildId, de.venomenon.gridwordsbot.domain.record.RecordSourceReference source) {
        if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
        java.util.Objects.requireNonNull(source, "source");
        return jdbc.query(select()+" WHERE guild_id=? AND new_source_type=? AND new_source_key=? ORDER BY created_at,event_id",
                (rs,row)->snapshot(rs), guildId, source.sourceType().name(), RecordJdbcMapping.sourceKey(source));
    }
    @Override public List<RecordEventSnapshot> findByResultId(long guildId, long resultId) {
        if (guildId <= 0 || resultId <= 0) throw new IllegalArgumentException("guildId and resultId must be positive");
        return jdbc.query(select()+" WHERE guild_id=? AND new_source_type='GAME_RESULT' AND split_part(new_source_key,':',1)=? ORDER BY created_at,event_id",
                (rs,row)->snapshot(rs), guildId, Long.toString(resultId));
    }

    @Override
    public List<RecordEventSnapshot> findResultFamily(
            long guildId, List<RecordStateKey> families, LocalDate affectedFrom) {
        return findFamily(guildId, families, affectedFrom, "GAME_RESULT",
                "split_part(new_source_key, ':', 5)::date");
    }

    @Override
    public List<RecordEventSnapshot> findStreakFamily(
            long guildId, List<RecordStateKey> families, LocalDate affectedFrom) {
        return findFamily(guildId, families, affectedFrom, "STREAK_RUN", "new_streak_end_date");
    }

    private List<RecordEventSnapshot> findFamily(
            long guildId,
            List<RecordStateKey> families,
            LocalDate affectedFrom,
            String sourceType,
            String sourceDateExpression) {
        if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
        java.util.Objects.requireNonNull(families, "families");
        java.util.Objects.requireNonNull(affectedFrom, "affectedFrom");
        List<RecordStateKey> keys = families.stream().distinct().toList();
        if (keys.isEmpty()) return List.of();
        if (keys.stream().anyMatch(key -> key.guildId() != guildId)) {
            throw new IllegalArgumentException("event family belongs to another guild");
        }
        String predicates = keys.stream()
                .map(ignored -> "(definition_key=? AND definition_version=? AND scope_type=? AND scope_key=?)")
                .collect(java.util.stream.Collectors.joining(" OR "));
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(guildId);
        parameters.add(sourceType);
        parameters.add(affectedFrom);
        for (RecordStateKey key : keys) {
            parameters.add(key.definitionKey().value());
            parameters.add(key.definitionVersion().value());
            parameters.add(key.scope().type().name());
            parameters.add(key.scopeKey());
        }
        String sql = select() + " WHERE guild_id=? AND new_source_type=? AND event_type<>'RECORD_INITIALIZED'"
                + " AND " + sourceDateExpression + ">=? AND (" + predicates + ") ORDER BY created_at,event_id";
        return jdbc.query(sql, (rs, row) -> snapshot(rs), parameters.toArray());
    }
    @Override public boolean invalidate(UUID eventId,Instant invalidatedAt) {
        java.util.Objects.requireNonNull(eventId, "eventId"); java.util.Objects.requireNonNull(invalidatedAt, "invalidatedAt");
        return jdbc.update("UPDATE record_event SET validity='INVALIDATED',invalidated_at=?,updated_at=? WHERE event_id=? AND validity='VALID'",
                RecordJdbcMapping.utc(invalidatedAt), RecordJdbcMapping.utc(invalidatedAt), eventId) == 1;
    }
    @Override public boolean supersede(UUID eventId,UUID successor,Instant invalidatedAt) {
        java.util.Objects.requireNonNull(eventId, "eventId"); java.util.Objects.requireNonNull(successor, "successor"); java.util.Objects.requireNonNull(invalidatedAt, "invalidatedAt");
        if (eventId.equals(successor)) throw new IllegalArgumentException("an event cannot supersede itself");
        return jdbc.update("UPDATE record_event SET validity='SUPERSEDED',invalidated_at=?,superseded_by=?,updated_at=? WHERE event_id=? AND validity='VALID' AND EXISTS (SELECT 1 FROM record_event successor WHERE successor.event_id=? AND successor.event_id<>record_event.event_id)",
                RecordJdbcMapping.utc(invalidatedAt), successor, RecordJdbcMapping.utc(invalidatedAt), eventId, successor) == 1;
    }
    private Optional<RecordEventSnapshot> findByIdempotency(String key) { return jdbc.query(select()+" WHERE idempotency_key=?",(rs,row)->snapshot(rs),key).stream().findFirst(); }
    private static String select() { return "SELECT * FROM record_event"; }
    private RecordEventSnapshot snapshot(ResultSet rs) throws SQLException {
        RecordStateKey key=new RecordStateKey(rs.getLong("guild_id"),new de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey(rs.getString("definition_key")),new de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion(rs.getString("definition_version")),RecordJdbcMapping.scope(rs));
        Optional<RecordValue> previous=RecordJdbcMapping.optionalValue(rs,"previous_");
        Optional<de.venomenon.gridwordsbot.domain.record.RecordSourceReference> previousSource=Optional.ofNullable(rs.getString("previous_source_type")).map(type->RecordJdbcMapping.source(type,unchecked(rs,"previous_source_key")));
        RecordEventDraft draft=new RecordEventDraft(rs.getObject("event_id",UUID.class),rs.getString("idempotency_key"),key,RecordEventType.valueOf(rs.getString("event_type")),previous,RecordJdbcMapping.value(rs,"new_"),Optional.ofNullable(rs.getObject("previous_holder_player_id",Long.class)),Optional.ofNullable(rs.getObject("new_holder_player_id",Long.class)),previousSource,RecordJdbcMapping.source(rs.getString("new_source_type"),rs.getString("new_source_key")),rs.getString("trigger_key"),de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin.valueOf(rs.getString("processing_origin")),RecordJdbcMapping.instant(rs,"detected_at"));
        return new RecordEventSnapshot(draft,RecordEventValidity.valueOf(rs.getString("validity")),Optional.ofNullable(RecordJdbcMapping.instant(rs,"invalidated_at")),Optional.ofNullable(rs.getObject("superseded_by",UUID.class)),RecordJdbcMapping.instant(rs,"created_at"),RecordJdbcMapping.instant(rs,"updated_at"));
    }
    private static String unchecked(ResultSet rs,String column) { try { return rs.getString(column); } catch(SQLException exception) { throw new IllegalStateException("record event mapping failed",exception); } }
}
