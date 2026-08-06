package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;
import de.venomenon.gridwordsbot.port.out.RecordLiveHistoryQuery;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Canonical live read rooted at one result version.  Unlike bootstrap reads it
 * first verifies the exact version; an obsolete claim therefore never turns
 * into a broad evaluation.
 */
public final class PostgresRecordLiveHistoryQuery implements RecordLiveHistoryQuery {
    private final JdbcTemplate jdbc;

    public PostgresRecordLiveHistoryQuery(JdbcTemplate jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public RecordHistorySnapshot loadFor(RecordLiveEvaluationKey key) {
        java.util.Objects.requireNonNull(key, "key");
        Long exists = jdbc.query("""
                SELECT r.id FROM game_result r JOIN submission s ON s.game_result_id=r.id
                WHERE r.id=? AND r.version=? AND s.guild_id=?
                  AND s.processing_state IN ('RESULT_STORED','COMPLETED','FAILED_RETRYABLE','SUPERSEDED')
                ORDER BY s.updated_at ASC LIMIT 1
                """, (rs, row) -> rs.getLong(1), key.gameResultId(), key.gameResultVersion(), key.guildId())
                .stream().findFirst().orElse(null);
        if (exists == null) {
            throw new IllegalStateException("claimed canonical result version is no longer available");
        }

        // This deliberately reads canonical record inputs only.  It does not
        // reuse RecordHistoryQuery, whose contract is a bootstrap-wide scan.
        List<RecordHistorySnapshot.Result> results = jdbc.query("""
                SELECT DISTINCT ON (r.id) r.id,r.version,r.player_id,r.game_type,r.game_date,r.solved,r.attempts_used,
                    r.max_attempts,r.duration_seconds,r.created_at
                FROM game_result r JOIN submission s ON s.game_result_id=r.id
                WHERE s.guild_id=? AND s.processing_state IN ('RESULT_STORED','COMPLETED','FAILED_RETRYABLE','SUPERSEDED')
                ORDER BY r.id, s.updated_at ASC
                """, (rs, row) -> new RecordHistorySnapshot.Result(rs.getLong("id"), rs.getLong("version"),
                rs.getLong("player_id"), GameType.valueOf(rs.getString("game_type")),
                rs.getObject("game_date", java.time.LocalDate.class),
                rs.getBoolean("solved") ? new ShareOutcome.Solved(rs.getInt("attempts_used"), rs.getInt("max_attempts"))
                        : new ShareOutcome.Unsolved(rs.getInt("max_attempts")),
                Duration.ofSeconds(rs.getLong("duration_seconds")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()), key.guildId());
        List<GameParticipationPeriod> periods = jdbc.query("""
                SELECT player_id,game_type,active_from,inactive_from FROM player_participation_period
                ORDER BY player_id,game_type,active_from
                """, (rs, row) -> new GameParticipationPeriod(rs.getLong("player_id"),
                GameType.valueOf(rs.getString("game_type")), rs.getObject("active_from", java.time.LocalDate.class),
                rs.getObject("inactive_from", java.time.LocalDate.class)));
        return new RecordHistorySnapshot(results, periods);
    }
}
