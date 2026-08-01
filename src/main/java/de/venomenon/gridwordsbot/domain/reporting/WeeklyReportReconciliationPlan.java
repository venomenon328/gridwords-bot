package de.venomenon.gridwordsbot.domain.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** A deterministic, ordered plan for reconciling due weekly report periods. */
public record WeeklyReportReconciliationPlan(List<WeeklyReportReconciliationCandidate> candidates) {
    public WeeklyReportReconciliationPlan {
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }

        for (int index = 0; index < candidates.size(); index++) {
            WeeklyReportReconciliationCandidate candidate = Objects.requireNonNull(
                    candidates.get(index), "candidates must not contain null");
            boolean isLatestCandidate = index == candidates.size() - 1;
            if (!isLatestCandidate && candidate.action() != WeeklyReportReconciliationAction.EXPIRE) {
                throw new IllegalArgumentException("only the latest candidate may be delivered or reconciled");
            }
            if (index > 0) {
                LocalDate expectedStart = candidates.get(index - 1).period().startDate().plusWeeks(1);
                if (!candidate.period().startDate().equals(expectedStart)) {
                    throw new IllegalArgumentException("candidates must be consecutive weekly periods");
                }
            }
        }
    }
}
