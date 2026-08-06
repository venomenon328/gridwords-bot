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
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseKey;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.domain.record.StreakRecordClassification;
import de.venomenon.gridwordsbot.domain.record.StreakRecordEvaluator;
import de.venomenon.gridwordsbot.domain.record.StreakRecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.StreakRecordMetric;
import de.venomenon.gridwordsbot.domain.record.StreakRun;
import de.venomenon.gridwordsbot.domain.record.StreakRunAnalysis;
import de.venomenon.gridwordsbot.domain.record.StreakRunAnalysisWindow;
import de.venomenon.gridwordsbot.domain.record.StreakRunChange;
import de.venomenon.gridwordsbot.domain.record.StreakRunReconciler;
import de.venomenon.gridwordsbot.domain.record.StreakRunStatus;
import de.venomenon.gridwordsbot.domain.record.StreakRunAnalyzer;
import de.venomenon.gridwordsbot.port.in.RecordDayCloseUseCase;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordDayCloseStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordPermanentFailure;
import de.venomenon.gridwordsbot.port.out.RecordRetryableFailure;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
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

/**
 * Registers and processes closed business days in order.  The marker is the
 * durable source of day-close progress; the scheduler merely calls this use
 * case and never decides the business cutoff itself.
 */
public final class RecordDayCloseService implements RecordDayCloseUseCase {
    private static final String RENDERER_VERSION = "records-v1";

    private final RecordDayCloseStore work;
    private final RecordHistoryQuery history;
    private final RecordBootstrapReadService bootstrap;
    private final RecordStateService states;
    private final RecordEventStore events;
    private final RecordAnnouncementStore announcements;
    private final RecordTransactionRunner transactions;
    private final RecordDefinitionCatalog catalog;
    private final Clock clock;
    private final long channelId;
    private final Duration leaseDuration;
    private final Duration retryBackoff;
    private final StreakRunAnalyzer analyzer = new StreakRunAnalyzer();
    private final StreakRecordEvaluator evaluator;

    public RecordDayCloseService(
            RecordDayCloseStore work,
            RecordHistoryQuery history,
            RecordBootstrapReadService bootstrap,
            RecordStateService states,
            RecordEventStore events,
            RecordAnnouncementStore announcements,
            RecordTransactionRunner transactions,
            RecordDefinitionCatalog catalog,
            Clock clock,
            long channelId) {
        this(work, history, bootstrap, states, events, announcements, transactions, catalog, clock, channelId,
                Duration.ofMinutes(2), Duration.ofMinutes(1));
    }

    public RecordDayCloseService(
            RecordDayCloseStore work,
            RecordHistoryQuery history,
            RecordBootstrapReadService bootstrap,
            RecordStateService states,
            RecordEventStore events,
            RecordAnnouncementStore announcements,
            RecordTransactionRunner transactions,
            RecordDefinitionCatalog catalog,
            Clock clock,
            long channelId,
            Duration leaseDuration,
            Duration retryBackoff) {
        this.work = java.util.Objects.requireNonNull(work, "work");
        this.history = java.util.Objects.requireNonNull(history, "history");
        this.bootstrap = java.util.Objects.requireNonNull(bootstrap, "bootstrap");
        this.states = java.util.Objects.requireNonNull(states, "states");
        this.events = java.util.Objects.requireNonNull(events, "events");
        this.announcements = java.util.Objects.requireNonNull(announcements, "announcements");
        this.transactions = java.util.Objects.requireNonNull(transactions, "transactions");
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        if (channelId <= 0) throw new IllegalArgumentException("channelId must be positive");
        this.channelId = channelId;
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.retryBackoff = positive(retryBackoff, "retryBackoff");
        this.evaluator = new StreakRecordEvaluator(catalog);
    }

    /** Registers all missing closes through {@code inclusiveCloseDate} and processes them chronologically. */
    @Override
    public int reconcileThrough(long guildId, LocalDate inclusiveCloseDate) {
        if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
        java.util.Objects.requireNonNull(inclusiveCloseDate, "inclusiveCloseDate");
        if (bootstrap.readiness(new RecordBootstrapKey(guildId, catalog.version()))
                != RecordBootstrapReadiness.READY) {
            // Historical materialization owns the initial state.  A regular
            // close is retried by the existing cleanup trigger once that
            // silent bootstrap has completed.
            return 0;
        }
        RecordHistorySnapshot snapshot = history.load(guildId);
        Optional<LocalDate> earliest = earliestRelevantDay(snapshot);
        if (earliest.isEmpty()) return 0;
        LocalDate first = work.latestSucceededDate(guildId, catalog.version().value())
                .map(date -> date.plusDays(1)).orElseGet(earliest::orElseThrow);
        int completed = 0;
        for (LocalDate day = first; !day.isAfter(inclusiveCloseDate); day = day.plusDays(1)) {
            RecordDayCloseKey key = new RecordDayCloseKey(guildId, catalog.version(), day);
            if (work.register(key).state() == de.venomenon.gridwordsbot.domain.record.RecordWorkState.SUCCEEDED) {
                continue;
            }
            DayCloseResult result = process(key, inclusiveCloseDate.plusDays(1));
            if (result != DayCloseResult.SUCCEEDED) {
                break;
            }
            completed++;
        }
        return completed;
    }

