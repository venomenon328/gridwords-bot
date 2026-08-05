package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the persistent bootstrap state; eligibility, claims and backoff stay in PostgreSQL. */
@Component
@Profile("database")
final class RecordBootstrapScheduler {
    private final RecordBootstrapCoordinator coordinator;
    private final GridwordsBotProperties properties;
    RecordBootstrapScheduler(RecordBootstrapCoordinator coordinator, GridwordsBotProperties properties) {
        this.coordinator = coordinator; this.properties = properties;
    }
    @Scheduled(fixedDelayString = "#{@recordBootstrapPollDelayMillis}")
    void poll() {
        coordinator.run(properties.discord().guildId());
    }
}
