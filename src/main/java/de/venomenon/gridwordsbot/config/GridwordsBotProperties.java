package de.venomenon.gridwordsbot.config;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "gridwords")
public record GridwordsBotProperties(Discord discord, Schedule schedule, Storage storage, Excuses excuses) {
    @ConstructorBinding
    public GridwordsBotProperties {
        excuses = excuses == null ? Excuses.defaults() : excuses;
    }

    public GridwordsBotProperties(Discord discord, Schedule schedule, Storage storage) {
        this(discord, schedule, storage, Excuses.defaults());
    }
    public record Discord(boolean enabled, String token, long guildId, long channelId, List<Long> adminUserIds) { }
    public record Schedule(LocalTime firstReminder, LocalTime secondReminder, LocalTime weeklyReport,
                           LocalTime monthlyReport, LocalTime dailyCleanup, ZoneId timeZone) {
        @ConstructorBinding
        public Schedule {
            Objects.requireNonNull(firstReminder, "firstReminder");
            Objects.requireNonNull(secondReminder, "secondReminder");
            Objects.requireNonNull(weeklyReport, "weeklyReport");
            Objects.requireNonNull(monthlyReport, "monthlyReport");
            Objects.requireNonNull(dailyCleanup, "dailyCleanup");
            Objects.requireNonNull(timeZone, "timeZone");
            if (!firstReminder.isBefore(secondReminder)) {
                throw new IllegalArgumentException("firstReminder must be before secondReminder");
            }
        }
        public Schedule(LocalTime firstReminder, LocalTime secondReminder, LocalTime weeklyReport,
                        LocalTime monthlyReport, ZoneId timeZone) {
            this(firstReminder, secondReminder, weeklyReport, monthlyReport, LocalTime.of(6, 0), timeZone);
        }
    }
    public record Storage(int rawImageRetentionHours) { }
    public record Excuses(boolean enabled, Duration offerLifetime, int expirationPageSize, int expirationMaxPages) {
        @ConstructorBinding
        public Excuses {
            Objects.requireNonNull(offerLifetime, "offerLifetime");
            if (offerLifetime.isZero() || offerLifetime.isNegative()) {
                throw new IllegalArgumentException("offerLifetime must be positive");
            }
            if (expirationPageSize < 1 || expirationMaxPages < 1) {
                throw new IllegalArgumentException("excuse expiration limits must be positive");
            }
        }
        public Excuses(boolean enabled, Duration offerLifetime) {
            this(enabled, offerLifetime, 25, 4);
        }
        public static Excuses defaults() {
            return new Excuses(false, Duration.ofMinutes(15), 25, 4);
        }
    }
}
