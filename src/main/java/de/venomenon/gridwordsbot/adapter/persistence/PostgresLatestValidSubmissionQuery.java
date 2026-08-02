package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.LatestValidSubmissionQuery;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL reader for a player's latest valid submission per game type. */
@Repository
@Profile("database")
public class PostgresLatestValidSubmissionQuery implements LatestValidSubmissionQuery {
    private final JdbcTemplate jdbc;

    public PostgresLatestValidSubmissionQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LatestValidSubmission> findLatestValidSubmissions(long discordUserId) {
        if (discordUserId <= 0) {
            throw new IllegalArgumentException("discordUserId must be positive");
        }
        return jdbc.query("""
                SELECT DISTINCT ON (r.game_type)
                    r.game_type, r.solved, r.attempts_used, r.max_attempts,
                    r.duration_seconds, r.game_date, s.received_at
                FROM submission s
                JOIN game_result r ON r.id = s.game_result_id
                WHERE r.player_id = ?
                  AND s.processing_state <> 'SUPERSEDED'
                ORDER BY r.game_type, s.received_at DESC, s.source_message_id DESC
                """, (resultSet, row) -> {
            int maximumAttempts = resultSet.getInt("max_attempts");
            ShareOutcome outcome = resultSet.getBoolean("solved")
                    ? new ShareOutcome.Solved(resultSet.getInt("attempts_used"), maximumAttempts)
                    : new ShareOutcome.Unsolved(maximumAttempts);
            return new LatestValidSubmission(
                    GameType.valueOf(resultSet.getString("game_type")),
                    outcome,
                    Duration.ofSeconds(resultSet.getLong("duration_seconds")),
                    resultSet.getObject("game_date", java.time.LocalDate.class),
                    resultSet.getObject("received_at", OffsetDateTime.class).toInstant());
        }, discordUserId);
    }
}
