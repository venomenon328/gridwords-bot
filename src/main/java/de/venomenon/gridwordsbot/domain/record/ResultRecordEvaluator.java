package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Pure and deterministic evaluator for all result-record definitions of one solved candidate. */
public final class ResultRecordEvaluator {
    private static final Comparator<ResultRecordObservation> CANONICAL_FIRST_SOURCE = Comparator
            .comparing(ResultRecordObservation::gameDate)
            .thenComparing(ResultRecordObservation::firstAcceptedAt)
            .thenComparingLong(ResultRecordObservation::resultId);

    private final RecordDefinitionCatalog catalog;

    public ResultRecordEvaluator() {
        this(RecordDefinitionCatalog.recordsV1());
    }

    public ResultRecordEvaluator(RecordDefinitionCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        validateCatalog(catalog);
    }

    public ResultRecordEvaluationSet evaluate(
            ResultRecordObservation candidate,
            ResultRecordHistorySnapshot history,
            List<ResultRecordStateSnapshot> currentStates,
            RecordProcessingOrigin origin) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(currentStates, "currentStates");
        Objects.requireNonNull(origin, "origin");

        validateHistory(candidate, history);
        Map<StateIdentity, ResultRecordStateSnapshot> states = validateAndIndexStates(candidate, currentStates);
        List<ResultRecordEvaluation> evaluations = new ArrayList<>(ResultRecordMetric.values().length * 2);

