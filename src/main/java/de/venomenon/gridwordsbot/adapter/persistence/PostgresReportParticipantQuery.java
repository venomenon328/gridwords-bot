package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.port.out.ReportParticipantQuery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL read adapter for report participants using the established player and participation tables. */
@Repository
@Profile("database")
public class PostgresReportParticipantQuery implements ReportParticipantQuery {
    private final JdbcTemplate jdbc;

    public PostgresReportParticipantQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ParticipantProfile> findParticipantsTouching(ReportPeriod period) {
        return jdbc.query("""
                SELECT p.discord_user_id, p.display_name, history.first_participation_start,
                    pp.active_from, pp.inactive_from
                FROM player p
                JOIN (
                    SELECT player_id, MIN(active_from) AS first_participation_start
                    FROM player_participation_period
                    GROUP BY player_id
                ) history ON history.player_id = p.discord_user_id
                JOIN player_participation_period pp ON pp.player_id = p.discord_user_id
                WHERE pp.active_from <= ?
                  AND (pp.inactive_from IS NULL OR pp.inactive_from > ?)
                ORDER BY history.first_participation_start, p.discord_user_id, pp.active_from
                """, resultSet -> {
            java.util.Map<Long, ParticipantProfileBuilder> profiles = new java.util.LinkedHashMap<>();
            while (resultSet.next()) {
                long playerId = resultSet.getLong("discord_user_id");
                ParticipantProfileBuilder profile = profiles.get(playerId);
                if (profile == null) {
                    profile = new ParticipantProfileBuilder(
                            playerId,
                            resultSet.getString("display_name"),
                            resultSet.getObject("first_participation_start", LocalDate.class));
                    profiles.put(playerId, profile);
                }
                profile.periods.add(new ParticipationPeriod(
                        playerId,
                        resultSet.getObject("active_from", LocalDate.class),
                        resultSet.getObject("inactive_from", LocalDate.class)));
            }
            return profiles.values().stream().map(ParticipantProfileBuilder::build).toList();
        }, period.endDate(), period.startDate());
    }

    private static final class ParticipantProfileBuilder {
        private final long discordUserId;
        private final String displayName;
        private final LocalDate firstParticipationStart;
        private final List<ParticipationPeriod> periods = new java.util.ArrayList<>();

        private ParticipantProfileBuilder(long discordUserId, String displayName, LocalDate firstParticipationStart) {
            this.discordUserId = discordUserId;
            this.displayName = displayName;
            this.firstParticipationStart = firstParticipationStart;
        }

        private ParticipantProfile build() {
            return new ParticipantProfile(discordUserId, displayName, firstParticipationStart, periods);
        }
    }
}
