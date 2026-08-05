package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator.BootstrapRunResult;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import java.time.Duration;
import java.util.Optional;

/** Low-cardinality observation boundary for a complete bootstrap coordinator run. */
public interface RecordBootstrapMetrics {
    void record(BootstrapRunResult result, Optional<RecordWorkFailureCategory> failureCategory, Duration duration);
}
