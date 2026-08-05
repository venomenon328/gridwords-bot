package de.venomenon.gridwordsbot.adapter.observability;

import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator.BootstrapRunResult;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Optional;

/** Micrometer adapter with a deliberately closed tag vocabulary. */
public final class MicrometerRecordBootstrapMetrics implements RecordBootstrapMetrics {
    static final String RUNS = "gridwords.record.bootstrap.runs";
    static final String DURATION = "gridwords.record.bootstrap.duration";
    private final MeterRegistry registry;

    public MicrometerRecordBootstrapMetrics(MeterRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry);
    }

    @Override
    public void record(BootstrapRunResult result, Optional<RecordWorkFailureCategory> failureCategory, Duration duration) {
        java.util.Objects.requireNonNull(result);
        java.util.Objects.requireNonNull(failureCategory);
        java.util.Objects.requireNonNull(duration);
        String resultTag = result.name().toLowerCase(java.util.Locale.ROOT);
        String failureTag = failureCategory.map(category -> category.name().toLowerCase(java.util.Locale.ROOT)).orElse("none");
        Timer.builder(DURATION).tags("result", resultTag, "failure_category", failureTag).register(registry).record(duration);
        Counter.builder(RUNS).tags("result", resultTag, "failure_category", failureTag).register(registry).increment();
    }
}
