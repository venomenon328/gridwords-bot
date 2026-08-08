package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.achievement.AchievementHistorySnapshot;
import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.port.out.AchievementHistoryQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL reader for the complete canonical history of one achievement participant. */
public final class PostgresAchievementHistoryQuery implements AchievementHistoryQuery {
    private final JdbcTemplate jdbc;

    public PostgresAchievementHistoryQuery(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public AchievementHistorySnapshot load(long guildId, long participantId) {
        if (guildId <= 0 || participantId <= 0) {
            throw new IllegalArgumentException("guildId and participantId must be positive");
        }

        List<AchievementHistorySnapshot.Result> results = jdbc.query("""
                SELECT r.id, r.game_type, r.game_date, r.solved, r.attempts_used,
                       s.received_at,
                       r.quadwords_top_left_board, r.quadwords_top_right_board,
                       r.quadwords_bottom_left_board, r.quadwords_bottom_right_board
                  FROM game_result r
                  JOIN LATERAL (
                      SELECT received_at
                        FROM submission
                       WHERE game_result_id = r.id
                         AND guild_id = ?
                         AND processing_state IN (
                             'RESULT_STORED', 'FAILED_RETRYABLE', 'CANONICAL_MESSAGE_PUBLISHED',
                             'ORIGINAL_MESSAGE_DELETED', 'COMPLETED')
                       ORDER BY received_at DESC, source_message_id DESC
                       LIMIT 1
                  ) s ON TRUE
                 WHERE r.player_id = ?
                 ORDER BY r.game_date, r.game_type, s.received_at, r.id
                """, this::result, guildId, participantId);

        List<GameParticipationPeriod> periods = jdbc.query("""
                SELECT player_id, game_type, active_from, inactive_from
                  FROM player_participation_period
                 WHERE player_id = ?
                 ORDER BY game_type, active_from
                """, (rs, row) -> new GameParticipationPeriod(
                rs.getLong("player_id"),
                GameType.valueOf(rs.getString("game_type")),
                rs.getObject("active_from", java.time.LocalDate.class),
                rs.getObject("inactive_from", java.time.LocalDate.class)), participantId);
        return new AchievementHistorySnapshot(participantId, results, periods);
    }

    private AchievementHistorySnapshot.Result result(ResultSet rs, int row) throws SQLException {
        GameType game = GameType.valueOf(rs.getString("game_type"));
        boolean solved = rs.getBoolean("solved");
        OptionalInt attempts = solved
                ? OptionalInt.of(rs.getInt("attempts_used"))
                : OptionalInt.empty();
        return new AchievementHistorySnapshot.Result(
                rs.getLong("id"),
                game,
                rs.getObject("game_date", java.time.LocalDate.class),
                solved,
                attempts,
                rs.getObject("received_at", OffsetDateTime.class).toInstant(),
                quadWordsBoards(rs, game));
    }

    private static Optional<QuadWordsBoards> quadWordsBoards(ResultSet rs, GameType game) throws SQLException {
        if (game != GameType.QUADWORDS) {
            return Optional.empty();
        }
        String topLeft = rs.getString("quadwords_top_left_board");
        if (topLeft == null) {
            return Optional.empty();
        }
        return Optional.of(new QuadWordsBoards(
                QuadWordsBoard.fromCanonicalText(topLeft),
                QuadWordsBoard.fromCanonicalText(required(rs, "quadwords_top_right_board")),
                QuadWordsBoard.fromCanonicalText(required(rs, "quadwords_bottom_left_board")),
                QuadWordsBoard.fromCanonicalText(required(rs, "quadwords_bottom_right_board"))));
    }

    private static String required(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null) {
            throw new SQLException("incomplete QuadWords board details in canonical game result");
        }
        return value;
    }
}
