package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameResult;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakHistory;
import de.venomenon.gridwordsbot.port.out.ReportStreakHistoryQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL history reader bounded by the inclusive report streak cutoff. */
@Repository
@Profile("database")
public class PostgresReportStreakHistoryQuery implements ReportStreakHistoryQuery {
    private final JdbcTemplate jdbc;

    public PostgresReportStreakHistoryQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReportStreakHistory findThrough(LocalDate inclusiveCutoff) {
        return new ReportStreakHistory(participationPeriodsThrough(inclusiveCutoff), resultsThrough(inclusiveCutoff));
    }

    private List<ParticipationPeriod> participationPeriodsThrough(LocalDate inclusiveCutoff) {
        List<de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod> typedPeriods = jdbc.query("""
                SELECT player_id, game_type, active_from, inactive_from
                FROM player_participation_period
                WHERE active_from <= ?
                ORDER BY player_id, game_type, active_from
                """, (resultSet, row) -> new de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod(
                resultSet.getLong("player_id"),
                GameType.valueOf(resultSet.getString("game_type")),
                resultSet.getObject("active_from", LocalDate.class),
                resultSet.getObject("inactive_from", LocalDate.class)), inclusiveCutoff);
        return ParticipationPeriodCompatibility.union(typedPeriods);
    }

    private List<ReportGameResult> resultsThrough(LocalDate inclusiveCutoff) {
        return jdbc.query("""
                SELECT player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds
                FROM game_result
                WHERE game_date <= ?
                ORDER BY player_id, game_date, game_type
                """, (resultSet, row) -> {
            int maximumAttempts = resultSet.getInt("max_attempts");
            ShareOutcome outcome = resultSet.getBoolean("solved")
                    ? new ShareOutcome.Solved(resultSet.getInt("attempts_used"), maximumAttempts)
                    : new ShareOutcome.Unsolved(maximumAttempts);
            return new ReportGameResult(
                    resultSet.getLong("player_id"),
                    GameType.valueOf(resultSet.getString("game_type")),
                    resultSet.getObject("game_date", LocalDate.class),
                    outcome,
                    Duration.ofSeconds(resultSet.getLong("duration_seconds")));
        }, inclusiveCutoff);
    }
}
