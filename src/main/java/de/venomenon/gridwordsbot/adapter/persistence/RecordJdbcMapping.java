package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.DurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordScopeType;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordSourceType;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.domain.record.RecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordValueKind;
import de.venomenon.gridwordsbot.domain.record.StreakRecordMetric;
import de.venomenon.gridwordsbot.domain.record.StreakRecordValue;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** JDBC-only mapping helpers; rows never escape the persistence adapter. */
final class RecordJdbcMapping {
    private RecordJdbcMapping() { }

    static OffsetDateTime utc(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
    static RecordScope scope(ResultSet rs) throws SQLException {
        RecordScopeType type = RecordScopeType.valueOf(rs.getString("scope_type"));
        return switch (type) {
            case PERSONAL -> new RecordScope.Personal(parsePersonalScope(rs.getString("scope_key")));
            case SERVER_INDIVIDUAL -> new RecordScope.ServerIndividual();
            case SHARED -> new RecordScope.Shared();
        };
    }
    static RecordValue value(ResultSet rs, String prefix) throws SQLException {
        RecordValueKind kind = RecordValueKind.valueOf(rs.getString(prefix + "value_kind"));
        return switch (kind) {
            case ATTEMPTS_AND_DURATION -> new AttemptsDurationRecordValue(rs.getInt(prefix + "attempts"), Duration.ofMillis(rs.getLong(prefix + "duration_millis")));
            case DURATION -> new DurationRecordValue(Duration.ofMillis(rs.getLong(prefix + "duration_millis")));
            case STREAK -> new StreakRecordValue(rs.getInt(prefix + "streak_length"), rs.getObject(prefix + "streak_start_date", LocalDate.class), rs.getObject(prefix + "streak_end_date", LocalDate.class));
        };
    }
    static Optional<RecordValue> optionalValue(ResultSet rs, String prefix) throws SQLException {
        return rs.getString(prefix + "value_kind") == null ? Optional.empty() : Optional.of(value(rs, prefix));
    }
    static RecordSourceReference source(ResultSet rs, String prefix) throws SQLException {
        RecordSourceType type = RecordSourceType.valueOf(rs.getString(prefix + "source_type"));
        return switch (type) {
            case GAME_RESULT -> new RecordSourceReference.GameResult(
                    rs.getLong(prefix + "source_game_result_id"), rs.getLong(prefix + "source_game_result_version"),
                    rs.getLong(prefix + "source_game_player_id"), GameType.valueOf(rs.getString(prefix + "source_game_type")),
                    rs.getObject(prefix + "source_game_date", LocalDate.class));
            case STREAK_RUN -> new RecordSourceReference.StreakRun(
                    StreakRecordMetric.valueOf(rs.getString(prefix + "source_streak_metric")),
                    streakOwner(rs.getString(prefix + "source_streak_owner_type"), rs.getObject(prefix + "source_streak_owner_player_id", Long.class)),
                    rs.getObject(prefix + "source_streak_start_date", LocalDate.class));
        };
    }
    static RecordStateSnapshot state(ResultSet rs) throws SQLException {
        RecordStateKey key = new RecordStateKey(rs.getLong("guild_id"),
                new de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey(rs.getString("definition_key")),
                new de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion(rs.getString("definition_version")), scope(rs));
        return new RecordStateSnapshot(key, Optional.ofNullable(rs.getObject("holder_player_id", Long.class)), value(rs, ""), source(rs, ""),
                rs.getBoolean("running"), new de.venomenon.gridwordsbot.domain.record.RecordLockVersion(rs.getLong("lock_version")),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }
    static Object[] stateWriteParameters(RecordStateWrite write) {
        ValueColumns values = ValueColumns.of(write.value());
        SourceColumns source = SourceColumns.of(write.source());
        return new Object[] {write.holderPlayerId().orElse(null), values.kind(), values.attempts(), values.durationMillis(), values.streakLength(), values.streakStart(), values.streakEnd(),
                source.type(), source.gameResultId(), source.gameResultVersion(), source.gamePlayerId(), source.gameType(), source.gameDate(),
                source.streakMetric(), source.streakOwnerType(), source.streakOwnerPlayerId(), source.streakStartDate(), write.running()};
    }
    static ValueColumns valueColumns(RecordValue value) { return ValueColumns.of(value); }
    static SourceColumns sourceColumns(RecordSourceReference source) { return SourceColumns.of(source); }
    static String sourceKey(RecordSourceReference source) {
        return switch (source) {
            case RecordSourceReference.GameResult result -> result.resultId() + ":" + result.resultVersion() + ":" + result.playerId() + ":" + result.game().name() + ":" + result.gameDate();
            case RecordSourceReference.StreakRun streak -> switch (streak.owner()) {
                case RecordSourceReference.StreakRunOwner.Player player -> streak.metric().name() + ":PLAYER:" + player.playerId() + ":" + streak.startDate();
                case RecordSourceReference.StreakRunOwner.Shared ignored -> streak.metric().name() + ":SHARED:" + streak.startDate();
            };
        };
    }
    static RecordSourceReference source(String type, String key) {
        String[] parts = key.split(":", -1);
        return switch (RecordSourceType.valueOf(type)) {
            case GAME_RESULT -> new RecordSourceReference.GameResult(Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]), GameType.valueOf(parts[3]), LocalDate.parse(parts[4]));
            case STREAK_RUN -> new RecordSourceReference.StreakRun(StreakRecordMetric.valueOf(parts[0]),
                    "PLAYER".equals(parts[1]) ? new RecordSourceReference.StreakRunOwner.Player(Long.parseLong(parts[2])) : new RecordSourceReference.StreakRunOwner.Shared(),
                    LocalDate.parse(parts[parts.length - 1]));
        };
    }
    private static long parsePersonalScope(String key) { return Long.parseLong(key.substring("player:".length())); }
    private static RecordSourceReference.StreakRunOwner streakOwner(String type, Long playerId) {
        return "PLAYER".equals(type) ? new RecordSourceReference.StreakRunOwner.Player(playerId) : new RecordSourceReference.StreakRunOwner.Shared();
    }
    record ValueColumns(String kind, Integer attempts, Long durationMillis, Integer streakLength, LocalDate streakStart, LocalDate streakEnd) {
        static ValueColumns of(RecordValue value) {
            return switch (value) {
                case AttemptsDurationRecordValue attempts -> new ValueColumns(attempts.kind().name(), attempts.attempts(), attempts.duration().toMillis(), null, null, null);
                case DurationRecordValue duration -> new ValueColumns(duration.kind().name(), null, duration.duration().toMillis(), null, null, null);
                case StreakRecordValue streak -> new ValueColumns(streak.kind().name(), null, null, streak.length(), streak.startDate(), streak.endDate());
            };
        }
    }
    record SourceColumns(String type, Long gameResultId, Long gameResultVersion, Long gamePlayerId, String gameType, LocalDate gameDate,
                         String streakMetric, String streakOwnerType, Long streakOwnerPlayerId, LocalDate streakStartDate) {
        static SourceColumns of(RecordSourceReference source) {
            return switch (source) {
                case RecordSourceReference.GameResult game -> new SourceColumns(game.sourceType().name(), game.resultId(), game.resultVersion(), game.playerId(), game.game().name(), game.gameDate(), null, null, null, null);
                case RecordSourceReference.StreakRun streak -> {
                    Long owner = streak.owner() instanceof RecordSourceReference.StreakRunOwner.Player player ? player.playerId() : null;
                    String ownerType = owner == null ? "SHARED" : "PLAYER";
                    yield new SourceColumns(streak.sourceType().name(), null, null, null, null, null, streak.metric().name(), ownerType, owner, streak.startDate());
                }
            };
        }
    }
}
