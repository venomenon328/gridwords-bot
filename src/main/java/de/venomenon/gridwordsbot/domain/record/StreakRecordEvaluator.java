package de.venomenon.gridwordsbot.domain.record;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Pure live-crossing and completion classifier for derived streak runs. */
public final class StreakRecordEvaluator {
    private final RecordDefinitionCatalog catalog;

    public StreakRecordEvaluator() {
        this(RecordDefinitionCatalog.recordsV1());
    }

    public StreakRecordEvaluator(RecordDefinitionCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        if (!RecordDefinitionVersion.RECORDS_V1.equals(catalog.version())
                && !RecordDefinitionVersion.RECORDS_V2.equals(catalog.version())) {
            throw new IllegalArgumentException("streak evaluator requires a supported complete record catalog");
        }
    }

    public StreakRecordEvaluationSet evaluate(StreakRun candidate, StreakRecordHistorySnapshot history,
            Set<StreakCrossingKey> consumedCrossings, RecordProcessingOrigin origin) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(history, "history");
        Set<StreakCrossingKey> consumed = Set.copyOf(Objects.requireNonNull(consumedCrossings, "consumedCrossings"));
        Objects.requireNonNull(origin, "origin");
        List<StreakRecordEvaluation> evaluations = applicableDefinitions(candidate).stream()
                .map(definition -> evaluateDefinition(definition, candidate, history, consumed, origin))
                .toList();
        return new StreakRecordEvaluationSet(candidate, evaluations);
    }

    @SuppressWarnings("unchecked")
    private List<RecordDefinition<StreakRecordValue>> applicableDefinitions(StreakRun candidate) {
        boolean personalOwner = candidate.identity().ownerScope() instanceof RecordScope.Personal;
        return catalog.definitions().stream()
                .filter(definition -> definition.metric() == candidate.identity().metric())
                .filter(definition -> personalOwner
                        ? definition.scopeType() == RecordScopeType.PERSONAL
                                || definition.scopeType() == RecordScopeType.SERVER_INDIVIDUAL
                        : definition.scopeType() == RecordScopeType.SHARED)
                .map(definition -> (RecordDefinition<StreakRecordValue>) definition)
                .toList();
    }

    private StreakRecordEvaluation evaluateDefinition(RecordDefinition<StreakRecordValue> definition,
            StreakRun candidate, StreakRecordHistorySnapshot history, Set<StreakCrossingKey> consumed,
            RecordProcessingOrigin origin) {
        RecordScope comparisonScope = comparisonScope(definition, candidate);
        List<StreakRun> comparable = history.runs().stream()
                .filter(run -> !run.identity().equals(candidate.identity()))
                .filter(run -> run.identity().metric() == candidate.identity().metric())
                .filter(run -> comparableOwner(definition.scopeType(), candidate, run))
                .toList();
        Optional<StreakRun> reference = best(definition, comparable);
        StreakRecordPhase phase = candidate.completed() ? StreakRecordPhase.COMPLETION : StreakRecordPhase.LIVE;
        StreakRecordClassification classification = classify(definition, candidate, reference, consumed);
        boolean publicEligible = publicEligible(definition, candidate, reference, comparable, classification, origin);
        OptionalInt gap = classification == StreakRecordClassification.NEAR_MISS
                ? OptionalInt.of(reference.orElseThrow().length() - candidate.length()) : OptionalInt.empty();
        return new StreakRecordEvaluation(definition, comparisonScope, candidate, reference, phase,
                classification, publicEligible, gap);
    }

    private StreakRecordClassification classify(RecordDefinition<StreakRecordValue> definition,
            StreakRun candidate, Optional<StreakRun> reference, Set<StreakCrossingKey> consumed) {
        if (reference.isEmpty()) return StreakRecordClassification.NONE;
        RecordComparison comparison = definition.compare(candidate.value(), reference.orElseThrow().value());
        if (!candidate.completed()) {
            StreakCrossingKey key = new StreakCrossingKey(definition.definitionVersion(), definition.key(),
                    candidate.identity());
            return comparison == RecordComparison.BETTER && !consumed.contains(key)
                    ? StreakRecordClassification.CROSSED : StreakRecordClassification.NONE;
        }
        return switch (comparison) {
            case BETTER -> StreakRecordClassification.NEW_RECORD;
            case EQUAL -> StreakRecordClassification.TIED;
            case WORSE -> RecordNearMissPolicy.isNearMiss(reference.orElseThrow().length(), candidate.length())
                    ? StreakRecordClassification.NEAR_MISS : StreakRecordClassification.NONE;
        };
    }

    private boolean publicEligible(RecordDefinition<StreakRecordValue> definition, StreakRun candidate,
            Optional<StreakRun> reference, List<StreakRun> comparable,
            StreakRecordClassification classification, RecordProcessingOrigin origin) {
        if (classification == StreakRecordClassification.NONE || !origin.publicAnnouncementEligible()) return false;
        RecordAnnouncementThreshold.Streak threshold = (RecordAnnouncementThreshold.Streak)
                definition.announcementThreshold();
        List<StreakRun> completed = comparable.stream().filter(StreakRun::completed).toList();
        if (completed.size() < threshold.minimumPriorCompletedRuns()) return false;
        long distinctPlayers = completed.stream().flatMap(run -> switch (run.identity().ownerScope()) {
            case RecordScope.Personal personal -> java.util.stream.Stream.of(personal.playerId());
            case RecordScope.Shared ignored -> java.util.stream.Stream.empty();
            case RecordScope.ServerIndividual ignored -> throw new IllegalStateException("invalid run owner");
        }).distinct().count();
        if (distinctPlayers < threshold.minimumPriorDistinctPlayers()) return false;
        if (classification != StreakRecordClassification.NEAR_MISS) {
            return candidate.length() >= threshold.minimumLength();
        }
        if (reference.orElseThrow().length() < threshold.minimumLength()) return false;
        return !candidate.identity().metric().drought() || candidate.length() >= threshold.minimumLength();
    }

    private Optional<StreakRun> best(RecordDefinition<StreakRecordValue> definition, List<StreakRun> runs) {
        Comparator<StreakRun> canonicalOrder = Comparator.comparing(StreakRun::endDate)
                .thenComparing(run -> run.identity().startDate())
                .thenComparing(run -> StreakRunAnalysis.scopeKey(run.identity().ownerScope()));
        StreakRun best = null;
        for (StreakRun run : runs) {
            if (best == null) {
                best = run;
            } else {
                RecordComparison comparison = definition.compare(run.value(), best.value());
                if (comparison == RecordComparison.BETTER
                        || comparison == RecordComparison.EQUAL && canonicalOrder.compare(run, best) < 0) {
                    best = run;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static RecordScope comparisonScope(RecordDefinition<?> definition, StreakRun candidate) {
        return switch (definition.scopeType()) {
            case PERSONAL -> candidate.identity().ownerScope();
            case SERVER_INDIVIDUAL -> new RecordScope.ServerIndividual();
            case SHARED -> new RecordScope.Shared();
        };
    }

    private static boolean comparableOwner(RecordScopeType type, StreakRun candidate, StreakRun run) {
        return switch (type) {
            case PERSONAL -> run.identity().ownerScope().equals(candidate.identity().ownerScope());
            case SERVER_INDIVIDUAL -> run.identity().ownerScope() instanceof RecordScope.Personal;
            case SHARED -> run.identity().ownerScope() instanceof RecordScope.Shared;
        };
    }
}
