package de.venomenon.gridwordsbot.application.achievement;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinition;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvaluation;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvaluator;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementHistorySnapshot;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.AchievementHistoryQuery;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Reconciles the complete, canonical achievement projection for one participant.
 *
 * <p>This service intentionally does not know submissions, schedulers, or Discord. Callers provide a stable
 * processing origin and, for an ordinary trigger, an optional durable live-announcement handoff target.</p>
 */
public final class AchievementReconciliationService {
    private static final String HANDOFF_RENDERER_VERSION = "achievement-handoff-v1";
    private static final int MAX_CAS_ATTEMPTS = 6;
    private static final int MAX_SNAPSHOT_ATTEMPTS = 6;

    private final AchievementHistoryQuery history;
    private final AchievementEvaluator evaluator;
    private final AchievementDefinitionCatalog catalog;
    private final AchievementAwardStateStore awardStates;
    private final AchievementEventStore events;
    private final AchievementAnnouncementStore announcements;
    private final AchievementTransactionRunner transactions;
    private final Clock clock;
    private final ZoneId timeZone;
    private final Map<AchievementKey, Integer> catalogOrder;

    public AchievementReconciliationService(
            AchievementHistoryQuery history,
            AchievementEvaluator evaluator,
            AchievementDefinitionCatalog catalog,
            AchievementAwardStateStore awardStates,
            AchievementEventStore events,
            AchievementAnnouncementStore announcements,
            AchievementTransactionRunner transactions,
            Clock clock,
            ZoneId timeZone) {
        this.history = Objects.requireNonNull(history, "history");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.awardStates = Objects.requireNonNull(awardStates, "awardStates");
        this.events = Objects.requireNonNull(events, "events");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeZone = Objects.requireNonNull(timeZone, "timeZone");
        this.catalogOrder = catalogOrder(catalog);
    }

    /** Loads the participant-wide canonical history, evaluates it, and persists one atomic reconciliation. */
    public ReconciliationResult reconcile(ReconciliationRequest request) {
        Objects.requireNonNull(request, "request");
        for (int attempt = 0; attempt < MAX_SNAPSHOT_ATTEMPTS; attempt++) {
            AchievementHistorySnapshot snapshot = loadSnapshot(request);
            AchievementEvaluation evaluation = evaluator.evaluate(snapshot, timeZone);
            try {
                return transactions.inParticipantTransaction(request.participantId(), () -> {
                    AchievementHistorySnapshot lockedSnapshot = loadSnapshot(request);
                    if (!lockedSnapshot.equals(snapshot)) {
                        throw StaleHistorySnapshotException.INSTANCE;
                    }
                    return reconcileWithinTransaction(request, evaluation);
                });
            } catch (StaleHistorySnapshotException ignored) {
                // Canonical history changed after evaluation; retry from a fresh participant-wide snapshot.
            }
        }
        throw new IllegalStateException("achievement reconciliation history kept changing for participant "
                + request.participantId());
    }

    private AchievementHistorySnapshot loadSnapshot(ReconciliationRequest request) {
        AchievementHistorySnapshot snapshot = history.load(request.guildId(), request.participantId());
        if (snapshot.participantId() != request.participantId()) {
            throw new IllegalStateException("achievement history belongs to another participant");
        }
        return snapshot;
    }

    private ReconciliationResult reconcileWithinTransaction(
            ReconciliationRequest request, AchievementEvaluation evaluation) {
        Map<AchievementKey, AchievementEvidence> evidenced = evidenceByKey(evaluation);
        Map<AchievementKey, AchievementAwardState.Snapshot> existing = existingStates(request);
        Instant detectedAt = clock.instant();
        List<Transition> transitions = new ArrayList<>();
        List<AchievementEventFact.Snapshot> newlyActivatedEvents = new ArrayList<>();

        for (AchievementDefinition definition : catalog.definitions()) {
            AchievementKey key = definition.key();
            AppliedTransition applied = reconcileKey(
                    new AchievementAwardState.Key(request.guildId(), request.participantId(), key),
                    existing.get(key),
                    evidenced.get(key),
                    request.processingOrigin(),
                    detectedAt);
            transitions.add(applied.transition());
            applied.activationEvent().ifPresent(newlyActivatedEvents::add);
        }

        reconcilePendingAnnouncements(request.guildId(), request.participantId());
        Optional<LiveUnlockBatch> batch = request.liveAnnouncementTarget()
                .map(target -> createLiveUnlockBatch(request, target, newlyActivatedEvents))
                .filter(result -> !result.eventIds().isEmpty());
        return new ReconciliationResult(transitions, batch);
    }

