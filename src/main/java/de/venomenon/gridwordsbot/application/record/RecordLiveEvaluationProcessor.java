package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementPhase;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSubject;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapProjection;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordEventValidity;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.StreakRecordMetric;
import de.venomenon.gridwordsbot.domain.record.ResultRecordEvaluation;
import de.venomenon.gridwordsbot.domain.record.ResultRecordEvaluator;
import de.venomenon.gridwordsbot.domain.record.ResultRecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.ResultRecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.StreakRecordClassification;
import de.venomenon.gridwordsbot.domain.record.StreakRecordEvaluator;
import de.venomenon.gridwordsbot.domain.record.StreakRecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.StreakRun;
import de.venomenon.gridwordsbot.domain.record.StreakRunAnalysis;
import de.venomenon.gridwordsbot.domain.record.StreakRunAnalysisWindow;
import de.venomenon.gridwordsbot.domain.record.StreakRunAnalyzer;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordLiveEvaluationStore;
import de.venomenon.gridwordsbot.port.out.RecordLiveHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordPermanentFailure;
import de.venomenon.gridwordsbot.port.out.RecordRetryableFailure;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Processes exactly one claimed, versioned live evaluation.  Claim polling,
 * retry policy and all transport delivery deliberately remain outside this
 * application service.
 */
public final class RecordLiveEvaluationProcessor {
    private static final String RENDERER_VERSION = RecordAnnouncementRenderer.VERSION;
    private final RecordLiveEvaluationStore work;
    private final RecordLiveHistoryQuery history;
    private final RecordBootstrapReadService bootstrap;
    private final RecordStateService states;
    private final RecordEventStore events;
    private final RecordAnnouncementStore announcements;
    private final RecordTransactionRunner transactions;
    private final RecordDefinitionCatalog catalog;
    private final Clock clock;
    private final long channelId;
    private final ResultRecordEvaluator resultEvaluator;
    private final StreakRecordEvaluator streakEvaluator;
    private final StreakRunAnalyzer streakAnalyzer;

    public RecordLiveEvaluationProcessor(
            RecordLiveEvaluationStore work,
            RecordLiveHistoryQuery history,
            RecordBootstrapReadService bootstrap,
            RecordStateService states,
            RecordEventStore events,
            RecordAnnouncementStore announcements,
            RecordTransactionRunner transactions,
            RecordDefinitionCatalog catalog,
            Clock clock,
            long channelId) {
        this.work = java.util.Objects.requireNonNull(work);
        this.history = java.util.Objects.requireNonNull(history);
        this.bootstrap = java.util.Objects.requireNonNull(bootstrap);
        this.states = java.util.Objects.requireNonNull(states);
        this.events = java.util.Objects.requireNonNull(events);
        this.announcements = java.util.Objects.requireNonNull(announcements);
        this.transactions = java.util.Objects.requireNonNull(transactions);
        this.catalog = java.util.Objects.requireNonNull(catalog);
        this.clock = java.util.Objects.requireNonNull(clock);
        if (channelId <= 0) throw new IllegalArgumentException("channelId must be positive");
        this.channelId = channelId;
        this.resultEvaluator = new ResultRecordEvaluator(catalog);
        this.streakEvaluator = new StreakRecordEvaluator(catalog);
        this.streakAnalyzer = new StreakRunAnalyzer();
    }

    public ProcessingResult process(RecordLiveEvaluationClaim claim) {
        return process(claim, () -> true);
    }

    /**
     * Executes one claim while the runtime coordinator supplies the current
     * heartbeat ownership. A lost heartbeat fences the processor before it
     * can enter another record write transaction.
     */
    public ProcessingResult process(RecordLiveEvaluationClaim claim, BooleanSupplier leaseOwned) {
        java.util.Objects.requireNonNull(claim, "claim");
        java.util.Objects.requireNonNull(leaseOwned, "leaseOwned");
        for (int attempt = 0; attempt < 3; attempt++) {
            if (!leaseOwned.getAsBoolean()) return ProcessingResult.FENCED_OUT;
            // Canonical reads, state-generation reads and pure projection stay
            // outside the short write transaction.
            RecordHistorySnapshot canonical = history.loadFor(claim.key(), claim.processingOrigin());
            if (!leaseOwned.getAsBoolean()) return ProcessingResult.FENCED_OUT;
            RecordHistorySnapshot.Result result = canonical.results().stream()
                    .filter(candidate -> candidate.resultId() == claim.key().gameResultId())
                    .filter(candidate -> candidate.resultVersion() == claim.key().gameResultVersion())
                    .findFirst()
                    .orElseThrow(() -> new RecordPermanentFailure(
                            "claimed result is absent from canonical history", null));
            EvaluationPlan plan = evaluate(claim, canonical, result,
                    states.states(claim.key().guildId(), catalog.version()));
            if (!leaseOwned.getAsBoolean()) return ProcessingResult.FENCED_OUT;
            try {
                return transactions.inTransaction(
                        () -> processWithinTransaction(claim, canonical, result, plan, leaseOwned));
            } catch (LostLeaseDuringCompletion ignored) {
                // The write transaction rolled back. A stale token must not
                // leave partially reconciled state, event or intent writes.
                return ProcessingResult.FENCED_OUT;
            } catch (StaleCorrectionPlan ignored) {
                // The surrounding transaction has rolled back.  A fresh
                // canonical read and pure projection are required; the old
                // exact target is never retried against a newer generation.
            }
        }
        throw new RecordRetryableFailure(
                "record correction replan retries exhausted after three canonical generation changes", null);
    }

