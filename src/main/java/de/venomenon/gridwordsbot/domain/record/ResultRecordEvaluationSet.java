package de.venomenon.gridwordsbot.domain.record;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** The deterministic, aggregable six-definition evaluation of one candidate result. */
public record ResultRecordEvaluationSet(
        ResultRecordObservation candidate,
        RecordProcessingOrigin origin,
        List<ResultRecordEvaluation> evaluations) {

    public ResultRecordEvaluationSet {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(origin, "origin");
        evaluations = List.copyOf(Objects.requireNonNull(evaluations, "evaluations"));
        if (evaluations.size() != ResultRecordMetric.values().length * 2) {
            throw new IllegalArgumentException("evaluation set must contain all six result-record definitions");
        }
        if (evaluations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("evaluations must not contain null");
        }

        List<DefinitionIdentity> actualOrder = new ArrayList<>();
        Set<RecordDefinitionKey> keys = new HashSet<>();
        for (ResultRecordEvaluation evaluation : evaluations) {
            if (!(evaluation.definition().metric() instanceof ResultRecordMetric metric)) {
                throw new IllegalArgumentException("evaluation set must contain result definitions only");
            }
            if (!evaluation.definition().game().filter(game -> game == candidate.game()).isPresent()) {
                throw new IllegalArgumentException("evaluation definition game does not match candidate");
            }
            if (evaluation.scope() instanceof RecordScope.Personal personal
                    && personal.playerId() != candidate.playerId()) {
                throw new IllegalArgumentException("personal evaluation scope must belong to candidate player");
            }
            if (evaluation.action() == ResultRecordEvaluationAction.IMPROVED
                    && !evaluation.resultingSourceReference().equals(candidate.sourceReference())) {
                throw new IllegalArgumentException("improved evaluation must use the candidate as resulting source");
            }
            if (evaluation.publicAnnouncementEligible() && !origin.publicAnnouncementEligible()) {
                throw new IllegalArgumentException("silent processing origin cannot contain public announcements");
            }
            if (!keys.add(evaluation.definition().key())) {
                throw new IllegalArgumentException("evaluation definition keys must be unique");
            }
            actualOrder.add(new DefinitionIdentity(metric, evaluation.scope().type()));
        }

        List<DefinitionIdentity> expectedOrder = new ArrayList<>();
        for (ResultRecordMetric metric : ResultRecordMetric.values()) {
            expectedOrder.add(new DefinitionIdentity(metric, RecordScopeType.PERSONAL));
            expectedOrder.add(new DefinitionIdentity(metric, RecordScopeType.SERVER_INDIVIDUAL));
        }
        if (!actualOrder.equals(expectedOrder)) {
            throw new IllegalArgumentException("evaluations must use deterministic metric and scope order");
        }
    }

    public Optional<ResultRecordEvaluation> find(ResultRecordMetric metric, RecordScopeType scopeType) {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(scopeType, "scopeType");
        return evaluations.stream()
                .filter(evaluation -> evaluation.definition().metric() == metric)
                .filter(evaluation -> evaluation.scope().type() == scopeType)
                .findFirst();
    }

    public List<ResultRecordEvaluation> publicAnnouncements() {
        return evaluations.stream().filter(ResultRecordEvaluation::publicAnnouncementEligible).toList();
    }

    private record DefinitionIdentity(ResultRecordMetric metric, RecordScopeType scopeType) {
    }
}
