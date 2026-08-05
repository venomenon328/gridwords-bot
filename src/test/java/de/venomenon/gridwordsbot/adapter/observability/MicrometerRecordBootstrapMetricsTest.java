package de.venomenon.gridwordsbot.adapter.observability;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator.BootstrapRunResult;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MicrometerRecordBootstrapMetricsTest {
    @Test
    void recordsOnlyClosedLowCardinalityResultAndFailureCategoryTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerRecordBootstrapMetrics metrics = new MicrometerRecordBootstrapMetrics(registry);

        metrics.record(BootstrapRunResult.SUCCEEDED, Optional.empty(), Duration.ofMillis(12));
        metrics.record(BootstrapRunResult.RETRY_SCHEDULED, Optional.of(RecordWorkFailureCategory.RETRYABLE), Duration.ofMillis(4));
        metrics.record(BootstrapRunResult.UNKNOWN, Optional.of(RecordWorkFailureCategory.UNKNOWN), Duration.ofMillis(1));

        assertThat(registry.find(MicrometerRecordBootstrapMetrics.RUNS).meters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .containsExactlyInAnyOrder("result", "failure_category"));
        assertThat(registry.get(MicrometerRecordBootstrapMetrics.RUNS)
                .tags("result", "succeeded", "failure_category", "none").counter().count()).isEqualTo(1);
        assertThat(registry.get(MicrometerRecordBootstrapMetrics.DURATION)
                .tags("result", "retry_scheduled", "failure_category", "retryable").timer().count()).isEqualTo(1);
        assertThat(registry.find(MicrometerRecordBootstrapMetrics.RUNS).meters())
                .extracting(meter -> meter.getId().getTags().toString())
                .noneMatch(tags -> tags.contains("guild") || tags.contains("token") || tags.contains("state"));
    }
}
