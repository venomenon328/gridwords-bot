package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the persistent bootstrap state; eligibility, claims and backoff stay in PostgreSQL. */
@Component
@Profile("database")
final class RecordBootstrapScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(RecordBootstrapScheduler.class);
    private final RecordBootstrapCoordinator coordinator;
    private final GridwordsBotProperties properties;
    RecordBootstrapScheduler(RecordBootstrapCoordinator coordinator, GridwordsBotProperties properties) {
        this.coordinator = coordinator; this.properties = properties;
    }
    @Scheduled(fixedDelayString = "${gridwords.records.bootstrap-poll-delay:PT1M}")
    void poll() {
        var result = coordinator.run(properties.discord().guildId());
        LOG.info("record bootstrap poll completed: guildId={}, result={}", properties.discord().guildId(), result);
    }
}
