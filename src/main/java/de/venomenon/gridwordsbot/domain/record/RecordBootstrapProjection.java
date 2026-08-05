package de.venomenon.gridwordsbot.domain.record;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure target projection of the complete canonical history for one catalog version. */
public final class RecordBootstrapProjection {
    private final RecordDefinitionCatalog catalog;
    private final StreakRunAnalyzer streakAnalyzer;

    public RecordBootstrapProjection(RecordDefinitionCatalog catalog, StreakRunAnalyzer streakAnalyzer) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.streakAnalyzer = Objects.requireNonNull(streakAnalyzer, "streakAnalyzer");
    }

    public List<Candidate> project(
            long guildId,
            RecordHistorySnapshot history,
            StreakRunAnalysisWindow window) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(window, "window");
        List<Candidate> candidates = new ArrayList<>();
        for (RecordDefinition<?> definition : catalog.definitions()) {
            if (definition.metric() instanceof ResultRecordMetric metric) {
                if (definition.scopeType() == RecordScopeType.PERSONAL) {
                    history.results().stream()
                            .map(RecordHistorySnapshot.Result::playerId)
                            .distinct()
                            .sorted()
                            .forEach(player -> bestResult(
                                    definition,
                                    metric,
                                    history.results().stream()
                                            .filter(result -> result.playerId() == player)
                                            .toList())
                                    .ifPresent(result -> candidates.add(
                                            resultCandidate(guildId, definition, metric, result))));
                } else {
                    bestResult(definition, metric, history.results())
                            .ifPresent(result -> candidates.add(
                                    resultCandidate(guildId, definition, metric, result)));
                }
            }
        }

        List<StreakRun> runs = streakAnalyzer.analyze(
                history.results().stream().map(RecordHistorySnapshot.Result::streakResult).toList(),
                history.participationPeriods(),
                window).runs();
        for (RecordDefinition<?> definition : catalog.definitions()) {
            if (definition.metric() instanceof StreakRecordMetric metric) {
                if (definition.scopeType() == RecordScopeType.PERSONAL) {
                    runs.stream()
                            .filter(run -> run.identity().metric() == metric)
                            .filter(run -> run.identity().ownerScope() instanceof RecordScope.Personal)
                            .map(run -> ((RecordScope.Personal) run.identity().ownerScope()).playerId())
                            .distinct()
                            .sorted()
                            .forEach(player -> bestRun(
                                    definition,
                                    metric,
                                    runs.stream()
                                            .filter(run -> run.identity().ownerScope()
                                                    .equals(new RecordScope.Personal(player)))
                                            .toList())
                                    .ifPresent(run -> candidates.add(
                                            streakCandidate(guildId, definition, run))));
                } else {
                    bestRun(definition, metric, runs)
                            .ifPresent(run -> candidates.add(streakCandidate(guildId, definition, run)));
                }
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparing((Candidate candidate) -> candidate.key().definitionKey().value())
                        .thenComparing(candidate -> candidate.key().scopeKey()))
                .toList();
    }

    private Optional<RecordHistorySnapshot.Result> bestResult(
            RecordDefinition<?> definition,
            ResultRecordMetric metric,
            List<RecordHistorySnapshot.Result> results) {
        return results.stream()
                .filter(result -> result.outcome()
                        instanceof de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved)
                .filter(result -> definition.game().filter(game -> game == result.game()).isPresent())
                .min((left, right) -> compareResult(definition, metric, left, right));
    }

    private int compareResult(
            RecordDefinition<?> definition,
            ResultRecordMetric metric,
            RecordHistorySnapshot.Result left,
            RecordHistorySnapshot.Result right) {
        RecordComparison comparison = definition.compareValues(
                left.solvedObservation().valueFor(metric),
                right.solvedObservation().valueFor(metric));
        if (comparison == RecordComparison.BETTER) return -1;
        if (comparison == RecordComparison.WORSE) return 1;
        return Comparator.comparing(RecordHistorySnapshot.Result::gameDate)
                .thenComparing(RecordHistorySnapshot.Result::firstAcceptedAt)
                .thenComparingLong(RecordHistorySnapshot.Result::resultId)
                .compare(left, right);
    }

    private Candidate resultCandidate(
            long guildId,
            RecordDefinition<?> definition,
            ResultRecordMetric metric,
            RecordHistorySnapshot.Result result) {
        RecordScope scope = definition.scopeType() == RecordScopeType.PERSONAL
                ? new RecordScope.Personal(result.playerId())
                : new RecordScope.ServerIndividual();
        return new Candidate(
                new RecordStateKey(guildId, definition.key(), definition.definitionVersion(), scope),
                new RecordStateWrite(
                        Optional.of(result.playerId()),
                        result.solvedObservation().valueFor(metric),
                        result.solvedObservation().sourceReference(),
                        Optional.of(result.firstAcceptedAt()),
                        false));
    }

    private Optional<StreakRun> bestRun(
            RecordDefinition<?> definition,
            StreakRecordMetric metric,
            List<StreakRun> runs) {
        return runs.stream()
                .filter(run -> run.identity().metric() == metric)
                .filter(run -> switch (definition.scopeType()) {
                    case PERSONAL -> run.identity().ownerScope() instanceof RecordScope.Personal;
                    case SERVER_INDIVIDUAL -> run.identity().ownerScope() instanceof RecordScope.Personal;
                    case SHARED -> run.identity().ownerScope() instanceof RecordScope.Shared;
                })
                .min((left, right) -> {
                    RecordComparison comparison = definition.compareValues(left.value(), right.value());
                    if (comparison == RecordComparison.BETTER) return -1;
                    if (comparison == RecordComparison.WORSE) return 1;
                    return Comparator.comparing(StreakRun::endDate)
                            .thenComparing(run -> run.identity().startDate())
                            .thenComparing(run -> StreakRunAnalysis.scopeKey(run.identity().ownerScope()))
                            .compare(left, right);
                });
    }

    private Candidate streakCandidate(long guildId, RecordDefinition<?> definition, StreakRun run) {
        RecordScope scope = switch (definition.scopeType()) {
            case PERSONAL -> run.identity().ownerScope();
            case SERVER_INDIVIDUAL -> new RecordScope.ServerIndividual();
            case SHARED -> new RecordScope.Shared();
        };
        Optional<Long> holder = run.identity().ownerScope() instanceof RecordScope.Personal personal
                ? Optional.of(personal.playerId())
                : Optional.empty();
        return new Candidate(
                new RecordStateKey(guildId, definition.key(), definition.definitionVersion(), scope),
                new RecordStateWrite(
                        holder,
                        run.value(),
                        run.sourceReference(),
                        Optional.empty(),
                        run.status() == StreakRunStatus.RUNNING));
    }

    public record Candidate(RecordStateKey key, RecordStateWrite write) {
        public Candidate {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(write, "write");
        }
    }
}
