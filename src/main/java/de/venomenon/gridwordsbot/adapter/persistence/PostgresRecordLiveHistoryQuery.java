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

        HistoryWindow window = historyWindow(key.guildId(), target, origin);
        List<RecordHistorySnapshot.Result> results = originRequiresExactReconciliation(origin)
                ? correctionResults(key.guildId(), target, window)
                : liveResults(key.guildId(), target, window);
        List<GameParticipationPeriod> periods = periods(window);
        return new RecordHistorySnapshot(results, periods);
    }

    /**
     * A new submission needs only the submitter's two game histories, the
     * solved result history of the submitted game and players active on this
     * game day.  It never invokes the bootstrap-wide history port or loads all
     * participation periods.
     */
    private List<RecordHistorySnapshot.Result> liveResults(
            long guildId, RecordHistorySnapshot.Result target, HistoryWindow window) {
        return jdbc.query("""
                SELECT DISTINCT ON (r.id) r.id,r.version,r.player_id,r.game_type,r.game_date,r.solved,r.attempts_used,
                    r.max_attempts,r.duration_seconds,r.created_at
                FROM game_result r JOIN submission s ON s.game_result_id=r.id
                WHERE s.guild_id=? AND s.processing_state IN ('RESULT_STORED','COMPLETED','FAILED_RETRYABLE','SUPERSEDED')
                  AND (
                      (r.game_date BETWEEN ? AND ?)
                      OR (r.game_type=? AND r.solved=TRUE)
                  )
                ORDER BY r.id, s.updated_at ASC
                """, (rs, row) -> result(rs), guildId, window.start(), window.end(), target.game().name());
    }

    /**
     * Exact corrections retain the complete solved comparison domain of the
     * corrected game for result fallbacks, while streak derivation is bounded
     * to the corrected player's participation interval.  This is deliberately
     * not a generic guild-history read.
     */
    private List<RecordHistorySnapshot.Result> correctionResults(
            long guildId, RecordHistorySnapshot.Result target, HistoryWindow window) {
        return jdbc.query("""
                SELECT DISTINCT ON (r.id) r.id,r.version,r.player_id,r.game_type,r.game_date,r.solved,r.attempts_used,
                    r.max_attempts,r.duration_seconds,r.created_at
                FROM game_result r JOIN submission s ON s.game_result_id=r.id
                WHERE s.guild_id=? AND s.processing_state IN ('RESULT_STORED','COMPLETED','FAILED_RETRYABLE','SUPERSEDED')
                  AND ((r.game_date BETWEEN ? AND ?)
                       OR (r.game_type=? AND r.solved=TRUE))
                ORDER BY r.id, s.updated_at ASC
                """, (rs, row) -> result(rs), guildId, window.start(), window.end(), target.game().name());
    }

    private List<GameParticipationPeriod> periods(HistoryWindow window) {
        return jdbc.query("""
                SELECT player_id,game_type,active_from,inactive_from FROM player_participation_period
                WHERE active_from<=? AND (inactive_from IS NULL OR inactive_from>=?)
                ORDER BY player_id,game_type,active_from
                """, (rs, row) -> period(rs), window.end(), window.start());
    }

    private HistoryWindow historyWindow(
            long guildId, RecordHistorySnapshot.Result target, RecordProcessingOrigin origin) {
        java.time.LocalDate start = jdbc.query("""
                SELECT min(active_from) FROM player_participation_period
                WHERE player_id=? AND active_from<=? AND (inactive_from IS NULL OR inactive_from>=?)
                """, rs -> rs.next() ? rs.getObject(1, java.time.LocalDate.class) : null,
                target.playerId(), target.gameDate(), target.gameDate());
        if (start == null) start = target.gameDate();
        java.time.LocalDate end = target.gameDate();
        if (originRequiresExactReconciliation(origin)) {
            java.time.LocalDate intervalEnd = jdbc.query("""
                    SELECT max(inactive_from) FROM player_participation_period
                    WHERE player_id=? AND active_from<=? AND (inactive_from IS NULL OR inactive_from>=?)
                    """, rs -> rs.next() ? rs.getObject(1, java.time.LocalDate.class) : null,
                    target.playerId(), target.gameDate(), target.gameDate());
            Boolean stillActive = jdbc.query("""
                    SELECT EXISTS (
                        SELECT 1 FROM player_participation_period
                        WHERE player_id=? AND active_from<=? AND inactive_from IS NULL)
                    """, rs -> rs.next() && rs.getBoolean(1), target.playerId(), target.gameDate());
            java.time.LocalDate lastResult = jdbc.query("""
                    SELECT max(r.game_date) FROM game_result r JOIN submission s ON s.game_result_id=r.id
                    WHERE s.guild_id=? AND s.processing_state IN ('RESULT_STORED','COMPLETED','FAILED_RETRYABLE','SUPERSEDED')
                    """, rs -> rs.next() ? rs.getObject(1, java.time.LocalDate.class) : null, guildId);
            if (Boolean.TRUE.equals(stillActive) && lastResult != null) end = lastResult;
            else if (intervalEnd != null && lastResult != null) end = intervalEnd.isBefore(lastResult) ? intervalEnd : lastResult;
            else if (intervalEnd != null) end = intervalEnd;
            else if (lastResult != null) end = lastResult;
        }
        return new HistoryWindow(start, end.isBefore(start) ? start : end);
    }

    private static boolean originRequiresExactReconciliation(RecordProcessingOrigin origin) {
        return origin == RecordProcessingOrigin.NORMAL_CORRECTION
                || origin == RecordProcessingOrigin.IMPORT
                || origin == RecordProcessingOrigin.BACKFILL
                || origin == RecordProcessingOrigin.ADMINISTRATIVE_REPAIR;
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

    private record HistoryWindow(java.time.LocalDate start, java.time.LocalDate end) {
        private HistoryWindow {
            java.util.Objects.requireNonNull(start, "start");
            java.util.Objects.requireNonNull(end, "end");
            if (end.isBefore(start)) throw new IllegalArgumentException("history window is reversed");
        }
    }
}
