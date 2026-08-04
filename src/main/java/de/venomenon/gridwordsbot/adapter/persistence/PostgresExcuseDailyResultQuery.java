package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseDailyResult;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.PriorValidResultQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Independent read adapter for the frozen daily comparison used while a result is being stored.
 * It deliberately does not depend on {@link PostgresPersistenceAdapter}, so the enabled excuse
 * lifecycle can be constructor-injected into that adapter without a Spring bean cycle.
 */
@Repository
@Profile("database")
public class PostgresExcuseDailyResultQuery implements PriorValidResultQuery {

    private final JdbcTemplate jdbc;

    public PostgresExcuseDailyResultQuery(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<ExcuseDailyResult> findPriorValidResults(
            long excludedPlayerId,
            GameType gameType,
            LocalDate gameDate,
            Set<Long> participatingPlayerIds) {
        if (excludedPlayerId <= 0) {
            throw new IllegalArgumentException("excludedPlayerId must be positive");
        }
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(gameDate, "gameDate");
        List<Long> participants = Objects.requireNonNull(participatingPlayerIds, "participatingPlayerIds").stream()
                .filter(playerId -> playerId != excludedPlayerId)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (participants.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(", ", java.util.Collections.nCopies(participants.size(), "?"));
        List<Object> parameters = new ArrayList<>(3 + participants.size());
        parameters.add(gameType.name());
        parameters.add(gameDate);
        parameters.addAll(participants);

        return jdbc.query("""
                SELECT player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds
                FROM game_result
                WHERE game_type = ?
                  AND game_date = ?
                  AND player_id IN (%s)
                ORDER BY player_id
                """.formatted(placeholders), (resultSet, rowNumber) -> {
                    int maximum = resultSet.getInt("max_attempts");
                    ShareOutcome outcome = resultSet.getBoolean("solved")
                            ? new ShareOutcome.Solved(resultSet.getInt("attempts_used"), maximum)
                            : new ShareOutcome.Unsolved(maximum);
                    return new ExcuseDailyResult(
                            resultSet.getLong("player_id"),
                            GameType.valueOf(resultSet.getString("game_type")),
                            resultSet.getObject("game_date", LocalDate.class),
                            outcome,
                            Duration.ofSeconds(resultSet.getLong("duration_seconds")));
                }, parameters.toArray());
    }
}