    private ProcessingResult processWithinTransaction(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot canonical,
            RecordHistorySnapshot.Result result,
            EvaluationPlan plan,
            BooleanSupplier leaseOwned) {
        if (!leaseOwned.getAsBoolean()) return ProcessingResult.FENCED_OUT;
        Instant now = clock.instant();
        if (!work.fence(claim.key(), claim.token(), now)) return ProcessingResult.FENCED_OUT;
        if (!leaseOwned.getAsBoolean()) return ProcessingResult.FENCED_OUT;
        if (requiresExactReconciliation(claim.processingOrigin())
                && !history.isCurrent(claim.key(), claim.processingOrigin(), canonical)) {
            throw new StaleCorrectionPlan();
        }
        boolean ready = bootstrap.readiness(new RecordBootstrapKey(
                claim.key().guildId(), RecordDefinitionVersion.RECORDS_V1)) == RecordBootstrapReadiness.READY;

        Map<de.venomenon.gridwordsbot.domain.record.RecordStateKey, RecordStateService.StateTransition> transitions =
                reconcileStates(claim, result, plan);
        List<RecordEventSnapshot> invalidated;
        List<AppendedFact> appended = new ArrayList<>();
        if (requiresExactReconciliation(claim.processingOrigin())) {
            EventReconciliation reconciliation = reconcileExactFacts(claim, result, plan, transitions, ready, now);
            invalidated = reconciliation.invalidated();
            appended.addAll(reconciliation.appended());
        } else {
            invalidated = invalidatePriorResultFacts(claim, now);
            if (result.outcome() instanceof de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved) {
                appended.addAll(appendResultFacts(claim, canonical, result, transitions, ready, now));
            }
            appended.addAll(appendImmediateStreakFacts(claim, result, plan.analysis(), transitions, ready, now));
        }
        reconcileAnnouncements(invalidated, appended,
                ready && claim.processingOrigin().publicAnnouncementEligible());

        if (!work.markSucceeded(claim.key(), claim.token(), now)) {
            throw new LostLeaseDuringCompletion();
        }
        return ProcessingResult.PROCESSED;
    }

    private List<RecordEventSnapshot> invalidatePriorResultFacts(
            RecordLiveEvaluationClaim claim,
            Instant now) {
        List<RecordEventSnapshot> invalidated = new ArrayList<>();
        for (RecordEventSnapshot event : events.findByResultId(
                claim.key().guildId(), claim.key().gameResultId())) {
            boolean obsoleteResult = event.draft().newSource() instanceof RecordSourceReference.GameResult source
                    && source.resultVersion() != claim.key().gameResultVersion();
            if (event.validity() == RecordEventValidity.VALID && obsoleteResult) {
                if (events.invalidate(event.draft().eventId(), now)) invalidated.add(event);
            }
        }
        return invalidated;
    }

    private EventReconciliation reconcileExactFacts(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot.Result changedResult,
            EvaluationPlan plan,
            Map<RecordStateKey, RecordStateService.StateTransition> transitions,
            boolean ready,
            Instant now) {
        List<RecordStateKey> resultFamilies = plan.affectedStateKeys().stream()
                .filter(key -> catalog.find(key.definitionKey()).orElseThrow().metric()
                        instanceof de.venomenon.gridwordsbot.domain.record.ResultRecordMetric)
                .toList();
        List<RecordStateKey> streakFamilies = plan.affectedStateKeys().stream()
                .filter(key -> catalog.find(key.definitionKey()).orElseThrow().metric() instanceof StreakRecordMetric)
                .toList();
        List<RecordEventSnapshot> existing = java.util.stream.Stream.concat(
                        events.findResultFamily(claim.key().guildId(), resultFamilies, changedResult.gameDate()).stream(),
                        events.findStreakFamily(claim.key().guildId(), streakFamilies, changedResult.gameDate()).stream())
                .filter(event -> event.draft().type() != RecordEventType.RECORD_INITIALIZED)
                .toList();
        List<PlannedFact> desired = plan.desiredFacts().stream()
                .filter(fact -> transitions.containsKey(fact.stateKey()))
                .toList();
        java.util.Set<PlannedFact> matched = new java.util.HashSet<>();
        List<RecordEventSnapshot> invalidated = new ArrayList<>();
        for (RecordEventSnapshot event : existing) {
            PlannedFact same = desired.stream()
                    .filter(candidate -> !matched.contains(candidate))
                    .filter(candidate -> candidate.sameFact(event.draft()))
                    .findFirst().orElse(null);
            if (same != null && event.validity() == RecordEventValidity.VALID) {
                matched.add(same);
            } else if (event.validity() == RecordEventValidity.VALID
                    && events.invalidate(event.draft().eventId(), now)) {
                invalidated.add(event);
            }
        }

        List<AppendedFact> appended = new ArrayList<>();
        for (PlannedFact fact : desired) {
            if (matched.contains(fact)) continue;
            String idempotency = fact.idempotencyKey();
            UUID eventId = stableUuid(idempotency);
            Optional<RecordEventSnapshot> occupied = events.find(eventId);
            if (occupied.isPresent()) {
                idempotency = "correction:" + claim.key().gameResultId() + ":"
                        + claim.key().gameResultVersion() + ":" + fact.idempotencyKey();
                eventId = stableUuid(idempotency);
            }
            RecordEventDraft draft = fact.toDraft(eventId, idempotency, claim.processingOrigin(), now);
            RecordEventSnapshot snapshot = events.append(draft).snapshot();
            appended.add(new AppendedFact(snapshot,
                    ready && fact.announcementEligible() && claim.processingOrigin().publicAnnouncementEligible()));
        }
        return new EventReconciliation(invalidated, appended);
    }