    private DayCloseResult process(RecordDayCloseKey key, LocalDate currentOpenDate) {
        Instant now = clock.instant();
        RecordDayCloseClaim claim = work.claim(key, leaseRequest(now)).orElse(null);
        if (claim == null) return DayCloseResult.NOT_CLAIMED;
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                if (!work.renewLease(key, claim.token(), leaseRequest(clock.instant()))) {
                    return DayCloseResult.LOST_LEASE;
                }
                RecordHistorySnapshot canonical = history.load(key.guildId());
                Plan plan = plan(key, canonical, currentOpenDate,
                        states.states(key.guildId(), catalog.version()));
                try {
                    return transactions.inTransaction(() -> completeClaim(key, claim, canonical, plan));
                } catch (StalePlan ignored) {
                    // Re-read the canonical snapshot and all record state generations.
                }
            }
            throw new RecordRetryableFailure("day-close canonical plan changed repeatedly", null);
        } catch (RecordRetryableFailure exception) {
            return work.markRetryableFailure(key, claim.token(),
                    new RecordWorkFailure(RecordWorkFailureCategory.RETRYABLE, "record day close retryable failure"),
                    clock.instant().plus(retryBackoff)) ? DayCloseResult.RETRY_SCHEDULED : DayCloseResult.LOST_LEASE;
        } catch (RecordPermanentFailure exception) {
            return work.markPermanentFailure(key, claim.token(),
                    new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT, "record day close permanent failure"),
                    clock.instant()) ? DayCloseResult.FAILED_PERMANENT : DayCloseResult.LOST_LEASE;
        }
    }

    private DayCloseResult completeClaim(
            RecordDayCloseKey key,
            RecordDayCloseClaim claim,
            RecordHistorySnapshot canonical,
            Plan plan) {
        Instant now = clock.instant();
        if (!work.fence(key, claim.token(), now)) return DayCloseResult.LOST_LEASE;
        if (!history.load(key.guildId()).equals(canonical)) throw new StalePlan();
        boolean ready = bootstrap.readiness(new RecordBootstrapKey(key.guildId(), RecordDefinitionVersion.RECORDS_V1))
                == RecordBootstrapReadiness.READY;
        reconcileStates(plan);
        List<AppendedFact> appended = appendFacts(key, plan, ready, now);
        if (ready) reconcileAnnouncements(appended);
        if (!work.markSucceeded(key, claim.token(), now)) return DayCloseResult.LOST_LEASE;
        return DayCloseResult.SUCCEEDED;
    }

    private Plan plan(
            RecordDayCloseKey key,
            RecordHistorySnapshot canonical,
            LocalDate currentOpenDate,
            List<RecordStateSnapshot> currentStates) {
        LocalDate first = earliestRelevantDay(canonical).orElse(key.gameDate());
        StreakRunAnalysis before = analyzer.analyze(streakResults(canonical), canonical.participationPeriods(),
                new StreakRunAnalysisWindow(first, key.gameDate(), false));
        StreakRunAnalysis after = analyzer.analyze(streakResults(canonical), canonical.participationPeriods(),
                new StreakRunAnalysisWindow(first, key.gameDate().plusDays(1), false));
        StreakRunAnalysis current = analyzer.analyze(streakResults(canonical), canonical.participationPeriods(),
                new StreakRunAnalysisWindow(first, currentOpenDate, false));
        List<StreakRun> changed = new StreakRunReconciler().reconcile(before, after).stream()
                .filter(change -> change.type() != StreakRunChange.Type.REMOVED)
                .map(change -> change.current().orElseThrow())
                .filter(run -> finalisedBy(key.gameDate(), run))
                .toList();
        RecordBootstrapProjection projection = new RecordBootstrapProjection(catalog, analyzer);
        Map<RecordStateKey, RecordBootstrapProjection.Candidate> targets = projection.project(
                        key.guildId(), canonical, new StreakRunAnalysisWindow(first, currentOpenDate, false)).stream()
                .filter(candidate -> catalog.find(candidate.key().definitionKey()).orElseThrow().metric()
                        instanceof StreakRecordMetric)
                .collect(java.util.stream.Collectors.toMap(RecordBootstrapProjection.Candidate::key, candidate -> candidate));
        Set<RecordStateKey> keys = new java.util.LinkedHashSet<>(targets.keySet());
        currentStates.stream().filter(state -> catalog.find(state.key().definitionKey()).orElseThrow().metric()
                instanceof StreakRecordMetric).map(RecordStateSnapshot::key).forEach(keys::add);
        Map<RecordStateKey, Optional<RecordStateSnapshot>> expected = keys.stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(keyValue -> keyValue,
                        keyValue -> currentStates.stream().filter(state -> state.key().equals(keyValue)).findFirst()));
        return new Plan(before, after, changed, targets, keys, expected);
    }

    private void reconcileStates(Plan plan) {
        for (RecordStateKey key : plan.stateKeys()) {
            RecordStateService.StateTransition transition = states.reconcileCorrectionTargetTransitionWithinTransaction(
                    Optional.ofNullable(plan.targets().get(key)), key, plan.expectedStates().getOrDefault(key, Optional.empty()));
            if (transition.result() == RecordStateService.RebuildResult.STALE_PLAN) throw new StalePlan();
            if (transition.result() == RecordStateService.RebuildResult.RETRY_EXHAUSTED) {
                throw new RecordRetryableFailure("day-close record-state CAS retries exhausted", null);
            }
        }
    }

    private List<AppendedFact> appendFacts(
            RecordDayCloseKey key, Plan plan, boolean ready, Instant detectedAt) {
        List<AppendedFact> appended = new ArrayList<>();
        Set<de.venomenon.gridwordsbot.domain.record.StreakCrossingKey> consumed = states.consumedCrossings(
                key.guildId(), catalog.version());
        for (StreakRun run : plan.changedRuns()) {
            for (var evaluation : evaluator.evaluate(run, new StreakRecordHistorySnapshot(plan.after().runs()), consumed,
                    RecordProcessingOrigin.DAY_CLOSE).notable()) {
                RecordStateKey stateKey = new RecordStateKey(key.guildId(), evaluation.definition().key(),
                        evaluation.definition().definitionVersion(), evaluation.comparisonScope());
                RecordEventType type = eventType(evaluation.classification());
                String stable = "day-close:" + key.gameDate() + ":" + evaluation.definition().key().value() + ":"
                        + stateKey.scopeKey() + ":" + run.identity();
                RecordSourceReference.StreakRun source = evaluation.candidate().sourceReference();
                RecordEventSnapshot event = events.append(new RecordEventDraft(stableUuid(stable), stable, stateKey, type,
                        evaluation.reference().map(candidate -> (de.venomenon.gridwordsbot.domain.record.RecordValue) candidate.value()),
                        evaluation.candidate().value(), evaluation.reference().flatMap(candidate -> holder(candidate.sourceReference())),
                        holder(source), evaluation.reference().map(candidate -> (RecordSourceReference) candidate.sourceReference()), source,
                        "day-close:" + key.gameDate(), RecordProcessingOrigin.DAY_CLOSE, detectedAt)).snapshot();
                if (event.draft().processingOrigin() == RecordProcessingOrigin.DAY_CLOSE) {
                    appended.add(new AppendedFact(event, ready && evaluation.publicAnnouncementEligible()));
                }
            }
        }
        return appended;
    }

    private void reconcileAnnouncements(List<AppendedFact> appended) {
        appended.stream().filter(AppendedFact::announcementEligible).map(AppendedFact::snapshot)
                .collect(java.util.stream.Collectors.groupingBy(event -> new AnnouncementBucket(
                        event.draft().triggerKey(), phase(event.draft().type()), subject(event.draft())),
                java.util.LinkedHashMap::new, java.util.stream.Collectors.toList())).forEach((bucket, facts) -> {
            List<UUID> ids = facts.stream().map(event -> event.draft().eventId()).distinct().sorted().toList();
            RecordAnnouncementKey key = new RecordAnnouncementKey(facts.getFirst().draft().stateKey().guildId(), channelId,
                    bucket.trigger() + ":" + bucket.subject().key() + ":" + bucket.phase().name());
            RecordAnnouncementSnapshot existing = announcements.find(key).orElse(null);
            RecordAnnouncementProjection desired = existing == null ? RecordAnnouncementProjection.CREATE
                    : existing.registration().eventIds().equals(ids) ? RecordAnnouncementProjection.NO_OP
                    : existing.publishedAt().isPresent() ? RecordAnnouncementProjection.EDIT : RecordAnnouncementProjection.CREATE;
            announcements.registerOrUpdate(new RecordAnnouncementRegistration(key, bucket.subject(), bucket.phase(), desired,
                    RENDERER_VERSION, fingerprint(ids), ids));
        });
    }

    private static boolean finalisedBy(LocalDate closedDate, StreakRun run) {
        if (run.status() == StreakRunStatus.ENDED_BY_DAY_CLOSE) {
            return run.endDate().plusDays(1).equals(closedDate);
        }
        if (run.status() == StreakRunStatus.ENDED_BY_PARTICIPATION) return run.endDate().equals(closedDate);
        return run.status() == StreakRunStatus.RUNNING
                && run.identity().metric() == StreakRecordMetric.WITHOUT_PERFECT_DAY
                && run.endDate().equals(closedDate);
    }

    private static RecordEventType eventType(StreakRecordClassification classification) {
        return switch (classification) {
            case CROSSED -> RecordEventType.SERIES_RECORD_CROSSED;
            case TIED -> RecordEventType.SERIES_RECORD_TIED_AT_END;
            case NEAR_MISS -> RecordEventType.SERIES_RECORD_NEAR_MISSED_AT_END;
            case NEW_RECORD -> RecordEventType.RECORD_SERIES_FINISHED;
            case NONE -> throw new IllegalArgumentException("non-notable day-close streak event");
        };
    }

    private static Optional<Long> holder(RecordSourceReference.StreakRun source) {
        return switch (source.owner()) {
            case RecordSourceReference.StreakRunOwner.Player player -> Optional.of(player.playerId());
            case RecordSourceReference.StreakRunOwner.Shared ignored -> Optional.empty();
        };
    }

    private static RecordAnnouncementPhase phase(RecordEventType type) {
        return type == RecordEventType.SERIES_RECORD_CROSSED
                ? RecordAnnouncementPhase.STREAK_CROSSED : RecordAnnouncementPhase.STREAK_FINISHED;
    }

    private static RecordAnnouncementSubject subject(RecordEventDraft event) {
        return event.newHolderPlayerId().map(RecordAnnouncementSubject::player).orElseGet(RecordAnnouncementSubject::shared);
    }

    private static List<de.venomenon.gridwordsbot.domain.streak.StreakGameResult> streakResults(
            RecordHistorySnapshot history) {
        return history.results().stream().map(RecordHistorySnapshot.Result::streakResult).toList();
    }

    private static Optional<LocalDate> earliestRelevantDay(RecordHistorySnapshot history) {
        return java.util.stream.Stream.concat(history.results().stream().map(RecordHistorySnapshot.Result::gameDate),
                        history.participationPeriods().stream().map(period -> period.activeFrom()))
                .min(Comparator.naturalOrder());
    }

    private RecordLeaseClaimRequest leaseRequest(Instant now) {
        return new RecordLeaseClaimRequest(now, now.plus(leaseDuration));
    }

    private static Duration positive(Duration value, String name) {
        java.util.Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static UUID stableUuid(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String fingerprint(List<UUID> eventIds) {
        try {
            String source = eventIds.stream().map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining("|"));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Plan(
            StreakRunAnalysis before,
            StreakRunAnalysis after,
            List<StreakRun> changedRuns,
            Map<RecordStateKey, RecordBootstrapProjection.Candidate> targets,
            Set<RecordStateKey> stateKeys,
            Map<RecordStateKey, Optional<RecordStateSnapshot>> expectedStates) {
        private Plan {
            java.util.Objects.requireNonNull(before, "before");
            java.util.Objects.requireNonNull(after, "after");
            changedRuns = List.copyOf(changedRuns);
            targets = Map.copyOf(targets);
            stateKeys = Set.copyOf(stateKeys);
            expectedStates = Map.copyOf(expectedStates);
        }
    }

    private record AnnouncementBucket(String trigger, RecordAnnouncementPhase phase, RecordAnnouncementSubject subject) { }

    private record AppendedFact(RecordEventSnapshot snapshot, boolean announcementEligible) { }

    private static final class StalePlan extends RuntimeException { }

    public enum DayCloseResult { SUCCEEDED, NOT_CLAIMED, LOST_LEASE, RETRY_SCHEDULED, FAILED_PERMANENT }
}
