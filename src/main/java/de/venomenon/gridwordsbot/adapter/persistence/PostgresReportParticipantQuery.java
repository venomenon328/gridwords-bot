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
                SELECT p.discord_user_id, p.display_name, pp.game_type, pp.active_from, pp.inactive_from
                FROM player p
                JOIN player_participation_period pp ON pp.player_id = p.discord_user_id
                ORDER BY p.discord_user_id, pp.game_type, pp.active_from
                """, resultSet -> {
            java.util.Map<Long, ParticipantProfileBuilder> profiles = new java.util.LinkedHashMap<>();
            while (resultSet.next()) {
                long playerId = resultSet.getLong("discord_user_id");
                ParticipantProfileBuilder profile = profiles.get(playerId);
                if (profile == null) {
                    profile = new ParticipantProfileBuilder(playerId, resultSet.getString("display_name"));
                    profiles.put(playerId, profile);
                }
                profile.typedPeriods.add(new de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod(
                        playerId,
                        de.venomenon.gridwordsbot.domain.model.GameType.valueOf(resultSet.getString("game_type")),
                        resultSet.getObject("active_from", LocalDate.class),
                        resultSet.getObject("inactive_from", LocalDate.class)));
            }
            return profiles.values().stream()
                    .map(profile -> profile.build(period))
                    .flatMap(java.util.Optional::stream)
                    .sorted(java.util.Comparator.comparing(ParticipantProfile::firstParticipationStart)
                            .thenComparingLong(ParticipantProfile::discordUserId))
                    .toList();
        });
    }

    private static final class ParticipantProfileBuilder {
        private final long discordUserId;
        private final String displayName;
        private final List<de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod> typedPeriods =
                new java.util.ArrayList<>();

        private ParticipantProfileBuilder(long discordUserId, String displayName) {
            this.discordUserId = discordUserId;
            this.displayName = displayName;
        }

        private java.util.Optional<ParticipantProfile> build(ReportPeriod period) {
            List<ParticipationPeriod> globalPeriods = ParticipationPeriodCompatibility.union(typedPeriods);
            List<ParticipationPeriod> touchingPeriods = globalPeriods.stream()
                    .filter(candidate -> candidate.activeFrom().compareTo(period.endDate()) <= 0
                            && (candidate.inactiveFrom() == null
                                    || candidate.inactiveFrom().isAfter(period.startDate())))
                    .toList();
            if (touchingPeriods.isEmpty()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new ParticipantProfile(
                    discordUserId, displayName, globalPeriods.getFirst().activeFrom(), touchingPeriods));
        }
    }
}