    private List<AppendedFact> appendResultFacts(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot canonical,
            RecordHistorySnapshot.Result result,
            Map<de.venomenon.gridwordsbot.domain.record.RecordStateKey, RecordStateService.StateTransition> transitions,
            boolean ready,
            Instant now) {
        List<de.venomenon.gridwordsbot.domain.record.ResultRecordObservation> prior = canonical.results().stream()
                .filter(item -> item.game() == result.game())
                .filter(item -> item.resultId() != result.resultId())
                .filter(item -> item.outcome() instanceof de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved)
                .map(RecordHistorySnapshot.Result::solvedObservation).toList();
        List<AppendedFact> appended = new ArrayList<>();
        for (var entry : transitions.entrySet()) {
            RecordStateService.StateTransition transition = entry.getValue();
            if (!transition.changed() || transition.after().isEmpty()
                    || !(transition.after().orElseThrow().source() instanceof RecordSourceReference.GameResult source)
                    || source.resultId() != result.resultId() || source.resultVersion() != result.resultVersion()) {
                continue;
            }
            var stateKey = entry.getKey();
            if (!(catalog.find(stateKey.definitionKey()).orElseThrow().metric()
                    instanceof de.venomenon.gridwordsbot.domain.record.ResultRecordMetric)) continue;
            List<ResultRecordStateSnapshot> before = transition.before()
                    .filter(state -> state.source() instanceof RecordSourceReference.GameResult)
                    .map(this::resultState).stream().toList();
            ResultRecordEvaluation evaluation = resultEvaluator.evaluate(
                    result.solvedObservation(), new ResultRecordHistorySnapshot(prior), before, claim.processingOrigin())
                    .evaluations().stream()
                    .filter(candidate -> candidate.definition().key().equals(stateKey.definitionKey()))
                    .filter(candidate -> candidate.scope().equals(stateKey.scope()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("missing result evaluation for state transition"));
            if (evaluation.action() == de.venomenon.gridwordsbot.domain.record.ResultRecordEvaluationAction.UNCHANGED) continue;
            String key = "result:" + claim.key().gameResultId() + ":" + claim.key().gameResultVersion()
                    + ":" + evaluation.definition().key().value() + ":" + scopeKey(evaluation.scope());
            RecordEventType type = evaluation.action() == de.venomenon.gridwordsbot.domain.record.ResultRecordEvaluationAction.INITIALIZED
                    ? RecordEventType.RECORD_INITIALIZED : RecordEventType.RESULT_RECORD_BROKEN;
            var beforeState = transition.before();
            var afterState = transition.after().orElseThrow();
            RecordEventSnapshot appendedEvent = events.append(new RecordEventDraft(
                    stableUuid(key), key, stateKey, type,
                    beforeState.map(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot::value), afterState.value(),
                    beforeState.flatMap(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot::holderPlayerId),
                    afterState.holderPlayerId(),
                    beforeState.map(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot::source),
                    afterState.source(), trigger(claim), claim.processingOrigin(), now)).snapshot();
            appended.add(new AppendedFact(appendedEvent, ready && evaluation.publicAnnouncementEligible()));
        }
        return appended;
    }

    private List<AppendedFact> appendImmediateStreakFacts(
            RecordLiveEvaluationClaim claim, RecordHistorySnapshot.Result result, StreakRunAnalysis analysis,
            Map<de.venomenon.gridwordsbot.domain.record.RecordStateKey, RecordStateService.StateTransition> transitions,
            boolean ready, Instant now) {
        List<AppendedFact> appended = new ArrayList<>();
        for (StreakRun run : analysis.runs()) {
            if (run.endDate().isBefore(result.gameDate()) || run.identity().startDate().isAfter(result.gameDate())) continue;
            if (run.identity().ownerScope() instanceof RecordScope.Personal personal && personal.playerId() != result.playerId()) continue;
            for (var evaluation : streakEvaluator.evaluate(run, new StreakRecordHistorySnapshot(analysis.runs()),
                    states.consumedCrossings(claim.key().guildId(), catalog.version()), claim.processingOrigin()).notable()) {
                var stateKey = new de.venomenon.gridwordsbot.domain.record.RecordStateKey(claim.key().guildId(),
                        evaluation.definition().key(), evaluation.definition().definitionVersion(), evaluation.comparisonScope());
                RecordStateService.StateTransition transition = transitions.get(stateKey);
                if (transition == null || !transition.changed() || transition.after().isEmpty()
                        || !transition.after().orElseThrow().source().equals(evaluation.candidate().sourceReference())) {
                    continue;
                }
                String key = "streak:" + claim.key().gameResultId() + ":" + claim.key().gameResultVersion() + ":"
                        + evaluation.definition().key().value() + ":" + evaluation.candidate().identity();
                RecordEventType type = switch (evaluation.classification()) {
                    case CROSSED -> RecordEventType.SERIES_RECORD_CROSSED;
                    case TIED -> RecordEventType.SERIES_RECORD_TIED_AT_END;
                    case NEAR_MISS -> RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END;
                    case NEW_RECORD -> RecordEventType.RECORD_SERIES_FINISHED;
                    case NONE -> throw new IllegalStateException("non-notable streak event");
                };
                var beforeState = transition.before();
                var afterState = transition.after().orElseThrow();
                RecordEventSnapshot appendedEvent = events.append(new RecordEventDraft(stableUuid(key), key,
                        stateKey, type,
                        beforeState.map(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot::value), afterState.value(),
                        beforeState.flatMap(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot::holderPlayerId),
                        afterState.holderPlayerId(),
                        beforeState.map(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot::source),
                        afterState.source(), trigger(claim), claim.processingOrigin(), now)).snapshot();
                appended.add(new AppendedFact(appendedEvent, ready && evaluation.publicAnnouncementEligible()));
            }
        }
        return appended;
    }

    private Map<de.venomenon.gridwordsbot.domain.record.RecordStateKey, RecordStateService.StateTransition> reconcileStates(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot.Result changedResult,
            EvaluationPlan plan) {
        List<RecordBootstrapProjection.Candidate> candidates = plan.candidates();
        Map<de.venomenon.gridwordsbot.domain.record.RecordStateKey, RecordStateService.StateTransition> transitions =
                new java.util.LinkedHashMap<>();
        if (!requiresExactReconciliation(claim.processingOrigin())) {
            for (RecordBootstrapProjection.Candidate candidate : candidates.stream()
                    .filter(candidate -> liveAffectedBy(candidate.key(), changedResult)).toList()) {
                RecordStateService.StateTransition transition =
                        states.applyLiveCandidateTransitionWithinTransaction(candidate);
                if (transition.result() == RecordStateService.RebuildResult.RETRY_EXHAUSTED) {
                    throw new IllegalStateException("record-state optimistic update retries exhausted");
                }
                transitions.put(candidate.key(), transition);
            }
            return Map.copyOf(transitions);
        }
        Map<de.venomenon.gridwordsbot.domain.record.RecordStateKey, RecordBootstrapProjection.Candidate> targets =
                candidates.stream().collect(java.util.stream.Collectors.toMap(RecordBootstrapProjection.Candidate::key,
                        candidate -> candidate));
        java.util.Set<de.venomenon.gridwordsbot.domain.record.RecordStateKey> keys = plan.affectedStateKeys();
        for (de.venomenon.gridwordsbot.domain.record.RecordStateKey key : keys) {
            RecordStateService.StateTransition transition = states.reconcileCorrectionTargetTransitionWithinTransaction(
                    Optional.ofNullable(targets.get(key)), key, plan.expectedStates().getOrDefault(key, Optional.empty()));
            if (transition.result() == RecordStateService.RebuildResult.STALE_PLAN) {
                throw new StaleCorrectionPlan();
            }
            if (transition.result() == RecordStateService.RebuildResult.RETRY_EXHAUSTED) {
                throw new IllegalStateException("record-state optimistic update retries exhausted");
            }
            transitions.put(key, transition);
        }
        return Map.copyOf(transitions);
    }

    private EvaluationPlan evaluate(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot history,
            RecordHistorySnapshot.Result changedResult,
            List<RecordStateSnapshot> currentStates) {
        StreakRunAnalysisWindow window = analysisWindow(history, changedResult, claim.processingOrigin());
        StreakRunAnalysis analysis = streakAnalyzer.analyze(
                history.results().stream().map(RecordHistorySnapshot.Result::streakResult).toList(),
                history.participationPeriods(), window);
        Set<RecordStateKey> affectedStateKeys = affectedStateKeys(claim.key().guildId(), changedResult);
        List<RecordBootstrapProjection.Candidate> candidates = new RecordBootstrapProjection(catalog, streakAnalyzer)
                .project(claim.key().guildId(), history, window).stream()
                .filter(candidate -> affectedStateKeys.contains(candidate.key())).toList();
        Map<RecordStateKey, Optional<RecordStateSnapshot>> expectedStates = affectedStateKeys.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(key -> key,
                        key -> currentStates.stream().filter(state -> state.key().equals(key)).findFirst()));
        List<PlannedFact> desiredFacts = requiresExactReconciliation(claim.processingOrigin())
                ? planDesiredFacts(claim, history, changedResult, analysis, affectedStateKeys)
                : List.of();
        return new EvaluationPlan(candidates, analysis, affectedStateKeys, expectedStates, desiredFacts);
    }

