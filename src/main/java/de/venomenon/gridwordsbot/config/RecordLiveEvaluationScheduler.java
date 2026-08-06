package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Frequent, non-overlapping trigger for durable record live-evaluation work. */
@Component
@Profile("database")
@ConditionalOnProperty(
        prefix = "gridwords.records", name = "live-evaluation-enabled", havingValue = "true", matchIfMissing = true)
final class RecordLiveEvaluationScheduler {
    private final RecordLiveEvaluationCoordinator coordinator;

    RecordLiveEvaluationScheduler(RecordLiveEvaluationCoordinator coordinator) {
        this.coordinator = java.util.Objects.requireNonNull(coordinator, "coordinator");
    }

    @Scheduled(fixedDelayString = "#{@recordLiveEvaluationPollDelayMillis}")
    void poll() {
        coordinator.runNext();
    }
}
