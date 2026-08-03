package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.port.out.DailyStatusInteractionContextQuery;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("database")
public class PostgresDailyStatusInteractionContextQuery implements DailyStatusInteractionContextQuery {
    private final JdbcTemplate jdbc; public PostgresDailyStatusInteractionContextQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<Context> findCurrent(long guildId, long channelId, long messageId, LocalDate gameDate) {
        Boolean matches = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM daily_status_message WHERE guild_id = ? AND channel_id = ? AND game_date = ? AND bot_message_id = ?)", Boolean.class, guildId, channelId, gameDate, messageId);
        if (!Boolean.TRUE.equals(matches)) return Optional.empty();
        return Optional.of(new Context(jdbc.query("""
                SELECT p.discord_user_id, p.display_name
                FROM player p
                WHERE EXISTS (
                    SELECT 1 FROM player_participation_period pp
                    WHERE pp.player_id = p.discord_user_id
                      AND pp.active_from <= ?
                      AND (pp.inactive_from IS NULL OR ? < pp.inactive_from)
                )
                ORDER BY LOWER(p.display_name), p.discord_user_id
                """, (rs, row) -> new Participant(rs.getLong("discord_user_id"), rs.getString("display_name")),
                gameDate, gameDate)));
    }
}