package de.venomenon.gridwordsbot.adapter.observability;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MicrometerRecordLiveEvaluationMetricsTest {

    @Test
    void recordsOnlyTheClosedLowCardinalityOutcomeTagSet() {
        try (SimpleMeterRegistry registry = new SimpleMeterRegistry()) {
            var metrics = new MicrometerRecordLiveEvaluationMetrics(registry);
            var expectedOutcomes = Map.of(
                    RecordLiveEvaluationMetrics.Result.COMPLETED, "completed",
                    RecordLiveEvaluationMetrics.Result.FAILED_RETRYABLE, "failed_retryable",
                    RecordLiveEvaluationMetrics.Result.FAILED_PERMANENT, "failed_permanent",
                    RecordLiveEvaluationMetrics.Result.LOST_LEASE, "lost_lease",
                    RecordLiveEvaluationMetrics.Result.UNKNOWN, "unknown");

            expectedOutcomes.forEach((result, outcome) -> {
                metrics.record(result, Duration.ofMillis(25));

                assertThat(registry.get(MicrometerRecordLiveEvaluationMetrics.RUN_COUNTER_NAME)
                                .tag("outcome", outcome)
                                .counter()
                                .count())
                        .isEqualTo(1.0d);
                assertThat(registry.get(MicrometerRecordLiveEvaluationMetrics.RUN_DURATION_NAME)
                                .tag("outcome", outcome)
                                .timer()
                                .count())
                        .isEqualTo(1L);
            });

            assertThat(registry.getMeters()).hasSize(expectedOutcomes.size() * 2);
            assertThat(registry.getMeters()).allSatisfy(meter -> {
                assertThat(meter.getId().getTags()).singleElement().satisfies(tag -> {
                    assertThat(tag.getKey()).isEqualTo("outcome");
                    assertThat(expectedOutcomes.values()).contains(tag.getValue());
                });
            });
        }
    }
}
