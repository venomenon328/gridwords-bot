package de.venomenon.gridwordsbot.domain.record;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Aggregable and deterministic classifications for one candidate streak run. */
public record StreakRecordEvaluationSet(StreakRun candidate, List<StreakRecordEvaluation> evaluations) {
    public StreakRecordEvaluationSet {
        Objects.requireNonNull(candidate, "candidate");
        List<StreakRecordEvaluation> copy = List.copyOf(Objects.requireNonNull(evaluations, "evaluations"));
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("evaluations must contain non-null entries");
        }
        if (copy.stream().anyMatch(evaluation -> !evaluation.candidate().equals(candidate))) {
            throw new IllegalArgumentException("all evaluations must belong to candidate");
        }
        if (copy.stream().map(e -> e.definition().key()).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("duplicate definition evaluation");
        }
        evaluations = copy.stream().sorted(Comparator.comparing(e -> e.definition().key())).toList();
    }

    public List<StreakRecordEvaluation> notable() {
        return evaluations.stream().filter(e -> e.classification() != StreakRecordClassification.NONE).toList();
    }

    public List<StreakRecordEvaluation> publiclyEligible() {
        return evaluations.stream().filter(StreakRecordEvaluation::publicAnnouncementEligible).toList();
    }
}
