package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.achievement.AchievementAnnouncementDeliveryCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Repeated wake-up only; PostgreSQL remains the source of delivery eligibility and recovery. */
@Component
@Profile("database")
@ConditionalOnBean(AchievementAnnouncementDeliveryCoordinator.class)
final class AchievementAnnouncementDeliveryScheduler {
    private final AchievementAnnouncementDeliveryCoordinator coordinator;
    AchievementAnnouncementDeliveryScheduler(AchievementAnnouncementDeliveryCoordinator coordinator) { this.coordinator = coordinator; }
    @Scheduled(fixedDelayString = "#{@achievementAnnouncementPollDelayMillis}")
    void poll() { coordinator.runNext(); }
}
