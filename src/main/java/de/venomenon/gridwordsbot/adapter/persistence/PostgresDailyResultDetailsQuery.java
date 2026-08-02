package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.DailyResultDetailsQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("database")
public final class PostgresDailyResultDetailsQuery implements DailyResultDetailsQuery {
    private final JdbcTemplate jdbc; public PostgresDailyResultDetailsQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<ParsedGameResult> find(long playerId, GameType gameType, LocalDate gameDate) {
        return jdbc.query("SELECT * FROM game_result WHERE player_id = ? AND game_type = ? AND game_date = ?", (rs, row) -> {
            int max = rs.getInt("max_attempts"); ShareOutcome outcome = rs.getBoolean("solved") ? new ShareOutcome.Solved(rs.getInt("attempts_used"), max) : new ShareOutcome.Unsolved(max);
            String grid = rs.getString("normalized_board"); String topLeft = rs.getString("quadwords_top_left_board");
            Optional<QuadWordsBoards> boards = topLeft == null ? Optional.empty() : Optional.of(new QuadWordsBoards(QuadWordsBoard.fromCanonicalText(topLeft), QuadWordsBoard.fromCanonicalText(rs.getString("quadwords_top_right_board")), QuadWordsBoard.fromCanonicalText(rs.getString("quadwords_bottom_left_board")), QuadWordsBoard.fromCanonicalText(rs.getString("quadwords_bottom_right_board"))));
            Integer streak = rs.getObject("gridgames_streak", Integer.class);
            return new ParsedGameResult(gameType, gameDate, outcome, Duration.ofSeconds(rs.getLong("duration_seconds")), streak == null ? OptionalInt.empty() : OptionalInt.of(streak), grid == null ? Optional.empty() : Optional.of(new NormalizedBoard(List.of(grid.split("\\n")))), boards);
        }, playerId, gameType.name(), gameDate).stream().findFirst();
    }
}