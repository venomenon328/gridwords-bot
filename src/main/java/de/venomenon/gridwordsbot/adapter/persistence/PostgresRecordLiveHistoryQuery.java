package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
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
        return loadFor(key, RecordProcessingOrigin.NORMAL_CORRECTION);
    }

    @Override
    public RecordHistorySnapshot loadFor(RecordLiveEvaluationKey key, RecordProcessingOrigin origin) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(origin, "origin");
        RecordHistorySnapshot.Result target = jdbc.query("""
                SELECT r.id,r.version,r.player_id,r.game_type,r.game_date,r.solved,r.attempts_used,
                    r.max_attempts,r.duration_seconds,r.created_at
                FROM game_result r JOIN submission s ON s.game_result_id=r.id
                WHERE r.id=? AND r.version=? AND s.guild_id=?
                  AND s.processing_state IN ('RESULT_STORED','COMPLETED','FAILED_RETRYABLE','SUPERSEDED')
                ORDER BY s.updated_at ASC LIMIT 1
                """, (rs, row) -> result(rs), key.gameResultId(), key.gameResultVersion(), key.guildId())
                .stream().findFirst().orElse(null);
        if (target == null) {
            throw new IllegalStateException("claimed canonical result version is no longer available");
        }

        List<RecordHistorySnapshot.Result> results = origin == RecordProcessingOrigin.NORMAL_CORRECTION
                ? correctionResults(key.guildId())
                : liveResults(key.guildId(), target);
        List<GameParticipationPeriod> periods = origin == RecordProcessingOrigin.NORMAL_CORRECTION
                ? correctionPeriods(results)
                : livePeriods(target.gameDate());
        return new RecordHistorySnapshot(results, periods);
    }

    /**
     * A new submission needs only the submitter's two game histories, the
     * solved result history of the submitted game and players active on this
     * game day.  It never invokes the bootstrap-wide history port or loads all
     * participation periods.
     */
    private List<RecordHistorySnapshot.Result> liveResults(long guildId, RecordHistorySnapshot.Result target) {
        return jdbc.query("""
                SELECT DISTINCT ON (r.id) r.id,r.version,r.player_id,r.game_type,r.game_date,r.solved,r.attempts_used,
                    r.max_attempts,r.duration_seconds,r.created_at
                FROM game_result r JOIN submission s ON s.game_result_id=r.id
                WHERE s.guild_id=? AND s.processing_state IN ('RESULT_STORED','COMPLETED','FAILED_RETRYABLE','SUPERSEDED')
                  AND (r.player_id=? OR (r.game_type=? AND r.solved=TRUE))
                ORDER BY r.id, s.updated_at ASC
                """, (rs, row) -> result(rs), guildId, target.playerId(), target.game().name());
    }

    /** A correction has a wider, but still record-domain-scoped, canonical target set. */
    private List<RecordHistorySnapshot.Result> correctionResults(long guildId) {
        return jdbc.query("""
                SELECT DISTINCT ON (r.id) r.id,r.version,r.player_id,r.game_type,r.game_date,r.solved,r.attempts_used,
                    r.max_attempts,r.duration_seconds,r.created_at
                FROM game_result r JOIN submission s ON s.game_result_id=r.id
                WHERE s.guild_id=? AND s.processing_state IN ('RESULT_STORED','COMPLETED','FAILED_RETRYABLE','SUPERSEDED')
                ORDER BY r.id, s.updated_at ASC
                """, (rs, row) -> result(rs), guildId);
    }

    private List<GameParticipationPeriod> livePeriods(java.time.LocalDate gameDate) {
        return jdbc.query("""
                SELECT player_id,game_type,active_from,inactive_from FROM player_participation_period
                WHERE active_from<=? AND (inactive_from IS NULL OR inactive_from>=?)
                ORDER BY player_id,game_type,active_from
                """, (rs, row) -> period(rs), gameDate, gameDate);
    }

    private List<GameParticipationPeriod> correctionPeriods(List<RecordHistorySnapshot.Result> results) {
        if (results.isEmpty()) return List.of();
        java.time.LocalDate first = results.stream().map(RecordHistorySnapshot.Result::gameDate).min(java.util.Comparator.naturalOrder()).orElseThrow();
        java.time.LocalDate last = results.stream().map(RecordHistorySnapshot.Result::gameDate).max(java.util.Comparator.naturalOrder()).orElseThrow();
        return jdbc.query("""
                SELECT player_id,game_type,active_from,inactive_from FROM player_participation_period
                WHERE active_from<=? AND (inactive_from IS NULL OR inactive_from>=?)
                ORDER BY player_id,game_type,active_from
                """, (rs, row) -> period(rs), last, first);
    }

    private static RecordHistorySnapshot.Result result(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RecordHistorySnapshot.Result(rs.getLong("id"), rs.getLong("version"),
                rs.getLong("player_id"), GameType.valueOf(rs.getString("game_type")),
                rs.getObject("game_date", java.time.LocalDate.class),
                rs.getBoolean("solved") ? new ShareOutcome.Solved(rs.getInt("attempts_used"), rs.getInt("max_attempts"))
                        : new ShareOutcome.Unsolved(rs.getInt("max_attempts")),
                Duration.ofSeconds(rs.getLong("duration_seconds")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static GameParticipationPeriod period(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new GameParticipationPeriod(rs.getLong("player_id"), GameType.valueOf(rs.getString("game_type")),
                rs.getObject("active_from", java.time.LocalDate.class),
                rs.getObject("inactive_from", java.time.LocalDate.class));
    }
}
