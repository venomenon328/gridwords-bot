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
import java.util.UUID;

/**
 * Processes exactly one claimed, versioned live evaluation.  Claim polling,
 * retry policy and all transport delivery deliberately remain outside this
 * application service.
 */
public final class RecordLiveEvaluationProcessor {
    private static final String RENDERER_VERSION = "records-v1";
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
        java.util.Objects.requireNonNull(claim, "claim");
        // Canonical reads and the pure record/series derivation intentionally
        // precede the short write transaction.  The claim/result fence below
        // is repeated immediately before the first write.
        RecordHistorySnapshot canonical = history.loadFor(claim.key(), claim.processingOrigin());
        RecordHistorySnapshot.Result result = canonical.results().stream()
                .filter(candidate -> candidate.resultId() == claim.key().gameResultId())
                .filter(candidate -> candidate.resultVersion() == claim.key().gameResultVersion())
                .findFirst().orElseThrow(() -> new IllegalStateException("claimed result is absent from canonical history"));
        EvaluationPlan plan = evaluate(claim, canonical, result);
        return transactions.inTransaction(() -> processWithinTransaction(claim, canonical, result, plan));
    }

    private ProcessingResult processWithinTransaction(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot canonical,
            RecordHistorySnapshot.Result result,
            EvaluationPlan plan) {
        Instant now = clock.instant();
        if (!work.fence(claim.key(), claim.token(), now)) return ProcessingResult.FENCED_OUT;
        boolean ready = bootstrap.readiness(new RecordBootstrapKey(
                claim.key().guildId(), RecordDefinitionVersion.RECORDS_V1)) == RecordBootstrapReadiness.READY;

        Map<de.venomenon.gridwordsbot.domain.record.RecordStateKey, RecordStateService.StateTransition> transitions =
                reconcileStates(claim, result, plan);
        List<RecordEventSnapshot> invalidated = invalidatePriorResultFacts(claim, result, plan.analysis(), now);
        List<AppendedFact> appended = new ArrayList<>();

        if (result.outcome() instanceof de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved) {
            appended.addAll(appendResultFacts(claim, canonical, result, transitions, ready, now));
        }
        appended.addAll(appendImmediateStreakFacts(claim, result, plan.analysis(), transitions, ready, now));

        if (requiresExactReconciliation(claim.processingOrigin())) {
            appended.addAll(appendCorrectionReplacementFacts(claim, invalidated, transitions, ready, now));
        }
        reconcileAnnouncements(invalidated, appended, ready);

        if (!work.markSucceeded(claim.key(), claim.token(), now)) {
            throw new IllegalStateException("live evaluation lease was lost before completion");
        }
        return ProcessingResult.PROCESSED;
    }

    private List<RecordEventSnapshot> invalidatePriorResultFacts(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot.Result changedResult,
            StreakRunAnalysis analysis,
            Instant now) {
        List<RecordEventSnapshot> invalidated = new ArrayList<>();
        java.util.Map<UUID, RecordEventSnapshot> prior = new java.util.LinkedHashMap<>();
        events.findByResultId(claim.key().guildId(), claim.key().gameResultId())
                .forEach(event -> prior.put(event.draft().eventId(), event));
        if (requiresExactReconciliation(claim.processingOrigin())) {
            events.findByTriggerKey(claim.key().guildId(), trigger(claim))
                    .forEach(event -> prior.put(event.draft().eventId(), event));
            events.findAllByGuild(claim.key().guildId()).stream()
                    .filter(event -> affectedStreakFact(event, analysis, changedResult))
                    .forEach(event -> prior.put(event.draft().eventId(), event));
        }
        for (RecordEventSnapshot event : prior.values()) {
            boolean obsoleteResult = event.draft().newSource() instanceof RecordSourceReference.GameResult source
                    && source.resultVersion() != claim.key().gameResultVersion();
            boolean affectedStreak = requiresExactReconciliation(claim.processingOrigin())
                    && affectedStreakFact(event, analysis, changedResult);
            if (event.validity() == RecordEventValidity.VALID && (obsoleteResult || affectedStreak)) {
                if (events.invalidate(event.draft().eventId(), now)) invalidated.add(event);
            }
        }
        return invalidated;
    }

    /**
     * A correction is compared against the newly derived run identities, not
     * only against the result trigger.  Facts that ended before the corrected
     * day remain valid; facts at or after that day are invalidated whenever
     * their old identity disappeared or their exact run value changed.
     */
    private boolean affectedStreakFact(
            RecordEventSnapshot event,
            StreakRunAnalysis analysis,
            RecordHistorySnapshot.Result changedResult) {
        if (!(event.draft().newSource() instanceof RecordSourceReference.StreakRun source)) return false;
        if (!sameAffectedOwner(source, changedResult.playerId())) return false;
        if (!(event.draft().newValue() instanceof de.venomenon.gridwordsbot.domain.record.StreakRecordValue oldValue)
                || oldValue.endDate().isBefore(changedResult.gameDate())) return false;
        return analysis.runs().stream()
                .filter(run -> run.sourceReference().equals(source))
                .noneMatch(run -> run.value().equals(oldValue));
    }

    private static boolean sameAffectedOwner(RecordSourceReference.StreakRun source, long playerId) {
        return switch (source.owner()) {
            case RecordSourceReference.StreakRunOwner.Player player -> player.playerId() == playerId;
            case RecordSourceReference.StreakRunOwner.Shared ignored -> true;
        };
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
        java.util.Set<de.venomenon.gridwordsbot.domain.record.RecordStateKey> keys = new java.util.HashSet<>(targets.keySet());
        keys.addAll(states.states(claim.key().guildId(), catalog.version()).stream()
                .filter(state -> affectedBy(state.key(), changedResult))
                .map(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot::key).toList());
        for (de.venomenon.gridwordsbot.domain.record.RecordStateKey key : keys) {
            RecordStateService.StateTransition transition = states.reconcileCorrectionTargetTransitionWithinTransaction(
                    Optional.ofNullable(targets.get(key)), key);
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
            RecordHistorySnapshot.Result changedResult) {
        StreakRunAnalysisWindow window = analysisWindow(history, changedResult, claim.processingOrigin());
        StreakRunAnalysis analysis = streakAnalyzer.analyze(
                history.results().stream().map(RecordHistorySnapshot.Result::streakResult).toList(),
                history.participationPeriods(), window);
        List<RecordBootstrapProjection.Candidate> candidates = new RecordBootstrapProjection(catalog, streakAnalyzer)
                .project(claim.key().guildId(), history, window).stream()
                .filter(candidate -> affectedBy(candidate.key(), changedResult)).toList();
        return new EvaluationPlan(candidates, analysis);
    }

    private static StreakRunAnalysisWindow analysisWindow(
            RecordHistorySnapshot history,
            RecordHistorySnapshot.Result changedResult,
            RecordProcessingOrigin origin) {
        LocalDate first = history.participationPeriods().stream()
                .filter(period -> period.playerId() == changedResult.playerId())
                .filter(period -> period.contains(changedResult.gameDate()))
                .map(de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod::activeFrom)
                .min(Comparator.naturalOrder()).orElse(changedResult.gameDate());
        LocalDate asOf = changedResult.gameDate();
        if (requiresExactReconciliation(origin)) {
            boolean openParticipation = history.participationPeriods().stream()
                    .filter(period -> period.playerId() == changedResult.playerId())
                    .filter(period -> period.contains(changedResult.gameDate()))
                    .anyMatch(period -> period.inactiveFrom() == null);
            LocalDate periodEnd = history.participationPeriods().stream()
                    .filter(period -> period.playerId() == changedResult.playerId())
                    .filter(period -> period.contains(changedResult.gameDate()))
                    .map(de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod::inactiveFrom)
                    .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(changedResult.gameDate());
            LocalDate lastCanonical = history.results().stream().map(RecordHistorySnapshot.Result::gameDate)
                    .max(Comparator.naturalOrder()).orElse(changedResult.gameDate());
            asOf = openParticipation ? lastCanonical : (periodEnd.isBefore(lastCanonical) ? periodEnd : lastCanonical);
        }
        return new StreakRunAnalysisWindow(first, asOf, false);
    }

    /**
     * A correction invalidates obsolete facts first.  If its exact canonical
     * target still has a source, append a successor fact in the same logical
     * family so a previously delivered aggregate becomes an EDIT rather than a
     * destructive DELETE followed by an unrelated CREATE.
     */
    private List<AppendedFact> appendCorrectionReplacementFacts(
            RecordLiveEvaluationClaim claim,
            List<RecordEventSnapshot> invalidated,
            Map<de.venomenon.gridwordsbot.domain.record.RecordStateKey, RecordStateService.StateTransition> transitions,
            boolean ready,
            Instant now) {
        List<AppendedFact> replacements = new ArrayList<>();
        for (RecordEventSnapshot invalidatedEvent : invalidated) {
            RecordEventDraft old = invalidatedEvent.draft();
            if (old.type() == RecordEventType.RECORD_INITIALIZED) continue;
            RecordStateService.StateTransition transition = transitions.get(old.stateKey());
            if (transition == null || !transition.changed() || transition.after().isEmpty()) continue;
            var state = transition.after().orElseThrow();
            String key = "correction:" + claim.key().gameResultId() + ":" + claim.key().gameResultVersion()
                    + ":" + old.stateKey().definitionKey().value() + ":" + old.stateKey().scopeKey();
            RecordEventSnapshot successor = events.append(new RecordEventDraft(
                    stableUuid(key), key, state.key(), old.type(),
                    transition.before().map(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot::value), state.value(),
                    transition.before().flatMap(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot::holderPlayerId),
                    state.holderPlayerId(),
                    transition.before().map(de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot::source), state.source(),
                    trigger(claim), claim.processingOrigin(), now)).snapshot();
            boolean wasPublic = !announcements.findByEventId(old.eventId()).isEmpty();
            replacements.add(new AppendedFact(successor,
                    ready && claim.processingOrigin().publicAnnouncementEligible() && wasPublic));
        }
        return replacements;
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
                                phase(event.draft().type()), subject(event.draft())), java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .forEach((bucket, facts) -> registerNewFacts(facts, bucket));
    }

    private void registerNewFacts(List<RecordEventSnapshot> facts, AnnouncementBucket bucket) {
        String subjectKey = bucket.subject().key();
        RecordAnnouncementKey key = new RecordAnnouncementKey(facts.getFirst().draft().stateKey().guildId(), channelId,
                triggerFromFacts(facts) + ":" + subjectKey + ":" + bucket.phase().name());
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
    private static String triggerFromFacts(List<RecordEventSnapshot> facts) { return facts.getFirst().draft().triggerKey(); }
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

    private record AnnouncementBucket(RecordAnnouncementPhase phase, RecordAnnouncementSubject subject) { }
    private record AppendedFact(RecordEventSnapshot snapshot, boolean announcementEligible) { }
    private record EvaluationPlan(List<RecordBootstrapProjection.Candidate> candidates, StreakRunAnalysis analysis) {
        private EvaluationPlan {
            candidates = List.copyOf(candidates);
            java.util.Objects.requireNonNull(analysis, "analysis");
        }
    }

    public enum ProcessingResult { PROCESSED, FENCED_OUT }
}
