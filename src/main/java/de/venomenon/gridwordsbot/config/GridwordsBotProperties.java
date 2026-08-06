package de.venomenon.gridwordsbot.config;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "gridwords")
public record GridwordsBotProperties(
        Discord discord,
        Schedule schedule,
        Storage storage,
        ExcuseGenerator excuseGenerator,
        Excuses excuses,
        Records records) {
    @ConstructorBinding
    public GridwordsBotProperties {
        excuseGenerator = excuseGenerator == null ? ExcuseGenerator.defaults() : excuseGenerator;
        excuses = excuses == null ? Excuses.defaults() : excuses;
        records = records == null ? Records.defaults() : records;
    }

    public GridwordsBotProperties(Discord discord, Schedule schedule, Storage storage) {
        this(discord, schedule, storage, ExcuseGenerator.defaults(), Excuses.defaults(), Records.defaults());
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
    public record ExcuseGenerator(boolean contextualEnabled) {
        public static ExcuseGenerator defaults() {
            return new ExcuseGenerator(false);
        }
    }
    public record Excuses(Duration offerLifetime, int expirationPageSize, int expirationMaxPages) {
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
        public Excuses(Duration offerLifetime) {
            this(offerLifetime, 25, 4);
        }
        public static Excuses defaults() {
            return new Excuses(Duration.ofMinutes(15), 25, 4);
        }
    }
    /** Operational limits for the persistent record-bootstrap and live-evaluation workers. */
    public record Records(
            Duration bootstrapPollDelay,
            Duration bootstrapLeaseDuration,
            Duration bootstrapRetryBackoff,
            Boolean liveEvaluationEnabled,
            Duration liveEvaluationPollDelay,
            Duration liveEvaluationLeaseDuration,
            Duration liveEvaluationHeartbeatInterval,
            Duration liveEvaluationInitialRetryBackoff,
            Duration liveEvaluationMaxRetryBackoff) {
        private static final Duration MINIMUM_POLL_DELAY = Duration.ofMillis(1);
        private static final Duration DEFAULT_BOOTSTRAP_POLL_DELAY = Duration.ofMinutes(1);
        private static final Duration DEFAULT_BOOTSTRAP_LEASE_DURATION = Duration.ofMinutes(2);
        private static final Duration DEFAULT_BOOTSTRAP_RETRY_BACKOFF = Duration.ofMinutes(1);
        private static final Duration DEFAULT_LIVE_POLL_DELAY = Duration.ofSeconds(10);
        private static final Duration DEFAULT_LIVE_LEASE_DURATION = Duration.ofMinutes(2);
        private static final Duration DEFAULT_LIVE_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
        private static final Duration DEFAULT_LIVE_INITIAL_RETRY_BACKOFF = Duration.ofSeconds(10);
        private static final Duration DEFAULT_LIVE_MAX_RETRY_BACKOFF = Duration.ofMinutes(5);

        @ConstructorBinding
        public Records {
            bootstrapPollDelay = defaultIfNull(bootstrapPollDelay, DEFAULT_BOOTSTRAP_POLL_DELAY);
            bootstrapLeaseDuration = defaultIfNull(bootstrapLeaseDuration, DEFAULT_BOOTSTRAP_LEASE_DURATION);
            bootstrapRetryBackoff = defaultIfNull(bootstrapRetryBackoff, DEFAULT_BOOTSTRAP_RETRY_BACKOFF);
            requireAtLeast(bootstrapPollDelay, MINIMUM_POLL_DELAY, "bootstrapPollDelay");
            requirePositive(bootstrapLeaseDuration, "bootstrapLeaseDuration");
            requirePositive(bootstrapRetryBackoff, "bootstrapRetryBackoff");
            liveEvaluationEnabled = liveEvaluationEnabled == null || liveEvaluationEnabled;
            liveEvaluationPollDelay = defaultIfNull(liveEvaluationPollDelay, DEFAULT_LIVE_POLL_DELAY);
            liveEvaluationLeaseDuration = defaultIfNull(liveEvaluationLeaseDuration, DEFAULT_LIVE_LEASE_DURATION);
            liveEvaluationHeartbeatInterval = defaultIfNull(
                    liveEvaluationHeartbeatInterval, DEFAULT_LIVE_HEARTBEAT_INTERVAL);
            liveEvaluationInitialRetryBackoff = defaultIfNull(
                    liveEvaluationInitialRetryBackoff, DEFAULT_LIVE_INITIAL_RETRY_BACKOFF);
            liveEvaluationMaxRetryBackoff = defaultIfNull(
                    liveEvaluationMaxRetryBackoff, DEFAULT_LIVE_MAX_RETRY_BACKOFF);
            requireAtLeast(liveEvaluationPollDelay, MINIMUM_POLL_DELAY, "liveEvaluationPollDelay");
            requirePositive(liveEvaluationLeaseDuration, "liveEvaluationLeaseDuration");
            requirePositive(liveEvaluationHeartbeatInterval, "liveEvaluationHeartbeatInterval");
            requirePositive(liveEvaluationInitialRetryBackoff, "liveEvaluationInitialRetryBackoff");
            requirePositive(liveEvaluationMaxRetryBackoff, "liveEvaluationMaxRetryBackoff");
            if (liveEvaluationHeartbeatInterval.compareTo(liveEvaluationLeaseDuration) >= 0) {
                throw new IllegalArgumentException(
                        "liveEvaluationHeartbeatInterval must be shorter than liveEvaluationLeaseDuration");
            }
            if (liveEvaluationInitialRetryBackoff.compareTo(liveEvaluationMaxRetryBackoff) > 0) {
                throw new IllegalArgumentException(
                        "liveEvaluationInitialRetryBackoff must not exceed liveEvaluationMaxRetryBackoff");
            }
        }

        public Records(
                Duration bootstrapPollDelay,
                Duration bootstrapLeaseDuration,
                Duration bootstrapRetryBackoff) {
            this(
                    bootstrapPollDelay,
                    bootstrapLeaseDuration,
                    bootstrapRetryBackoff,
                    true,
                    DEFAULT_LIVE_POLL_DELAY,
                    DEFAULT_LIVE_LEASE_DURATION,
                    DEFAULT_LIVE_HEARTBEAT_INTERVAL,
                    DEFAULT_LIVE_INITIAL_RETRY_BACKOFF,
                    DEFAULT_LIVE_MAX_RETRY_BACKOFF);
        }

        public static Records defaults() {
            return new Records(
                    DEFAULT_BOOTSTRAP_POLL_DELAY,
                    DEFAULT_BOOTSTRAP_LEASE_DURATION,
                    DEFAULT_BOOTSTRAP_RETRY_BACKOFF,
                    true,
                    DEFAULT_LIVE_POLL_DELAY,
                    DEFAULT_LIVE_LEASE_DURATION,
                    DEFAULT_LIVE_HEARTBEAT_INTERVAL,
                    DEFAULT_LIVE_INITIAL_RETRY_BACKOFF,
                    DEFAULT_LIVE_MAX_RETRY_BACKOFF);
        }
        private static Duration defaultIfNull(Duration value, Duration fallback) {
            return value == null ? fallback : value;
        }
        private static void requireAtLeast(Duration value, Duration minimum, String name) {
            Objects.requireNonNull(value, name);
            if (value.compareTo(minimum) < 0) {
                throw new IllegalArgumentException(name + " must be at least " + minimum);
            }
        }
        private static void requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
