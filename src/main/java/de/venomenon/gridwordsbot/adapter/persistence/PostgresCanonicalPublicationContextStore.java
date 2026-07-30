package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.port.out.CanonicalPublicationContextStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL lookup used to restore contextual series lines when a canonical Discord message must be recreated. */
@Repository
@Profile("database")
public class PostgresCanonicalPublicationContextStore implements CanonicalPublicationContextStore {

    private final JdbcTemplate jdbc;

    public PostgresCanonicalPublicationContextStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HistoricalContext findForResult(long gameResultId) {
        if (gameResultId <= 0) {
            throw new IllegalArgumentException("gameResultId must be positive");
        }
        HistoricalContext context = jdbc.queryForObject("""
                SELECT
                    COALESCE(bool_or(personal_complete_established), FALSE) AS personal_complete,
                    COALESCE(bool_or(personal_perfect_established), FALSE) AS personal_perfect,
                    COALESCE(bool_or(shared_complete_established), FALSE) AS shared_complete,
                    COALESCE(bool_or(shared_perfect_established), FALSE) AS shared_perfect
                FROM submission
                WHERE game_result_id = ?
                """, (resultSet, rowNumber) -> new HistoricalContext(
                        resultSet.getBoolean("personal_complete"),
                        resultSet.getBoolean("personal_perfect"),
                        resultSet.getBoolean("shared_complete"),
                        resultSet.getBoolean("shared_perfect")), gameResultId);
        return context == null ? HistoricalContext.none() : context;
    }
}
