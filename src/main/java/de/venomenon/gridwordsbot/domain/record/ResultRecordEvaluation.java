package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** The complete transport-neutral evaluation of one result-record definition. */
public record ResultRecordEvaluation(
        RecordDefinition<?> definition,
        RecordScope scope,
        ResultRecordEvaluationAction action,
        Optional<ResultRecordStateSnapshot> previousState,
        ResultRecordStateSnapshot resultingState,
        boolean publicAnnouncementEligible) {

    public ResultRecordEvaluation {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(action, "action");
        previousState = Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(resultingState, "resultingState");
        if (!(definition.metric() instanceof ResultRecordMetric metric)) {
            throw new IllegalArgumentException("result evaluation requires a result-record definition");
        }
        validateState(definition, scope, resultingState, metric, "resultingState");
        previousState.ifPresent(state -> validateState(definition, scope, state, metric, "previousState"));

        switch (action) {
            case INITIALIZED -> {
                if (previousState.isPresent()) {
                    throw new IllegalArgumentException("initialized evaluation must not have a previous state");
                }
                if (publicAnnouncementEligible) {
                    throw new IllegalArgumentException("initialization must remain publicly silent");
                }
            }
            case IMPROVED -> {
                ResultRecordStateSnapshot previous = previousState.orElseThrow(() ->
                        new IllegalArgumentException("improved evaluation requires a previous state"));
                if (definition.compareValues(resultingState.valueFor(metric), previous.valueFor(metric))
                        != RecordComparison.BETTER) {
                    throw new IllegalArgumentException("improved evaluation must contain a strictly better state");
                }
            }
            case UNCHANGED -> {
                ResultRecordStateSnapshot previous = previousState.orElseThrow(() ->
                        new IllegalArgumentException("unchanged evaluation requires a previous state"));
                if (!previous.equals(resultingState)) {
                    throw new IllegalArgumentException("unchanged evaluation must preserve the previous state");
                }
                if (publicAnnouncementEligible) {
                    throw new IllegalArgumentException("unchanged evaluation must remain publicly silent");
                }
            }
        }
    }

    public Optional<RecordValue> previousValue() {
        ResultRecordMetric metric = (ResultRecordMetric) definition.metric();
        return previousState.map(state -> state.valueFor(metric));
    }

    public RecordValue resultingValue() {
        return resultingState.valueFor((ResultRecordMetric) definition.metric());
    }

    public OptionalLong previousHolderPlayerId() {
        return previousState.isPresent()
                ? OptionalLong.of(previousState.orElseThrow().holderPlayerId())
                : OptionalLong.empty();
    }

    public long resultingHolderPlayerId() {
        return resultingState.holderPlayerId();
    }

    public Optional<RecordSourceReference.GameResult> previousSourceReference() {
        return previousState.map(ResultRecordStateSnapshot::sourceReference);
    }

    public RecordSourceReference.GameResult resultingSourceReference() {
        return resultingState.sourceReference();
    }

    private static void validateState(
            RecordDefinition<?> definition,
            RecordScope scope,
            ResultRecordStateSnapshot state,
            ResultRecordMetric metric,
            String name) {
        if (!definition.key().equals(state.definitionKey())) {
            throw new IllegalArgumentException(name + " definition key does not match evaluation definition");
        }
        if (!definition.definitionVersion().equals(state.definitionVersion())) {
            throw new IllegalArgumentException(name + " definition version does not match evaluation definition");
        }
        if (!scope.equals(state.scope()) || scope.type() != definition.scopeType()) {
            throw new IllegalArgumentException(name + " scope does not match evaluation definition");
        }
        if (!definition.game().filter(game -> game == state.source().game()).isPresent()) {
            throw new IllegalArgumentException(name + " source game does not match evaluation definition");
        }
        if (!(definition.sourceEligibility() instanceof RecordSourceEligibility.SolvedGameResult eligibility)
                || !eligibility.accepts(state.source().game(), state.source().outcome())) {
            throw new IllegalArgumentException(name + " source is not eligible for evaluation definition");
        }
        RecordValue value = state.valueFor(metric);
        if (value.kind() != definition.valueKind()) {
            throw new IllegalArgumentException(name + " value kind does not match evaluation definition");
        }
    }
}
