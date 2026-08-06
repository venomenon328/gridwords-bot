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
        return transactions.inTransaction(() -> processWithinTransaction(claim));
    }

    private ProcessingResult processWithinTransaction(RecordLiveEvaluationClaim claim) {
        Instant now = clock.instant();
        if (!work.fence(claim.key(), claim.token(), now)) return ProcessingResult.FENCED_OUT;

        RecordHistorySnapshot canonical = history.loadFor(claim.key());
        RecordHistorySnapshot.Result result = canonical.results().stream()
                .filter(candidate -> candidate.resultId() == claim.key().gameResultId())
                .filter(candidate -> candidate.resultVersion() == claim.key().gameResultVersion())
                .findFirst().orElseThrow(() -> new IllegalStateException("claimed result is absent from canonical history"));
        boolean ready = bootstrap.readiness(new RecordBootstrapKey(
                claim.key().guildId(), RecordDefinitionVersion.RECORDS_V1)) == RecordBootstrapReadiness.READY;

        List<RecordEventSnapshot> invalidated = invalidatePriorResultFacts(claim, now);
        List<RecordEventSnapshot> appended = new ArrayList<>();

        if (result.outcome() instanceof de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved) {
            appended.addAll(appendResultFacts(claim, canonical, result, ready, now));
        }
        appended.addAll(appendImmediateStreakFacts(claim, canonical, result, ready, now));

        reconcileStates(claim, canonical, now);
        reconcileAnnouncements(invalidated, appended, ready);

        if (!work.markSucceeded(claim.key(), claim.token(), now)) {
            throw new IllegalStateException("live evaluation lease was lost before completion");
        }
        return ProcessingResult.PROCESSED;
    }

    private List<RecordEventSnapshot> invalidatePriorResultFacts(RecordLiveEvaluationClaim claim, Instant now) {
        List<RecordEventSnapshot> invalidated = new ArrayList<>();
        for (RecordEventSnapshot event : events.findByResultId(claim.key().guildId(), claim.key().gameResultId())) {
            if (event.validity() == RecordEventValidity.VALID
                    && event.draft().newSource() instanceof RecordSourceReference.GameResult source
                    && source.resultVersion() != claim.key().gameResultVersion()) {
                if (events.invalidate(event.draft().eventId(), now)) invalidated.add(event);
            }
        }
        return invalidated;
    }

    private List<RecordEventSnapshot> appendResultFacts(
            RecordLiveEvaluationClaim claim,
            RecordHistorySnapshot canonical,
            RecordHistorySnapshot.Result result,
            boolean ready,
            Instant now) {
        List<ResultRecordStateSnapshot> current = states.states(claim.key().guildId(), catalog.version()).stream()
                .filter(state -> state.source() instanceof RecordSourceReference.GameResult source
                        && source.game() == result.game())
                .filter(state -> state.key().scope() instanceof RecordScope.ServerIndividual
                        || state.key().scope() instanceof RecordScope.Personal personal
                                && personal.playerId() == result.playerId())
                .map(this::resultState).toList();
        List<de.venomenon.gridwordsbot.domain.record.ResultRecordObservation> prior = canonical.results().stream()
                .filter(item -> item.game() == result.game())
                .filter(item -> item.resultId() != result.resultId())
                .filter(item -> item.outcome() instanceof de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved)
                .map(RecordHistorySnapshot.Result::solvedObservation).toList();
        List<RecordEventSnapshot> appended = new ArrayList<>();
        for (ResultRecordEvaluation evaluation : resultEvaluator.evaluate(
                result.solvedObservation(), new ResultRecordHistorySnapshot(prior), current, claim.processingOrigin()).evaluations()) {
            if (evaluation.action() != de.venomenon.gridwordsbot.domain.record.ResultRecordEvaluationAction.IMPROVED
                    || !ready || !evaluation.publicAnnouncementEligible()) continue;
            String key = "result:" + claim.key().gameResultId() + ":" + claim.key().gameResultVersion()
                    + ":" + evaluation.definition().key().value() + ":" + scopeKey(evaluation.scope());
            appended.add(events.append(new RecordEventDraft(
                    stableUuid(key), key, new de.venomenon.gridwordsbot.domain.record.RecordStateKey(
                            claim.key().guildId(), evaluation.definition().key(), evaluation.definition().definitionVersion(), evaluation.scope()),
                    RecordEventType.RESULT_RECORD_BROKEN, evaluation.previousValue(), evaluation.resultingValue(),
                    evaluation.previousHolderPlayerId().isPresent() ? Optional.of(evaluation.previousHolderPlayerId().getAsLong()) : Optional.empty(),
                    Optional.of(evaluation.resultingHolderPlayerId()), evaluation.previousSourceReference().map(source -> source),
                    evaluation.resultingSourceReference(), trigger(claim), claim.processingOrigin(), now)).snapshot());
        }
        return appended;
    }

    private List<RecordEventSnapshot> appendImmediateStreakFacts(
            RecordLiveEvaluationClaim claim, RecordHistorySnapshot canonical, RecordHistorySnapshot.Result result,
            boolean ready, Instant now) {
        LocalDate first = canonical.results().stream().map(RecordHistorySnapshot.Result::gameDate)
                .min(Comparator.naturalOrder()).orElse(result.gameDate());
        LocalDate asOf = canonical.results().stream().map(RecordHistorySnapshot.Result::gameDate)
                .max(Comparator.naturalOrder()).orElse(result.gameDate());
        StreakRunAnalysis analysis = streakAnalyzer.analyze(canonical.results().stream()
                .map(RecordHistorySnapshot.Result::streakResult).toList(), canonical.participationPeriods(),
                new StreakRunAnalysisWindow(first, asOf, false));
        List<RecordEventSnapshot> appended = new ArrayList<>();
        for (StreakRun run : analysis.runs()) {
            if (run.endDate().isBefore(result.gameDate()) || run.identity().startDate().isAfter(result.gameDate())) continue;
            if (run.identity().ownerScope() instanceof RecordScope.Personal personal && personal.playerId() != result.playerId()) continue;
            for (var evaluation : streakEvaluator.evaluate(run, new StreakRecordHistorySnapshot(analysis.runs()),
                    states.consumedCrossings(claim.key().guildId(), catalog.version()), claim.processingOrigin()).publiclyEligible()) {
                if (!ready) continue;
                String key = "streak:" + claim.key().gameResultId() + ":" + claim.key().gameResultVersion() + ":"
                        + evaluation.definition().key().value() + ":" + evaluation.candidate().identity();
                RecordEventType type = switch (evaluation.classification()) {
                    case CROSSED -> RecordEventType.SERIES_RECORD_CROSSED;
                    case TIED -> RecordEventType.SERIES_RECORD_TIED_AT_END;
                    case NEAR_MISS -> RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END;
                    case NEW_RECORD -> RecordEventType.RECORD_SERIES_FINISHED;
                    case NONE -> throw new IllegalStateException("non-notable streak event");
                };
                Optional<StreakRun> reference = evaluation.reference();
                Optional<Long> previousHolder = reference.map(candidate -> candidate.identity().ownerScope())
                        .filter(RecordScope.Personal.class::isInstance).map(RecordScope.Personal.class::cast)
                        .map(RecordScope.Personal::playerId);
                Optional<RecordSourceReference> previousSource = reference.map(StreakRun::sourceReference).map(source -> source);
                Optional<de.venomenon.gridwordsbot.domain.record.RecordValue> previousValue = reference.map(StreakRun::value).map(value -> value);
                Optional<Long> newHolder = evaluation.candidate().identity().ownerScope() instanceof RecordScope.Personal personal
                        ? Optional.of(personal.playerId()) : Optional.empty();
                appended.add(events.append(new RecordEventDraft(stableUuid(key), key,
                        new de.venomenon.gridwordsbot.domain.record.RecordStateKey(claim.key().guildId(),
                                evaluation.definition().key(), evaluation.definition().definitionVersion(), evaluation.comparisonScope()),
                        type, previousValue, evaluation.candidate().value(), previousHolder, newHolder, previousSource,
                        evaluation.candidate().sourceReference(), trigger(claim), claim.processingOrigin(), now)).snapshot());
            }
        }
        return appended;
    }

    private void reconcileStates(RecordLiveEvaluationClaim claim, RecordHistorySnapshot history, Instant now) {
        LocalDate first = history.results().stream().map(RecordHistorySnapshot.Result::gameDate)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate asOf = history.results().stream().map(RecordHistorySnapshot.Result::gameDate)
                .max(Comparator.naturalOrder()).orElseThrow();
        List<RecordBootstrapProjection.Candidate> candidates = new RecordBootstrapProjection(catalog, streakAnalyzer)
                .project(claim.key().guildId(), history, new StreakRunAnalysisWindow(first, asOf, false));
        for (RecordBootstrapProjection.Candidate candidate : candidates) {
            RecordStateService.RebuildResult outcome = states.reconcileCanonicalTargetWithinTransaction(
                    candidate, "live-reconcile:" + claim.key().gameResultId(), now);
            if (outcome == RecordStateService.RebuildResult.RETRY_EXHAUSTED) {
                throw new IllegalStateException("record-state optimistic update retries exhausted");
            }
        }
    }

    private void reconcileAnnouncements(List<RecordEventSnapshot> invalidated, List<RecordEventSnapshot> appended, boolean ready) {
        if (!ready) return;
        for (RecordEventSnapshot event : invalidated) {
            for (RecordAnnouncementSnapshot announcement : announcements.findByEventId(event.draft().eventId())) {
                updateAnnouncement(announcement, announcement.registration().eventIds().stream()
                        .filter(id -> !id.equals(event.draft().eventId())).toList());
            }
        }
        appended.stream().filter(event -> event.validity() == RecordEventValidity.VALID)
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
        List<UUID> ids = facts.stream().map(event -> event.draft().eventId()).sorted().toList();
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

    public enum ProcessingResult { PROCESSED, FENCED_OUT }
}
