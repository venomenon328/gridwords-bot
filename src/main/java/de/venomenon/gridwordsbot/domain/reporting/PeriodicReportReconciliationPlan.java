package de.venomenon.gridwordsbot.domain.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** A deterministic, ordered plan for reconciling due periodic report periods. */
public record PeriodicReportReconciliationPlan(List<PeriodicReportReconciliationCandidate> candidates) {
    public PeriodicReportReconciliationPlan {
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }

        ReportType type = candidates.getFirst().type();
        for (int index = 0; index < candidates.size(); index++) {
            PeriodicReportReconciliationCandidate candidate = Objects.requireNonNull(
                    candidates.get(index), "candidates must not contain null");
            if (candidate.type() != type) {
                throw new IllegalArgumentException("candidates must use one report type");
            }
            boolean isLatestCandidate = index == candidates.size() - 1;
            if (!isLatestCandidate && candidate.action() != PeriodicReportReconciliationAction.EXPIRE) {
                throw new IllegalArgumentException("only the latest candidate may be delivered or reconciled");
            }
            if (index > 0) {
                LocalDate expectedStart = type.nextPeriodStartAfter(candidates.get(index - 1).period().startDate());
                if (!candidate.period().startDate().equals(expectedStart)) {
                    throw new IllegalArgumentException("candidates must be consecutive report periods");
                }
            }
        }
    }
}
