package de.venomenon.gridwordsbot.adapter.observability;

import de.venomenon.gridwordsbot.application.record.RecordAnnouncementDeliveryCoordinator.RunResult;
import de.venomenon.gridwordsbot.application.record.RecordAnnouncementDeliveryMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;

/** Closed outcome vocabulary; no guild, player, channel or announcement identities become metric tags. */
public final class MicrometerRecordAnnouncementDeliveryMetrics implements RecordAnnouncementDeliveryMetrics {
    private final MeterRegistry registry;
    public MicrometerRecordAnnouncementDeliveryMetrics(MeterRegistry registry) { this.registry = java.util.Objects.requireNonNull(registry); }
    @Override public void record(RunResult result, Duration duration) {
        String outcome = result.name().toLowerCase(Locale.ROOT);
        Counter.builder("gridwords.record.announcement.runs").tag("outcome", outcome).register(registry).increment();
        Timer.builder("gridwords.record.announcement.duration").tag("outcome", outcome).register(registry).record(duration);
    }
}