    private List<PlannedFact> planDesiredFacts(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot history,
            RecordHistorySnapshot.Result changedResult,
            StreakRunAnalysis analysis,
            Set<RecordStateKey> affectedKeys) {
        return java.util.stream.Stream.concat(
                        planResultFacts(claim, history, changedResult, affectedKeys).stream(),
                        planStreakFacts(claim, history, changedResult, analysis, affectedKeys).stream())
                .sorted(Comparator.comparing(PlannedFact::triggerKey)
                        .thenComparing(fact -> fact.stateKey().definitionKey().value())
                        .thenComparing(fact -> fact.stateKey().scopeKey())
                        .thenComparing(PlannedFact::idempotencyKey))
                .toList();
    }

    private List<PlannedFact> planResultFacts(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot history,
            RecordHistorySnapshot.Result changedResult,
            Set<RecordStateKey> affectedKeys) {
        List<de.venomenon.gridwordsbot.domain.record.ResultRecordObservation> prior = new ArrayList<>();
        Map<RecordStateKey, ResultRecordStateSnapshot> simulated = new java.util.LinkedHashMap<>();
        List<PlannedFact> facts = new ArrayList<>();
        for (RecordHistorySnapshot.Result item : history.results().stream()
                .filter(candidate -> candidate.game() == changedResult.game())
                .filter(candidate -> candidate.outcome()
                        instanceof de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved)
                .sorted(Comparator.comparing(RecordHistorySnapshot.Result::gameDate)
                        .thenComparing(RecordHistorySnapshot.Result::firstAcceptedAt)
                        .thenComparingLong(RecordHistorySnapshot.Result::resultId))
                .toList()) {
            var observation = item.solvedObservation();
            List<ResultRecordStateSnapshot> applicableStates = simulated.entrySet().stream()
                    .filter(entry -> entry.getKey().scope() instanceof RecordScope.ServerIndividual
                            || entry.getKey().scope() instanceof RecordScope.Personal personal
                                    && personal.playerId() == item.playerId())
                    .map(Map.Entry::getValue).toList();
            for (ResultRecordEvaluation evaluation : resultEvaluator.evaluate(
                    observation, new ResultRecordHistorySnapshot(prior), applicableStates,
                    claim.processingOrigin()).evaluations()) {
                RecordStateKey stateKey = new RecordStateKey(claim.key().guildId(), evaluation.definition().key(),
                        evaluation.definition().definitionVersion(), evaluation.scope());
                if (!affectedKeys.contains(stateKey)) continue;
                if (evaluation.action()
                        != de.venomenon.gridwordsbot.domain.record.ResultRecordEvaluationAction.UNCHANGED) {
                    simulated.put(stateKey, evaluation.resultingState());
                }
                if (evaluation.action()
                        != de.venomenon.gridwordsbot.domain.record.ResultRecordEvaluationAction.IMPROVED
                        || item.gameDate().isBefore(changedResult.gameDate())) {
                    continue;
                }
                RecordSourceReference.GameResult source = evaluation.resultingSourceReference();
                String key = "result:" + source.resultId() + ":" + source.resultVersion() + ":"
                        + evaluation.definition().key().value() + ":" + scopeKey(evaluation.scope());
                facts.add(new PlannedFact(key, stateKey, RecordEventType.RESULT_RECORD_BROKEN,
                        evaluation.previousValue(), evaluation.resultingValue(),
                        evaluation.previousState().map(ResultRecordStateSnapshot::holderPlayerId),
                        Optional.of(evaluation.resultingHolderPlayerId()),
                        evaluation.previousSourceReference().map(candidate -> (RecordSourceReference) candidate),
                        source, "live-result:" + source.resultId(), evaluation.publicAnnouncementEligible()));
            }
            prior.add(observation);
        }
        return facts;
    }

