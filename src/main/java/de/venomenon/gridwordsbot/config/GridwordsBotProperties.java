package de.venomenon.gridwordsbot.config;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gridwords")
public record GridwordsBotProperties(Discord discord, Schedule schedule, Storage storage) {
    public record Discord(boolean enabled, String token, long guildId, long channelId, List<Long> adminUserIds) { }
    public record Schedule(LocalTime firstReminder, LocalTime secondReminder, LocalTime weeklyReport,
                           LocalTime monthlyReport, ZoneId timeZone) {
        public Schedule {
            Objects.requireNonNull(firstReminder, "firstReminder");
            Objects.requireNonNull(secondReminder, "secondReminder");
            Objects.requireNonNull(weeklyReport, "weeklyReport");
            Objects.requireNonNull(monthlyReport, "monthlyReport");
            Objects.requireNonNull(timeZone, "timeZone");
            if (!firstReminder.isBefore(secondReminder)) {
                throw new IllegalArgumentException("firstReminder must be before secondReminder");
            }
        }
    }
    public record Storage(int rawImageRetentionHours) { }
}