package de.venomenon.gridwordsbot.config;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gridwords")
public record GridwordsBotProperties(
        Discord discord,
        Players players,
        Schedule schedule,
        Storage storage) {

    public record Discord(
            boolean enabled,
            String token,
            long guildId,
            long channelId,
            List<Long> adminUserIds) {
    }

    public record Players(Player first, Player second) {
    }

    public record Player(long userId, String displayName) {
    }

    public record Schedule(
            LocalTime firstReminder,
            LocalTime secondReminder,
            LocalTime weeklyReport,
            LocalTime monthlyReport,
            ZoneId timeZone) {
    }

    public record Storage(int rawImageRetentionHours) {
    }
}