    private List<PlannedFact> planStreakFacts(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot history,
            RecordHistorySnapshot.Result changedResult,
            StreakRunAnalysis analysis,
            Set<RecordStateKey> affectedKeys) {
        List<PlannedFact> facts = new ArrayList<>();
        for (StreakRun run : analysis.runs()) {
            if (run.endDate().isBefore(changedResult.gameDate())) continue;
            for (var evaluation : streakEvaluator.evaluate(run,
                    new StreakRecordHistorySnapshot(analysis.runs()), Set.of(), claim.processingOrigin()).notable()) {
                RecordStateKey stateKey = new RecordStateKey(claim.key().guildId(), evaluation.definition().key(),
                        evaluation.definition().definitionVersion(), evaluation.comparisonScope());
                if (!affectedKeys.contains(stateKey)) continue;
                RecordHistorySnapshot.Result triggerResult = triggerResult(history, run).orElse(changedResult);
                RecordEventType type = switch (evaluation.classification()) {
                    case CROSSED -> RecordEventType.SERIES_RECORD_CROSSED;
                    case TIED -> RecordEventType.SERIES_RECORD_TIED_AT_END;
                    case NEAR_MISS -> RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END;
                    case NEW_RECORD -> RecordEventType.RECORD_SERIES_FINISHED;
                    case NONE -> throw new IllegalStateException("non-notable streak event");
                };
                String key = "streak:" + triggerResult.resultId() + ":" + triggerResult.resultVersion() + ":"
                        + evaluation.definition().key().value() + ":" + evaluation.candidate().identity();
                Optional<Long> holder = holder(evaluation.candidate().sourceReference());
                Optional<RecordSourceReference> previousSource = evaluation.reference()
                        .map(candidate -> (RecordSourceReference) candidate.sourceReference());
                facts.add(new PlannedFact(key, stateKey, type,
                        evaluation.reference().map(candidate -> (de.venomenon.gridwordsbot.domain.record.RecordValue)
                                candidate.value()), evaluation.candidate().value(),
                        evaluation.reference().flatMap(candidate -> holder(candidate.sourceReference())), holder,
                        previousSource, evaluation.candidate().sourceReference(),
                        "live-result:" + triggerResult.resultId(), evaluation.publicAnnouncementEligible()));
            }
        }
        return facts;
    }

