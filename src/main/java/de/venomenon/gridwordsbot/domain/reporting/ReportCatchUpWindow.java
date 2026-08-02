package de.venomenon.gridwordsbot.domain.reporting;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** The half-open interval in which an overdue report may still be delivered. */
public record ReportCatchUpWindow(ReportDueAt dueAt, Duration duration) {
    public ReportCatchUpWindow {
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }

    public boolean isOpenAt(Instant now) {
        Objects.requireNonNull(now, "now");
        Instant dueInstant = dueAt.instant();
        return !now.isBefore(dueInstant) && now.isBefore(dueInstant.plus(duration));
    }
}
