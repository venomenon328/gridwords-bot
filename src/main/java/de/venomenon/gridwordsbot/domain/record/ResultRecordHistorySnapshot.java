package de.venomenon.gridwordsbot.domain.record;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The explicitly prior, immutable result history supplied to one evaluation. */
public record ResultRecordHistorySnapshot(List<ResultRecordObservation> priorResults) {

    public ResultRecordHistorySnapshot {
        priorResults = List.copyOf(Objects.requireNonNull(priorResults, "priorResults"));
        if (priorResults.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("priorResults must not contain null");
        }
        Set<Long> resultIds = new HashSet<>();
        if (priorResults.stream().anyMatch(result -> !resultIds.add(result.resultId()))) {
            throw new IllegalArgumentException("priorResults must contain distinct stable result IDs");
        }
    }

    public static ResultRecordHistorySnapshot empty() {
        return new ResultRecordHistorySnapshot(List.of());
    }
}