    private static Optional<RecordHistorySnapshot.Result> triggerResult(
            RecordHistorySnapshot history, StreakRun run) {
        return history.results().stream()
                .filter(result -> result.gameDate().equals(run.endDate()))
                .filter(result -> switch (run.identity().ownerScope()) {
                    case RecordScope.Personal personal -> result.playerId() == personal.playerId();
                    case RecordScope.Shared ignored -> true;
                    case RecordScope.ServerIndividual ignored -> false;
                })
                .filter(result -> run.identity().metric().fixedGame()
                        .map(game -> result.game() == game).orElse(true))
                .max(Comparator.comparing(RecordHistorySnapshot.Result::firstAcceptedAt)
                        .thenComparingLong(RecordHistorySnapshot.Result::resultId));
    }

    private static Optional<Long> holder(RecordSourceReference.StreakRun source) {
        return switch (source.owner()) {
            case RecordSourceReference.StreakRunOwner.Player player -> Optional.of(player.playerId());
            case RecordSourceReference.StreakRunOwner.Shared ignored -> Optional.empty();
        };
    }

    private static StreakRunAnalysisWindow analysisWindow(
            RecordHistorySnapshot history,
            RecordHistorySnapshot.Result changedResult,
            RecordProcessingOrigin origin) {
        boolean exact = requiresExactReconciliation(origin);
        LocalDate first = history.participationPeriods().stream()
                .filter(period -> exact || period.playerId() == changedResult.playerId()
                        && period.contains(changedResult.gameDate()))
                .map(de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod::activeFrom)
                .min(Comparator.naturalOrder()).orElse(changedResult.gameDate());
        LocalDate asOf = changedResult.gameDate();
        if (exact) {
            asOf = history.results().stream().map(RecordHistorySnapshot.Result::gameDate)
                    .max(Comparator.naturalOrder()).orElse(changedResult.gameDate());
        }
        return new StreakRunAnalysisWindow(first, asOf, false);
    }

    private boolean affectedBy(
            de.venomenon.gridwordsbot.domain.record.RecordStateKey key,
            RecordHistorySnapshot.Result changedResult) {
        return catalog.find(key.definitionKey()).map(definition -> {
            if (definition.metric() instanceof de.venomenon.gridwordsbot.domain.record.ResultRecordMetric) {
                return definition.game().filter(game -> game == changedResult.game()).isPresent();
            }
            if (key.scope() instanceof RecordScope.Personal personal) {
                return personal.playerId() == changedResult.playerId();
            }
            return true;
        }).orElseThrow(() -> new IllegalStateException("unknown record-state definition"));
    }

    private boolean liveAffectedBy(
            de.venomenon.gridwordsbot.domain.record.RecordStateKey key,
            RecordHistorySnapshot.Result changedResult) {
        if (!affectedBy(key, changedResult)) return false;
        return !(key.scope() instanceof RecordScope.Personal personal) || personal.playerId() == changedResult.playerId();
    }

    private static boolean requiresExactReconciliation(RecordProcessingOrigin origin) {
        return origin == RecordProcessingOrigin.NORMAL_CORRECTION
                || origin == RecordProcessingOrigin.REPLAY
                || origin == RecordProcessingOrigin.IMPORT
                || origin == RecordProcessingOrigin.BACKFILL
                || origin == RecordProcessingOrigin.ADMINISTRATIVE_REPAIR;
    }

