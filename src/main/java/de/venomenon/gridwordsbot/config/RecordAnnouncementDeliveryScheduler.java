package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.record.RecordAnnouncementDeliveryCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Frequent trigger only; PostgreSQL owns selection, claims, leases and retry eligibility. */
@Component
@Profile("database")
@ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
final class RecordAnnouncementDeliveryScheduler {
    private final RecordAnnouncementDeliveryCoordinator coordinator;
    RecordAnnouncementDeliveryScheduler(RecordAnnouncementDeliveryCoordinator coordinator) { this.coordinator = coordinator; }
    @Scheduled(fixedDelayString = "#{@recordAnnouncementPollDelayMillis}")
    void poll() { coordinator.runNext(); }
}
