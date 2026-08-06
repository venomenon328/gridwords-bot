package de.venomenon.gridwordsbot.application.record;

import java.time.Duration;

public final class NoOpRecordAnnouncementDeliveryMetrics implements RecordAnnouncementDeliveryMetrics {
    @Override public void record(RecordAnnouncementDeliveryCoordinator.RunResult result, Duration duration) { }
}