    private void reconcileAnnouncements(List<RecordEventSnapshot> invalidated, List<AppendedFact> appended, boolean ready) {
        if (!ready) return;
        for (RecordEventSnapshot event : invalidated) {
            for (RecordAnnouncementSnapshot announcement : announcements.findByEventId(event.draft().eventId())) {
                updateAnnouncement(announcement, announcement.registration().eventIds().stream()
                        .filter(id -> !id.equals(event.draft().eventId())).toList());
            }
        }
        appended.stream().filter(AppendedFact::announcementEligible).map(AppendedFact::snapshot)
                .filter(event -> event.validity() == RecordEventValidity.VALID)
                .collect(java.util.stream.Collectors.groupingBy(event -> new AnnouncementBucket(
                                event.draft().triggerKey(), phase(event.draft().type()), subject(event.draft())),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .forEach((bucket, facts) -> registerNewFacts(facts, bucket));
    }

    private void registerNewFacts(List<RecordEventSnapshot> facts, AnnouncementBucket bucket) {
        String subjectKey = bucket.subject().key();
        RecordAnnouncementKey key = new RecordAnnouncementKey(facts.getFirst().draft().stateKey().guildId(), channelId,
                bucket.triggerKey() + ":" + subjectKey + ":" + bucket.phase().name());
        RecordAnnouncementSnapshot existing = announcements.find(key).orElse(null);
        List<UUID> ids = java.util.stream.Stream.concat(
                        existing == null ? java.util.stream.Stream.empty() : existing.registration().eventIds().stream()
                                .filter(id -> events.find(id).map(snapshot -> snapshot.validity() == RecordEventValidity.VALID).orElse(false)),
                        facts.stream().map(event -> event.draft().eventId()))
                .distinct().sorted().toList();
        RecordAnnouncementProjection desired = desired(existing, ids);
        announcements.registerOrUpdate(new RecordAnnouncementRegistration(key, bucket.subject(), bucket.phase(),
                desired, RENDERER_VERSION, fingerprint(ids), ids));
    }

    private void updateAnnouncement(RecordAnnouncementSnapshot existing, List<UUID> remaining) {
        List<UUID> ids = remaining.stream().sorted().toList();
        RecordAnnouncementProjection desired = ids.isEmpty() ? RecordAnnouncementProjection.DELETE : desired(existing, ids);
        announcements.registerOrUpdate(new RecordAnnouncementRegistration(existing.registration().key(),
                existing.registration().subject(), existing.registration().phase(), desired, RENDERER_VERSION,
                fingerprint(ids), ids));
    }

    private static RecordAnnouncementProjection desired(RecordAnnouncementSnapshot existing, List<UUID> facts) {
        if (existing == null) return RecordAnnouncementProjection.CREATE;
        if (existing.registration().eventIds().equals(facts)
                && existing.registration().contentFingerprint().equals(fingerprint(facts))) return RecordAnnouncementProjection.NO_OP;
        return existing.publishedAt().isPresent() ? RecordAnnouncementProjection.EDIT : RecordAnnouncementProjection.CREATE;
    }

    private ResultRecordStateSnapshot resultState(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot state) {
        RecordSourceReference.GameResult source = (RecordSourceReference.GameResult) state.source();
        return new ResultRecordStateSnapshot(state.key().definitionKey(), state.key().definitionVersion(), state.key().scope(),
                new de.venomenon.gridwordsbot.domain.record.ResultRecordObservation(source.resultId(), source.resultVersion(),
                        source.playerId(), source.game(), source.gameDate(), state.sourceGameFirstAcceptedAt().orElseThrow(),
                        new de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved(
                                state.value() instanceof de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue value
                                        ? value.attempts() : 1,
                                source.game() == de.venomenon.gridwordsbot.domain.model.GameType.GRIDWORDS ? 6 : 9),
                        state.value() instanceof de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue value
                                ? value.duration()
                                : ((de.venomenon.gridwordsbot.domain.record.DurationRecordValue) state.value()).duration()));
    }

    private static String trigger(RecordLiveEvaluationClaim claim) { return "live-result:" + claim.key().gameResultId(); }
    private static String scopeKey(RecordScope scope) {
        return switch (scope) {
            case RecordScope.Personal personal -> "player:" + personal.playerId();
            case RecordScope.ServerIndividual ignored -> "server";
            case RecordScope.Shared ignored -> "shared";
        };
    }
    private static RecordAnnouncementPhase phase(RecordEventType type) {
        return switch (type) {
            case RESULT_RECORD_BROKEN -> RecordAnnouncementPhase.LIVE_EVALUATION;
            case SERIES_RECORD_CROSSED -> RecordAnnouncementPhase.STREAK_CROSSED;
            case SERIES_RECORD_TIED_AT_END, SERIES_RECORD_NEAR_MISSED_AT_END, RECORD_SERIES_FINISHED -> RecordAnnouncementPhase.STREAK_FINISHED;
            default -> throw new IllegalArgumentException("event cannot be announced: " + type);
        };
    }
    private static RecordAnnouncementSubject subject(RecordEventDraft event) {
        return event.newHolderPlayerId().map(RecordAnnouncementSubject::player)
                .orElseGet(RecordAnnouncementSubject::shared);
    }
    private static UUID stableUuid(String key) { return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)); }
    private static String fingerprint(List<UUID> ids) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(ids.stream().map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining("|")).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private record AnnouncementBucket(
            String triggerKey, RecordAnnouncementPhase phase, RecordAnnouncementSubject subject) { }
    private record AppendedFact(RecordEventSnapshot snapshot, boolean announcementEligible) { }
    private record EventReconciliation(
            List<RecordEventSnapshot> invalidated, List<AppendedFact> appended) {
        private EventReconciliation {
            invalidated = List.copyOf(invalidated);
            appended = List.copyOf(appended);
        }
    }
    private record PlannedFact(
            String idempotencyKey,
            RecordStateKey stateKey,
            RecordEventType type,
            Optional<de.venomenon.gridwordsbot.domain.record.RecordValue> previousValue,
            de.venomenon.gridwordsbot.domain.record.RecordValue newValue,
            Optional<Long> previousHolderPlayerId,
            Optional<Long> newHolderPlayerId,
            Optional<RecordSourceReference> previousSource,
            RecordSourceReference newSource,
            String triggerKey,
            boolean announcementEligible) {
        private PlannedFact {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("planned event idempotency key is invalid");
            }
            java.util.Objects.requireNonNull(stateKey, "stateKey");
            java.util.Objects.requireNonNull(type, "type");
            previousValue = java.util.Objects.requireNonNull(previousValue, "previousValue");
            java.util.Objects.requireNonNull(newValue, "newValue");
            previousHolderPlayerId = java.util.Objects.requireNonNull(
                    previousHolderPlayerId, "previousHolderPlayerId");
            newHolderPlayerId = java.util.Objects.requireNonNull(newHolderPlayerId, "newHolderPlayerId");
            previousSource = java.util.Objects.requireNonNull(previousSource, "previousSource");
            java.util.Objects.requireNonNull(newSource, "newSource");
            if (triggerKey == null || triggerKey.isBlank()) {
                throw new IllegalArgumentException("planned event trigger key is invalid");
            }
        }

