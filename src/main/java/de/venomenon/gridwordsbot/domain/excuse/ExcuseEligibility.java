package de.venomenon.gridwordsbot.domain.excuse;

import java.util.Objects;
import java.util.Set;

/** Immutable result of one offer decision, including the facts needed by template selection later. */
public record ExcuseEligibility(
        boolean eligible,
        Set<ExcuseReason> reasons,
        ExcuseContext context,
        DailyComparisonSnapshot comparisonSnapshot,
        QuadWordsBoardAnalysis boardAnalysis) {

    public ExcuseEligibility {
        reasons = Set.copyOf(Objects.requireNonNull(reasons, "reasons"));
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(comparisonSnapshot, "comparisonSnapshot");
        Objects.requireNonNull(boardAnalysis, "boardAnalysis");
        if (eligible != !reasons.isEmpty()) {
            throw new IllegalArgumentException("eligibility must correspond to the presence of reasons");
        }
    }
}
