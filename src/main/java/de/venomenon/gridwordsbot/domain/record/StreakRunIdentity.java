package de.venomenon.gridwordsbot.domain.record;

import java.time.LocalDate;
import java.util.Objects;

/** Stable identity of one derived streak run. */
public record StreakRunIdentity(StreakRecordMetric metric, RecordScope ownerScope, LocalDate startDate) {
    public StreakRunIdentity {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(ownerScope, "ownerScope");
        Objects.requireNonNull(startDate, "startDate");
        if (ownerScope.type() == RecordScopeType.SERVER_INDIVIDUAL) {
            throw new IllegalArgumentException("server-individual scope is a comparison scope, not a run owner");
        }
        if (ownerScope.type() == RecordScopeType.SHARED && !metric.sharedScopeAllowed()) {
            throw new IllegalArgumentException("metric does not allow a shared run");
        }
    }
}
