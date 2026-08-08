package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.achievement.AchievementBootstrapCoordinator;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Repeats persistent Achievement-bootstrap claims; PostgreSQL remains the source of eligibility and backoff. */
@Component
@Profile("database")
final class AchievementBootstrapScheduler {
    private final AchievementBootstrapCoordinator coordinator;
    private final GridwordsBotProperties properties;

    AchievementBootstrapScheduler(AchievementBootstrapCoordinator coordinator, GridwordsBotProperties properties) {
        this.coordinator = coordinator;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 60_000)
    void poll() {
        coordinator.run(properties.discord().guildId(), properties.discord().channelId());
    }
}
