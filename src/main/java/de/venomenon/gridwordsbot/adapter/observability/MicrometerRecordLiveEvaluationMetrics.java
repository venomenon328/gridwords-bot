package de.venomenon.gridwordsbot.adapter.observability;

import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationCoordinator.RunResult;
import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;

/** Micrometer adapter with a closed, outcome-only tag vocabulary. */
public final class MicrometerRecordLiveEvaluationMetrics implements RecordLiveEvaluationMetrics {
    static final String RUNS = "gridwords.record.live-evaluation.runs";
    static final String DURATION = "gridwords.record.live-evaluation.duration";
    private final MeterRegistry registry;

    public MicrometerRecordLiveEvaluationMetrics(MeterRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void record(RunResult result, Duration duration) {
        java.util.Objects.requireNonNull(result, "result");
        java.util.Objects.requireNonNull(duration, "duration");
        String outcome = result.name().toLowerCase(Locale.ROOT);
        Counter.builder(RUNS).tag("outcome", outcome).register(registry).increment();
        Timer.builder(DURATION).tag("outcome", outcome).register(registry).record(duration);
    }
}