        for (ResultRecordMetric metric : ResultRecordMetric.values()) {
            evaluations.add(evaluateDefinition(
                    definition(candidate.game(), metric, RecordScopeType.PERSONAL),
                    new RecordScope.Personal(candidate.playerId()),
                    candidate,
                    history.priorResults().stream()
                            .filter(result -> result.playerId() == candidate.playerId())
                            .toList(),
                    states,
                    origin));
            evaluations.add(evaluateDefinition(
                    definition(candidate.game(), metric, RecordScopeType.SERVER_INDIVIDUAL),
                    new RecordScope.ServerIndividual(),
                    candidate,
                    history.priorResults(),
                    states,
                    origin));
        }
        return new ResultRecordEvaluationSet(candidate, origin, evaluations);
    }

    private ResultRecordEvaluation evaluateDefinition(
            RecordDefinition<?> definition,
            RecordScope scope,
            ResultRecordObservation candidate,
            List<ResultRecordObservation> priorScopeResults,
            Map<StateIdentity, ResultRecordStateSnapshot> states,
            RecordProcessingOrigin origin) {
        ResultRecordMetric metric = (ResultRecordMetric) definition.metric();
        Optional<ResultRecordStateSnapshot> current = Optional.ofNullable(
                states.get(new StateIdentity(definition.key(), scope)));

        if (current.isEmpty()) {
            ResultRecordObservation canonical = bestObservation(
                    definition, metric, Stream.concat(priorScopeResults.stream(), Stream.of(candidate)).toList());
            ResultRecordStateSnapshot initialized = state(definition, scope, canonical);
            return new ResultRecordEvaluation(
                    definition,
                    scope,
                    ResultRecordEvaluationAction.INITIALIZED,
                    Optional.empty(),
                    initialized,
                    false);
        }

        ResultRecordStateSnapshot currentState = current.orElseThrow();
        RecordComparison comparison = definition.compareValues(
                candidate.valueFor(metric), currentState.valueFor(metric));
        if (comparison != RecordComparison.BETTER) {
            return new ResultRecordEvaluation(
                    definition,
                    scope,
                    ResultRecordEvaluationAction.UNCHANGED,
                    current,
                    currentState,
                    false);
        }

        ResultRecordStateSnapshot improved = state(definition, scope, candidate);
        boolean announcementEligible = origin.publicAnnouncementEligible()
                && minimumBasisMet(definition, priorScopeResults);
        return new ResultRecordEvaluation(
                definition,
                scope,
                ResultRecordEvaluationAction.IMPROVED,
                current,
                improved,
                announcementEligible);
    }

    private static ResultRecordObservation bestObservation(
            RecordDefinition<?> definition,
            ResultRecordMetric metric,
            List<ResultRecordObservation> observations) {
        if (observations.isEmpty()) {
            throw new IllegalArgumentException("at least one observation is required to initialize a state");
        }
        return observations.stream().min((left, right) -> {
            RecordComparison comparison = definition.compareValues(
                    left.valueFor(metric), right.valueFor(metric));
            return switch (comparison) {
                case BETTER -> -1;
                case WORSE -> 1;
                case EQUAL -> CANONICAL_FIRST_SOURCE.compare(left, right);
            };
        }).orElseThrow();
    }

    private static boolean minimumBasisMet(
            RecordDefinition<?> definition, List<ResultRecordObservation> priorScopeResults) {
        if (!(definition.announcementThreshold() instanceof RecordAnnouncementThreshold.Result threshold)) {
            throw new IllegalArgumentException("result definition does not use a result announcement threshold");
        }
        long distinctPlayers = priorScopeResults.stream()
                .map(ResultRecordObservation::playerId)
                .distinct()
                .count();
        return priorScopeResults.size() >= threshold.minimumPriorSolvedResults()
                && distinctPlayers >= threshold.minimumPriorDistinctPlayers();
    }

    private static ResultRecordStateSnapshot state(
            RecordDefinition<?> definition, RecordScope scope, ResultRecordObservation source) {
        return new ResultRecordStateSnapshot(
                definition.key(), definition.definitionVersion(), scope, source);
    }

    private void validateHistory(
            ResultRecordObservation candidate, ResultRecordHistorySnapshot history) {
        for (ResultRecordObservation prior : history.priorResults()) {
            if (prior.resultId() == candidate.resultId()) {
                throw new IllegalArgumentException("candidate must not be part of the prior history snapshot");
            }
            if (prior.game() != candidate.game()) {
                throw new IllegalArgumentException("prior history result game does not match candidate game");
            }
        }
    }

    private Map<StateIdentity, ResultRecordStateSnapshot> validateAndIndexStates(
            ResultRecordObservation candidate, List<ResultRecordStateSnapshot> currentStates) {
        Map<StateIdentity, ResultRecordStateSnapshot> indexed = new HashMap<>();
        for (ResultRecordStateSnapshot state : List.copyOf(currentStates)) {
            Objects.requireNonNull(state, "currentStates must not contain null");
            RecordDefinition<?> definition = catalog.find(state.definitionKey())
                    .orElseThrow(() -> new IllegalArgumentException("current state uses an unknown definition"));
            if (!catalog.version().equals(state.definitionVersion())
                    || !definition.definitionVersion().equals(state.definitionVersion())) {
                throw new IllegalArgumentException("current state uses the wrong definition version");
            }
            if (!(definition.metric() instanceof ResultRecordMetric metric)) {
                throw new IllegalArgumentException("current state does not use a result-record definition");
            }
            if (!definition.game().filter(game -> game == candidate.game()).isPresent()) {
                throw new IllegalArgumentException("current state definition game does not match candidate game");
            }
            if (definition.scopeType() != state.scope().type()) {
                throw new IllegalArgumentException("current state scope does not match its definition");
            }
            if (state.scope() instanceof RecordScope.Personal personal
                    && personal.playerId() != candidate.playerId()) {
                throw new IllegalArgumentException("personal current state does not belong to candidate player");
            }
            if (state.source().game() != candidate.game()) {
                throw new IllegalArgumentException("current state source game does not match candidate game");
            }
            if (!(definition.sourceEligibility() instanceof RecordSourceEligibility.SolvedGameResult eligibility)
                    || !eligibility.accepts(state.source().game(), state.source().outcome())) {
                throw new IllegalArgumentException("current state source is not eligible for its definition");
            }
            if (state.valueFor(metric).kind() != definition.valueKind()) {
                throw new IllegalArgumentException("current state value kind does not match its definition");
            }

            StateIdentity identity = new StateIdentity(state.definitionKey(), state.scope());
            if (indexed.putIfAbsent(identity, state) != null) {
                throw new IllegalArgumentException("current states contain a duplicate definition and scope");
            }
        }
        return Map.copyOf(indexed);
    }

    private RecordDefinition<?> definition(
            GameType game, ResultRecordMetric metric, RecordScopeType scopeType) {
        List<RecordDefinition<?>> matching = catalog.definitions().stream()
                .filter(definition -> definition.metric() == metric)
                .filter(definition -> definition.game().filter(candidate -> candidate == game).isPresent())
                .filter(definition -> definition.scopeType() == scopeType)
                .toList();
        if (matching.size() != 1) {
            throw new IllegalArgumentException("catalog must contain exactly one matching result definition");
        }
        return matching.getFirst();
    }

    private static void validateCatalog(RecordDefinitionCatalog catalog) {
        if (!RecordDefinitionVersion.RECORDS_V1.equals(catalog.version())
                && !RecordDefinitionVersion.RECORDS_V2.equals(catalog.version())) {
            throw new IllegalArgumentException("result evaluator requires a supported complete record catalog");
        }
        Set<RecordDefinitionKey> resultKeys = catalog.definitions().stream()
                .filter(definition -> definition.metric() instanceof ResultRecordMetric)
                .map(RecordDefinition::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (resultKeys.size() != GameType.values().length * ResultRecordMetric.values().length * 2) {
            throw new IllegalArgumentException("catalog does not contain the complete result definitions");
        }
        for (GameType game : GameType.values()) {
            for (ResultRecordMetric metric : ResultRecordMetric.values()) {
                for (RecordScopeType scopeType : List.of(
                        RecordScopeType.PERSONAL, RecordScopeType.SERVER_INDIVIDUAL)) {
                    long count = catalog.definitions().stream()
                            .filter(definition -> definition.metric() == metric)
                            .filter(definition -> definition.game().filter(candidate -> candidate == game).isPresent())
                            .filter(definition -> definition.scopeType() == scopeType)
                            .count();
                    if (count != 1) {
                        throw new IllegalArgumentException(
                                "catalog contains a missing or duplicate result definition");
                    }
                }
            }
        }
    }

    private record StateIdentity(RecordDefinitionKey definitionKey, RecordScope scope) {
        private StateIdentity {
            Objects.requireNonNull(definitionKey, "definitionKey");
            Objects.requireNonNull(scope, "scope");
        }
    }
}