        private boolean sameFact(RecordEventDraft draft) {
            return stateKey.equals(draft.stateKey()) && type == draft.type()
                    && previousValue.equals(draft.previousValue()) && newValue.equals(draft.newValue())
                    && previousHolderPlayerId.equals(draft.previousHolderPlayerId())
                    && newHolderPlayerId.equals(draft.newHolderPlayerId())
                    && previousSource.equals(draft.previousSource()) && newSource.equals(draft.newSource())
                    && triggerKey.equals(draft.triggerKey());
        }

        private RecordEventDraft toDraft(
                UUID eventId,
                String persistedIdempotencyKey,
                RecordProcessingOrigin origin,
                Instant detectedAt) {
            return new RecordEventDraft(eventId, persistedIdempotencyKey, stateKey, type,
                    previousValue, newValue, previousHolderPlayerId, newHolderPlayerId,
                    previousSource, newSource, triggerKey, origin, detectedAt);
        }
    }
    private Set<RecordStateKey> affectedStateKeys(long guildId, RecordHistorySnapshot.Result changedResult) {
        return catalog.definitions().stream()
                .filter(definition -> definition.metric() instanceof de.venomenon.gridwordsbot.domain.record.ResultRecordMetric
                        ? definition.game().filter(game -> game == changedResult.game()).isPresent()
                        : streakMetricAffected((StreakRecordMetric) definition.metric(), changedResult.game()))
                .map(definition -> new RecordStateKey(guildId, definition.key(), definition.definitionVersion(),
                        switch (definition.scopeType()) {
                            case PERSONAL -> new RecordScope.Personal(changedResult.playerId());
                            case SERVER_INDIVIDUAL -> new RecordScope.ServerIndividual();
                            case SHARED -> new RecordScope.Shared();
                        }))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static boolean streakMetricAffected(
            StreakRecordMetric metric, de.venomenon.gridwordsbot.domain.model.GameType game) {
        return switch (metric) {
            case ACTIVITY, COMPLETE, PERFECT, WITHOUT_PERFECT_DAY -> true;
            case GRIDWORDS_SOLVED, GRIDWORDS_DROUGHT -> game == de.venomenon.gridwordsbot.domain.model.GameType.GRIDWORDS;
            case QUADWORDS_SOLVED, QUADWORDS_DROUGHT -> game == de.venomenon.gridwordsbot.domain.model.GameType.QUADWORDS;
        };
    }

    private record EvaluationPlan(
            List<RecordBootstrapProjection.Candidate> candidates,
            StreakRunAnalysis analysis,
            Set<RecordStateKey> affectedStateKeys,
            Map<RecordStateKey, Optional<RecordStateSnapshot>> expectedStates,
            List<PlannedFact> desiredFacts) {
        private EvaluationPlan {
            candidates = List.copyOf(candidates);
            java.util.Objects.requireNonNull(analysis, "analysis");
            affectedStateKeys = Set.copyOf(affectedStateKeys);
            expectedStates = Map.copyOf(expectedStates);
            desiredFacts = List.copyOf(desiredFacts);
        }
    }

    private static final class StaleCorrectionPlan extends RuntimeException { }
    private static final class LostLeaseDuringCompletion extends RuntimeException { }

    public enum ProcessingResult { PROCESSED, FENCED_OUT }
}
