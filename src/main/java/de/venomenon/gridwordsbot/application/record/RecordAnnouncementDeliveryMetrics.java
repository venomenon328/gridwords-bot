package de.venomenon.gridwordsbot.application.record;

import java.time.Duration;

/** Low-cardinality observation boundary for record-announcement delivery. */
public interface RecordAnnouncementDeliveryMetrics {
    void record(RecordAnnouncementDeliveryCoordinator.RunResult result, Duration duration);
}