    private Map<AchievementKey, AchievementAwardState.Snapshot> existingStates(ReconciliationRequest request) {
        Map<AchievementKey, AchievementAwardState.Snapshot> existing = new HashMap<>();
        for (AchievementAwardState.Snapshot state : awardStates.findAll(request.guildId(), request.participantId())) {
            if (catalog.find(state.key().achievementKey()).isEmpty()) {
                throw new IllegalStateException("persisted state references unknown achievement key: "
                        + state.key().achievementKey().value());
            }
            if (existing.put(state.key().achievementKey(), state) != null) {
                throw new IllegalStateException("duplicate persisted achievement award state: "
                        + state.key().achievementKey().value());
            }
        }
        return existing;
    }

    private AppliedTransition reconcileKey(
            AchievementAwardState.Key key,
            AchievementAwardState.Snapshot initial,
            AchievementEvidence evidence,
            AchievementEventFact.ProcessingOrigin origin,
            Instant detectedAt) {
        AchievementAwardState.Snapshot current = initial;
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            if (current == null) {
                if (evidence == null) {
                    return AppliedTransition.noOp(key.achievementKey());
                }
                AchievementAwardState.Write active = activeWrite(evidence, detectedAt);
                AchievementAwardState.InitializationResult initialized = awardStates.initialize(key, active);
                if (initialized.status() == AchievementAwardState.InitializationStatus.CREATED) {
                    AchievementEventFact.Snapshot event = appendTransition(
                            key, Optional.empty(), active, AchievementEventFact.Type.UNLOCKED, origin, detectedAt);
                    return AppliedTransition.changed(key.achievementKey(), TransitionType.UNLOCK, event);
                }
                current = initialized.snapshot();
                continue;
            }

            if (evidence == null) {
                if (current.write().status() == AchievementAwardState.Status.INVALIDATED) {
                    return AppliedTransition.noOp(key.achievementKey());
                }
                AchievementAwardState.Write invalidated = invalidatedWrite(current, detectedAt);
                AchievementAwardState.UpdateResult updated = awardStates.update(
                        key, current.lockVersion(), invalidated);
                if (updated.status() == AchievementAwardState.UpdateStatus.UPDATED) {
                    AchievementEventFact.Snapshot event = appendTransition(
                            key, Optional.of(current), invalidated,
                            AchievementEventFact.Type.INVALIDATED, origin, detectedAt);
                    return AppliedTransition.changed(key.achievementKey(), TransitionType.INVALIDATE, event);
                }
                current = nextState(updated, key);
                continue;
            }

            AchievementAwardState.Write active = activeWrite(evidence, detectedAt);
            if (current.write().status() == AchievementAwardState.Status.ACTIVE) {
                if (sameActiveProjection(current.write(), active)) {
                    return AppliedTransition.noOp(key.achievementKey());
                }
                AchievementAwardState.Write refreshed = activeWrite(evidence, current.write().detectedAt());
                AchievementAwardState.UpdateResult updated = awardStates.update(
                        key, current.lockVersion(), refreshed);
                if (updated.status() == AchievementAwardState.UpdateStatus.UPDATED
                        || updated.status() == AchievementAwardState.UpdateStatus.UNCHANGED) {
                    return AppliedTransition.noOp(key.achievementKey());
                }
                current = nextState(updated, key);
                continue;
            }

            AchievementAwardState.UpdateResult updated = awardStates.update(key, current.lockVersion(), active);
            if (updated.status() == AchievementAwardState.UpdateStatus.UPDATED) {
                AchievementEventFact.Snapshot event = appendTransition(
                        key, Optional.of(current), active,
                        AchievementEventFact.Type.REACTIVATED, origin, detectedAt);
                return AppliedTransition.changed(key.achievementKey(), TransitionType.REACTIVATE, event);
            }
            current = nextState(updated, key);
        }
        throw new IllegalStateException("achievement reconciliation CAS retries exhausted for "
                + key.achievementKey().value());
    }

    private static AchievementAwardState.Snapshot nextState(
            AchievementAwardState.UpdateResult result, AchievementAwardState.Key key) {
        return switch (result.status()) {
            case VERSION_CONFLICT, UNCHANGED -> result.snapshot().orElseThrow(
                    () -> new IllegalStateException("achievement state is missing after concurrent update"));
            case MISSING -> null;
            case UPDATED -> throw new IllegalStateException("updated state must be handled before retry");
        };
    }

    private AchievementEventFact.Snapshot appendTransition(
            AchievementAwardState.Key key,
            Optional<AchievementAwardState.Snapshot> before,
            AchievementAwardState.Write after,
            AchievementEventFact.Type type,
            AchievementEventFact.ProcessingOrigin origin,
            Instant detectedAt) {
        AchievementAwardState.Write eventFact = type == AchievementEventFact.Type.INVALIDATED
                ? before.orElseThrow(() -> new IllegalStateException("invalidation requires existing state")).write()
                : after;
        String idempotencyKey = "achievement-transition:v1:" + sha256(transitionIdentity(
                key, before, after, type, origin));
        AchievementEventFact.Draft draft = new AchievementEventFact.Draft(
                UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8)),
                idempotencyKey,
                key,
                eventFact.definitionVersion(),
                type,
                eventFact.earnedOn(),
                eventFact.evidenceKind(),
                eventFact.evidenceReference(),
                origin,
                detectedAt);
        return events.append(draft).event();
    }

    private void reconcilePendingAnnouncements(long guildId, long participantId) {
        Map<AchievementKey, AchievementAwardState.Snapshot> states = awardStates.findAll(guildId, participantId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        state -> state.key().achievementKey(), state -> state, (left, right) -> {
                            throw new IllegalStateException("duplicate achievement award state");
                        }, LinkedHashMap::new));
        for (AchievementAnnouncement.Snapshot pending : announcements.findPending(guildId, participantId)) {
            AchievementAnnouncement.Key key = pending.registration().key();
            List<UUID> previous = announcements.findItems(key).stream()
                    .map(AchievementAnnouncement.Item::eventId)
                    .toList();
            List<UUID> retained = previous.stream()
                    .filter(eventId -> remainsAnnounceable(eventId, states))
                    .toList();
            if (previous.equals(retained)) {
                continue;
            }
            if (!announcements.replaceItems(key, retained)) {
                throw new IllegalStateException("pending achievement announcement cannot be reduced");
            }
            if (retained.isEmpty()) {
                if (!announcements.markSuppressed(key, clock.instant())) {
                    throw new IllegalStateException("empty achievement announcement cannot be suppressed");
                }
            } else if (!announcements.updatePendingContent(
                    key, HANDOFF_RENDERER_VERSION, handoffFingerprint(pending.registration(), retained))) {
                throw new IllegalStateException("pending achievement announcement content cannot be refreshed");
            }
        }
    }

    private boolean remainsAnnounceable(
            UUID eventId, Map<AchievementKey, AchievementAwardState.Snapshot> currentStates) {
        AchievementEventFact.Snapshot event = events.find(eventId)
                .orElseThrow(() -> new IllegalStateException("announcement references missing achievement event: " + eventId));
        AchievementAwardState.Snapshot state = currentStates.get(event.fact().awardKey().achievementKey());
        return state != null && state.write().status() == AchievementAwardState.Status.ACTIVE;
    }

    private LiveUnlockBatch createLiveUnlockBatch(
            ReconciliationRequest request,
            LiveAnnouncementTarget target,
            List<AchievementEventFact.Snapshot> activationEvents) {
        List<AchievementEventFact.Snapshot> eligible = activationEvents.stream()
                .filter(event -> !announcements.wasSynchronized(
                        request.guildId(), request.participantId(), event.fact().awardKey().achievementKey()))
                .sorted(Comparator.comparingInt(event -> catalogOrder.get(event.fact().awardKey().achievementKey())))
                .toList();
        if (eligible.isEmpty()) {
            return new LiveUnlockBatch(Optional.empty(), List.of());
        }

        String idempotencyKey = "achievement-live-batch:v1:" + sha256(
                request.guildId() + "|" + request.participantId() + "|" + catalog.version().value() + "|"
                        + request.processingOrigin().name() + "|" + target.triggerIdentity());
        List<UUID> eventIds = eligible.stream().map(event -> event.fact().eventId()).toList();
        AchievementAnnouncement.Registration registration = new AchievementAnnouncement.Registration(
                request.guildId(), target.channelId(), request.participantId(), catalog.version(),
                AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH, idempotencyKey,
                HANDOFF_RENDERER_VERSION,
                handoffFingerprint(request.guildId(), target.channelId(), request.participantId(), idempotencyKey, eventIds));
        AchievementAnnouncement.Snapshot announcement = announcements.register(registration);
        if (!announcements.replaceItems(registration.key(), eventIds)) {
            throw new IllegalStateException("live achievement announcement cannot be populated");
        }
        return new LiveUnlockBatch(Optional.of(announcement.registration().key()), eventIds);
    }

    private AchievementAwardState.Write activeWrite(AchievementEvidence evidence, Instant detectedAt) {
        return new AchievementAwardState.Write(
                catalog.version(),
                AchievementAwardState.Status.ACTIVE,
                evidence.earnedOn(),
                detectedAt,
                evidence.kind(),
                evidence.reference(),
                Optional.empty());
    }

    private static AchievementAwardState.Write invalidatedWrite(
            AchievementAwardState.Snapshot current, Instant invalidatedAt) {
        AchievementAwardState.Write previous = current.write();
        return new AchievementAwardState.Write(
                previous.definitionVersion(),
                AchievementAwardState.Status.INVALIDATED,
                previous.earnedOn(),
                invalidatedAt,
                previous.evidenceKind(),
                previous.evidenceReference(),
                Optional.of(invalidatedAt));
    }

    private static boolean sameActiveProjection(
            AchievementAwardState.Write state, AchievementAwardState.Write evaluated) {
        return state.status() == AchievementAwardState.Status.ACTIVE
                && state.definitionVersion().equals(evaluated.definitionVersion())
                && state.earnedOn().equals(evaluated.earnedOn())
                && state.evidenceKind() == evaluated.evidenceKind()
                && state.evidenceReference().equals(evaluated.evidenceReference());
    }

    private static Map<AchievementKey, AchievementEvidence> evidenceByKey(AchievementEvaluation evaluation) {
        Map<AchievementKey, AchievementEvidence> evidence = new LinkedHashMap<>();
        for (AchievementEvidence item : evaluation.achievements()) {
            if (evidence.put(item.achievementKey(), item) != null) {
                throw new IllegalStateException("evaluator returned duplicate achievement evidence");
            }
        }
        return evidence;
    }

    private static Map<AchievementKey, Integer> catalogOrder(AchievementDefinitionCatalog catalog) {
        Map<AchievementKey, Integer> order = new HashMap<>();
        for (int index = 0; index < catalog.definitions().size(); index++) {
            order.put(catalog.definitions().get(index).key(), index);
        }
        return Map.copyOf(order);
    }

    private String handoffFingerprint(AchievementAnnouncement.Registration registration, List<UUID> eventIds) {
        return handoffFingerprint(
                registration.guildId(), registration.channelId(), registration.participantId(),
                registration.idempotencyKey(), eventIds);
    }

    private String handoffFingerprint(
            long guildId, long channelId, long participantId, String idempotencyKey, List<UUID> eventIds) {
        StringBuilder canonical = new StringBuilder(HANDOFF_RENDERER_VERSION)
                .append('|').append(guildId)
                .append('|').append(channelId)
                .append('|').append(participantId)
                .append('|').append(catalog.version().value())
                .append('|').append(idempotencyKey);
        for (UUID eventId : eventIds) {
            AchievementEventFact.Snapshot event = events.find(eventId)
                    .orElseThrow(() -> new IllegalStateException("achievement event disappeared before handoff"));
            AchievementDefinition definition = catalog.find(event.fact().awardKey().achievementKey())
                    .orElseThrow(() -> new IllegalStateException("event references unknown achievement key"));
            canonical.append('|').append(eventId)
                    .append('|').append(definition.key().value())
                    .append('|').append(definition.displayName())
                    .append('|').append(definition.description());
        }
        return sha256(canonical.toString());
    }

    private static String transitionIdentity(
            AchievementAwardState.Key key,
            Optional<AchievementAwardState.Snapshot> before,
            AchievementAwardState.Write after,
            AchievementEventFact.Type type,
            AchievementEventFact.ProcessingOrigin origin) {
        return "v1|" + key.guildId() + '|' + key.participantId() + '|' + key.achievementKey().value() + '|'
                + before.map(AchievementReconciliationService::stateIdentity).orElse("missing") + '|'
                + writeIdentity(after) + '|' + type.name() + '|' + origin.name();
    }

    private static String stateIdentity(AchievementAwardState.Snapshot state) {
        return state.lockVersion().value() + ":" + writeIdentity(state.write());
    }

    private static String writeIdentity(AchievementAwardState.Write write) {
        return write.definitionVersion().value() + ':' + write.status().name() + ':' + write.earnedOn()
                + ':' + write.evidenceKind().name() + ':' + write.evidenceReference();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public enum TransitionType { UNLOCK, NO_OP, INVALIDATE, REACTIVATE }

    public record ReconciliationRequest(
            long guildId,
            long participantId,
            AchievementEventFact.ProcessingOrigin processingOrigin,
            Optional<LiveAnnouncementTarget> liveAnnouncementTarget) {
        public ReconciliationRequest {
            if (guildId <= 0 || participantId <= 0) {
                throw new IllegalArgumentException("guildId and participantId must be positive");
            }
            Objects.requireNonNull(processingOrigin, "processingOrigin");
            liveAnnouncementTarget = Objects.requireNonNull(liveAnnouncementTarget, "liveAnnouncementTarget");
            if (liveAnnouncementTarget.isPresent() && !allowsLiveAnnouncement(processingOrigin)) {
                throw new IllegalArgumentException("processing origin does not allow a live achievement announcement");
            }
        }
    }

    public record LiveAnnouncementTarget(long channelId, String triggerIdentity) {
        public LiveAnnouncementTarget {
            if (channelId <= 0) {
                throw new IllegalArgumentException("channelId must be positive");
            }
            Objects.requireNonNull(triggerIdentity, "triggerIdentity");
            if (triggerIdentity.isBlank()) {
                throw new IllegalArgumentException("triggerIdentity must not be blank");
            }
        }
    }

    public record Transition(AchievementKey achievementKey, TransitionType type, Optional<UUID> eventId) {
        public Transition {
            Objects.requireNonNull(achievementKey, "achievementKey");
            Objects.requireNonNull(type, "type");
            eventId = Objects.requireNonNull(eventId, "eventId");
            if ((type == TransitionType.UNLOCK || type == TransitionType.INVALIDATE || type == TransitionType.REACTIVATE)
                    != eventId.isPresent()) {
                throw new IllegalArgumentException("only a persisted state transition may carry an event ID");
            }
        }
    }

    public record LiveUnlockBatch(Optional<AchievementAnnouncement.Key> announcementKey, List<UUID> eventIds) {
        public LiveUnlockBatch {
            announcementKey = Objects.requireNonNull(announcementKey, "announcementKey");
            eventIds = List.copyOf(Objects.requireNonNull(eventIds, "eventIds"));
            if (announcementKey.isPresent() != !eventIds.isEmpty()) {
                throw new IllegalArgumentException("announcement key is required exactly for a non-empty batch");
            }
        }
    }

    public record ReconciliationResult(List<Transition> transitions, Optional<LiveUnlockBatch> liveUnlockBatch) {
        public ReconciliationResult {
            transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions"));
            liveUnlockBatch = Objects.requireNonNull(liveUnlockBatch, "liveUnlockBatch");
        }
    }

    private record AppliedTransition(Transition transition, Optional<AchievementEventFact.Snapshot> activationEvent) {
        static AppliedTransition noOp(AchievementKey key) {
            return new AppliedTransition(new Transition(key, TransitionType.NO_OP, Optional.empty()), Optional.empty());
        }

        static AppliedTransition changed(
                AchievementKey key, TransitionType type, AchievementEventFact.Snapshot event) {
            return new AppliedTransition(new Transition(key, type, Optional.of(event.fact().eventId())),
                    type == TransitionType.UNLOCK || type == TransitionType.REACTIVATE
                            ? Optional.of(event)
                            : Optional.empty());
        }
    }

    private static final class StaleHistorySnapshotException extends RuntimeException {
        private static final StaleHistorySnapshotException INSTANCE = new StaleHistorySnapshotException();

        private StaleHistorySnapshotException() {
            super(null, null, false, false);
        }
    }

    private static boolean allowsLiveAnnouncement(AchievementEventFact.ProcessingOrigin origin) {
        return switch (origin) {
            case LIVE_SUBMISSION, NORMAL_CORRECTION, DAY_CLOSE, PARTICIPATION_CHANGE -> true;
            case BOOTSTRAP, REPLAY, IMPORT, BACKFILL, ADMINISTRATIVE_REPAIR -> false;
        };
    }
}
