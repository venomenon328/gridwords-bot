package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameResult;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.port.out.ReportGameResultQuery;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL result reader that deliberately does not depend on parser versions or board columns. */
@Repository
@Profile("database")
public class PostgresReportGameResultQuery implements ReportGameResultQuery {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresReportGameResultQuery(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public List<ReportGameResult> findResults(ReportPeriod period, Set<Long> participantIds) {
        if (participantIds.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds
                FROM game_result
                WHERE player_id IN (:participantIds)
                  AND game_date BETWEEN :periodStart AND :periodEnd
                ORDER BY player_id, game_date, game_type
                """, new MapSqlParameterSource()
                .addValue("participantIds", participantIds)
                .addValue("periodStart", period.startDate())
                .addValue("periodEnd", period.endDate()), (resultSet, row) -> {
            int maximumAttempts = resultSet.getInt("max_attempts");
            ShareOutcome outcome = resultSet.getBoolean("solved")
                    ? new ShareOutcome.Solved(resultSet.getInt("attempts_used"), maximumAttempts)
                    : new ShareOutcome.Unsolved(maximumAttempts);
            return new ReportGameResult(
                    resultSet.getLong("player_id"),
                    GameType.valueOf(resultSet.getString("game_type")),
                    resultSet.getObject("game_date", java.time.LocalDate.class),
                    outcome,
                    Duration.ofSeconds(resultSet.getLong("duration_seconds")));
        });
    }
}
