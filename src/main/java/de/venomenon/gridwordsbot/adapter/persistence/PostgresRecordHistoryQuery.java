package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.port.out.RecordHistoryQuery;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL read adapter for the intentionally one-off complete bootstrap scan. */
public final class PostgresRecordHistoryQuery implements RecordHistoryQuery {
    private final JdbcTemplate jdbc;
    public PostgresRecordHistoryQuery(JdbcTemplate jdbc) { this.jdbc = java.util.Objects.requireNonNull(jdbc); }
    @Override public RecordHistorySnapshot load(long guildId) {
        if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
        List<RecordHistorySnapshot.Result> results = jdbc.query("""
                SELECT DISTINCT ON (r.id) r.id,r.version,r.player_id,r.game_type,r.game_date,r.solved,r.attempts_used,
                    r.max_attempts,r.duration_seconds,r.created_at
                FROM game_result r JOIN submission s ON s.game_result_id=r.id
                WHERE s.guild_id=? AND s.processing_state IN ('RESULT_STORED','COMPLETED','FAILED_RETRYABLE','SUPERSEDED')
                ORDER BY r.id, s.updated_at ASC
                """, (rs, row) -> new RecordHistorySnapshot.Result(rs.getLong("id"), rs.getLong("version"),
                rs.getLong("player_id"), GameType.valueOf(rs.getString("game_type")), rs.getObject("game_date", java.time.LocalDate.class),
                rs.getBoolean("solved") ? new ShareOutcome.Solved(rs.getInt("attempts_used"), rs.getInt("max_attempts"))
                        : new ShareOutcome.Unsolved(rs.getInt("max_attempts")),
                Duration.ofSeconds(rs.getLong("duration_seconds")), rs.getObject("created_at", OffsetDateTime.class).toInstant()), guildId);
        List<GameParticipationPeriod> periods = jdbc.query("""
                SELECT player_id,game_type,active_from,inactive_from FROM player_participation_period
                ORDER BY player_id,game_type,active_from
                """, (rs, row) -> new GameParticipationPeriod(rs.getLong("player_id"),
                GameType.valueOf(rs.getString("game_type")), rs.getObject("active_from", java.time.LocalDate.class),
                rs.getObject("inactive_from", java.time.LocalDate.class)));
        return new RecordHistorySnapshot(results, periods);
    }
}
