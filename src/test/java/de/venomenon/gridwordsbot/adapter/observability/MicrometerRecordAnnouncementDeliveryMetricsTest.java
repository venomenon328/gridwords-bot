package de.venomenon.gridwordsbot.adapter.observability;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.record.RecordAnnouncementDeliveryCoordinator.RunResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MicrometerRecordAnnouncementDeliveryMetricsTest {

    @Test
    void exposesOnlyTheClosedOutcomeVocabularyAsMetricTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerRecordAnnouncementDeliveryMetrics metrics = new MicrometerRecordAnnouncementDeliveryMetrics(registry);
        Map<RunResult, String> expected = Map.of(
                RunResult.COMPLETED, "completed",
                RunResult.EXTERNALLY_REMOVED, "externally_removed",
                RunResult.LOST_LEASE, "lost_lease",
                RunResult.SUPPRESSED, "suppressed",
                RunResult.FAILED_RETRYABLE, "failed_retryable");

        expected.forEach((outcome, tag) -> metrics.record(outcome, Duration.ofMillis(25)));

        assertThat(registry.getMeters()).hasSize(expected.size() * 2).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).singleElement().satisfies(tag -> {
                    assertThat(tag.getKey()).isEqualTo("outcome");
                    assertThat(expected.values()).contains(tag.getValue());
                }));
    }
}
