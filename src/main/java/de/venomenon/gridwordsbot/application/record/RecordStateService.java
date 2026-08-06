package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordBootstrapProjection;
import de.venomenon.gridwordsbot.domain.record.RecordComparison;
import de.venomenon.gridwordsbot.domain.record.RecordDefinition;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordEventValidity;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateInitialization;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdate;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.domain.record.StreakCrossingKey;
import de.venomenon.gridwordsbot.domain.record.StreakRunIdentity;
import de.venomenon.gridwordsbot.port.out.RecordEventIdempotencyConflictException;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The only writer of coupled record-state and audit-anchor changes. */
public class RecordStateService {
    private final RecordStateStore stateStore;
    private final RecordEventStore eventStore;
    private final RecordTransactionRunner transactions;
    private final RecordDefinitionCatalog catalog;

    public RecordStateService(
            RecordStateStore stateStore,
            RecordEventStore eventStore,
            RecordTransactionRunner transactions,
            RecordDefinitionCatalog catalog) {
        this.stateStore = java.util.Objects.requireNonNull(stateStore);
        this.eventStore = java.util.Objects.requireNonNull(eventStore);
        this.transactions = java.util.Objects.requireNonNull(transactions);
        this.catalog = java.util.Objects.requireNonNull(catalog);
    }

    /** Idempotently creates a state and its one stable, silent bootstrap audit anchor in one transaction. */
    public boolean initializeSilently(
            RecordBootstrapProjection.Candidate candidate,
            String bootstrapKey,
            Instant detectedAt) {
        return transactions.inTransaction(() -> initializeInTransaction(candidate, bootstrapKey, detectedAt));
    }

    private boolean initializeInTransaction(
            RecordBootstrapProjection.Candidate candidate,
            String bootstrapKey,
            Instant detectedAt) {
        java.util.Objects.requireNonNull(candidate);
        java.util.Objects.requireNonNull(bootstrapKey);
        java.util.Objects.requireNonNull(detectedAt);
        RecordStateInitialization initialized = stateStore.initialize(candidate.key(), candidate.write());
        RecordStateSnapshot state = initialized.snapshot();
        ensureInitializationAnchor(
                state,
                bootstrapKey,
                detectedAt,
                InitializationAnchorValidation.STRICT_INITIALIZATION_REPLAY);
        return initialized instanceof RecordStateInitialization.Created;
    }

    private boolean initializeForReconciliation(
            RecordBootstrapProjection.Candidate candidate,
            String bootstrapKey,
            Instant detectedAt) {
        java.util.Objects.requireNonNull(candidate);
        java.util.Objects.requireNonNull(bootstrapKey);
        java.util.Objects.requireNonNull(detectedAt);
        RecordStateInitialization initialized = stateStore.initialize(candidate.key(), candidate.write());
        ensureInitializationAnchor(
                initialized.snapshot(),
                bootstrapKey,
                detectedAt,
                InitializationAnchorValidation.HISTORICAL_IDENTITY_ONLY);
        return initialized instanceof RecordStateInitialization.Created;
    }

    /**
     * Restores a missing initialization anchor and validates the immutable identity
     * of an existing one. Reconciliation keeps the original historical payload,
     * while a direct initialization replay remains strict.
     */
    private void ensureInitializationAnchor(
            RecordStateSnapshot state,
            String bootstrapKey,
            Instant detectedAt,
            InitializationAnchorValidation validation) {
        String stable = bootstrapKey + ":" + state.key().definitionKey().value() + ":" + state.key().scopeKey();
        UUID eventId = UUID.nameUUIDFromBytes(stable.getBytes(StandardCharsets.UTF_8));

        Optional<RecordEventSnapshot> existing = eventStore.find(eventId);
        if (existing.isPresent()) {
            validateInitializationAnchor(
                    existing.orElseThrow(),
                    state,
                    stable,
                    eventId,
                    detectedAt,
                    validation);
            return;
        }

        RecordEventDraft draft = initializationAnchor(state, stable, eventId, detectedAt);
        RecordEventSnapshot persisted;
        try {
            persisted = eventStore.append(draft).snapshot();
        } catch (RecordEventIdempotencyConflictException conflict) {
            persisted = eventStore.find(eventId).orElseThrow(() -> conflict);
        }
        validateInitializationAnchor(
                persisted,
                state,
                stable,
                eventId,
                detectedAt,
                validation);
    }

    private static RecordEventDraft initializationAnchor(
            RecordStateSnapshot state,
            String stable,
            UUID eventId,
            Instant detectedAt) {
        return new RecordEventDraft(
                eventId,
                "record-initialized:" + stable,
                state.key(),
                RecordEventType.RECORD_INITIALIZED,
                Optional.empty(),
                state.value(),
                Optional.empty(),
                state.holderPlayerId(),
                Optional.empty(),
                state.source(),
                stable,
                RecordProcessingOrigin.BOOTSTRAP,
                detectedAt);
    }

    private static void validateInitializationAnchor(
            RecordEventSnapshot snapshot,
            RecordStateSnapshot state,
            String stable,
            UUID eventId,
            Instant detectedAt,
            InitializationAnchorValidation validation) {
        RecordEventDraft draft = snapshot.draft();
        boolean valid = snapshot.validity() == RecordEventValidity.VALID
                && draft.eventId().equals(eventId)
                && draft.idempotencyKey().equals("record-initialized:" + stable)
                && draft.stateKey().equals(state.key())
                && draft.type() == RecordEventType.RECORD_INITIALIZED
                && draft.previousValue().isEmpty()
                && draft.previousHolderPlayerId().isEmpty()
                && draft.previousSource().isEmpty()
                && draft.triggerKey().equals(stable)
                && draft.processingOrigin() == RecordProcessingOrigin.BOOTSTRAP
                && draft.detectedAt().equals(detectedAt);
        if (validation == InitializationAnchorValidation.STRICT_INITIALIZATION_REPLAY) {
            valid = valid
                    && draft.newValue().equals(state.value())
                    && draft.newHolderPlayerId().equals(state.holderPlayerId())
                    && draft.newSource().equals(state.source());
        }
        if (!valid) {
            throw new RecordEventIdempotencyConflictException("record-initialized:" + stable);
        }
    }

    /** Reconciles one projection to its exact canonical source, retrying only optimistic-lock conflicts. */
    public RebuildResult rebuild(
            RecordBootstrapProjection.Candidate candidate,
            String bootstrapKey,
            Instant detectedAt) {
        return transactions.inTransaction(() -> rebuildInTransaction(candidate, bootstrapKey, detectedAt));
    }

    private RebuildResult rebuildInTransaction(
            RecordBootstrapProjection.Candidate candidate,
            String bootstrapKey,
            Instant detectedAt) {
        for (int attempts = 0; attempts < 3; attempts++) {
            Optional<RecordStateSnapshot> current = stateStore.find(candidate.key());
            if (current.isEmpty()) {
                if (initializeForReconciliation(candidate, bootstrapKey, detectedAt)) return RebuildResult.CREATED;
                continue;
            }
            RecordStateSnapshot state = current.orElseThrow();
            if (same(state, candidate.write()) || stateIsAtLeastAsGood(state, candidate.write())) {
                return RebuildResult.UNCHANGED;
            }
            RecordStateUpdateResult updated = stateStore.update(
                    new RecordStateUpdate(candidate.key(), state.lockVersion(), candidate.write()));
            if (updated.status() == RecordStateUpdateResult.Status.UPDATED) return RebuildResult.REPLACED;
            if (updated.status() == RecordStateUpdateResult.Status.UNCHANGED) return RebuildResult.UNCHANGED;
        }
        return RebuildResult.RETRY_EXHAUSTED;
    }

    /**
     * Applies the exact result of a complete canonical-history recomputation.
     * This is intentionally distinct from {@link #rebuild}, whose live-safe
     * path never lets a stale candidate lower an already better state.
     */
    public RebuildResult reconcileCanonicalTarget(
            RecordBootstrapProjection.Candidate candidate,
            String bootstrapKey,
            Instant detectedAt) {
        return transactions.inTransaction(
                () -> reconcileCanonicalTargetWithinTransaction(candidate, bootstrapKey, detectedAt));
    }

    /**
     * The live processor owns the surrounding short transaction.  Keeping this
     * operation here preserves this service as the sole record-state writer
     * without opening an independent nested transaction for every state.
     */
    public RebuildResult reconcileCanonicalTargetWithinTransaction(
            RecordBootstrapProjection.Candidate candidate,
            String bootstrapKey,
            Instant detectedAt) {
        for (int attempts = 0; attempts < 3; attempts++) {
            Optional<RecordStateSnapshot> current = stateStore.find(candidate.key());
            if (current.isEmpty()) {
                if (initializeForReconciliation(candidate, bootstrapKey, detectedAt)) return RebuildResult.CREATED;
                continue;
            }
            RecordStateSnapshot state = current.orElseThrow();
            ensureInitializationAnchor(
                    state,
                    bootstrapKey,
                    detectedAt,
                    InitializationAnchorValidation.HISTORICAL_IDENTITY_ONLY);
            if (same(state, candidate.write())) return RebuildResult.UNCHANGED;
            RecordStateUpdateResult updated = stateStore.update(
                    new RecordStateUpdate(candidate.key(), state.lockVersion(), candidate.write()));
            if (updated.status() == RecordStateUpdateResult.Status.UPDATED) return RebuildResult.REPLACED;
            if (updated.status() == RecordStateUpdateResult.Status.UNCHANGED) return RebuildResult.UNCHANGED;
        }
        return RebuildResult.RETRY_EXHAUSTED;
    }

    /**
     * Applies an immediately observed live candidate without creating a
     * bootstrap anchor.  A stale candidate may never replace an equal or
     * better state that won the CAS race in the meantime.
     */
    public RebuildResult applyLiveCandidateWithinTransaction(RecordBootstrapProjection.Candidate candidate) {
        return applyLiveCandidateTransitionWithinTransaction(candidate).result();
    }

    /**
     * Performs the live CAS and exposes the state that actually won it.  The
     * processor uses this as the sole proof that an audit fact may be emitted:
     * a candidate that loses a race has no changed transition and must stay
     * silent.
     */
    public StateTransition applyLiveCandidateTransitionWithinTransaction(
            RecordBootstrapProjection.Candidate candidate) {
        java.util.Objects.requireNonNull(candidate, "candidate");
        for (int attempts = 0; attempts < 3; attempts++) {
            Optional<RecordStateSnapshot> current = stateStore.find(candidate.key());
            if (current.isEmpty()) {
                RecordStateInitialization initialized = stateStore.initialize(candidate.key(), candidate.write());
                if (initialized instanceof RecordStateInitialization.Created created) {
                    return new StateTransition(RebuildResult.CREATED, Optional.empty(), Optional.of(created.snapshot()));
                }
                continue;
            }
            RecordStateSnapshot state = current.orElseThrow();
            if (same(state, candidate.write()) || stateIsAtLeastAsGood(state, candidate.write())) {
                return new StateTransition(RebuildResult.UNCHANGED, Optional.of(state), Optional.of(state));
            }
            RecordStateUpdateResult updated = stateStore.update(
                    new RecordStateUpdate(candidate.key(), state.lockVersion(), candidate.write()));
            if (updated.status() == RecordStateUpdateResult.Status.UPDATED) {
                RecordStateSnapshot after = stateStore.find(candidate.key())
                        .orElseThrow(() -> new IllegalStateException("updated record state is missing"));
                return new StateTransition(RebuildResult.REPLACED, Optional.of(state), Optional.of(after));
            }
            if (updated.status() == RecordStateUpdateResult.Status.UNCHANGED) {
                RecordStateSnapshot after = stateStore.find(candidate.key()).orElse(state);
                return new StateTransition(RebuildResult.UNCHANGED, Optional.of(state), Optional.of(after));
            }
        }
        return new StateTransition(RebuildResult.RETRY_EXHAUSTED, Optional.empty(), Optional.empty());
    }

    /**
     * Applies an exact, canonically recomputed correction target.  In contrast
     * to the live path a correction is permitted to fall back to a worse next
     * canonical source or to remove a state that has no remaining source.
     * The caller already owns the outer record transaction.
     */
    public RebuildResult reconcileCorrectionTargetWithinTransaction(
            Optional<RecordBootstrapProjection.Candidate> target,
            de.venomenon.gridwordsbot.domain.record.RecordStateKey key) {
        return reconcileCorrectionTargetTransitionWithinTransaction(target, key).result();
    }

    /** Exact correction counterpart to the live transition API. */
    public StateTransition reconcileCorrectionTargetTransitionWithinTransaction(
            Optional<RecordBootstrapProjection.Candidate> target,
            de.venomenon.gridwordsbot.domain.record.RecordStateKey key) {
        java.util.Objects.requireNonNull(target, "target");
        java.util.Objects.requireNonNull(key, "key");
        if (target.isPresent()) {
            RecordBootstrapProjection.Candidate candidate = target.orElseThrow();
            if (!candidate.key().equals(key)) throw new IllegalArgumentException("correction target key mismatch");
            for (int attempts = 0; attempts < 3; attempts++) {
                Optional<RecordStateSnapshot> current = stateStore.find(key);
                if (current.isEmpty()) {
                    RecordStateInitialization initialized = stateStore.initialize(key, candidate.write());
                    if (initialized instanceof RecordStateInitialization.Created created) {
                        return new StateTransition(RebuildResult.CREATED, Optional.empty(), Optional.of(created.snapshot()));
                    }
                    continue;
                }
                RecordStateSnapshot state = current.orElseThrow();
                if (same(state, candidate.write())) {
                    return new StateTransition(RebuildResult.UNCHANGED, Optional.of(state), Optional.of(state));
                }
                RecordStateUpdateResult updated = stateStore.update(
                        new RecordStateUpdate(key, state.lockVersion(), candidate.write()));
                if (updated.status() == RecordStateUpdateResult.Status.UPDATED) {
                    RecordStateSnapshot after = stateStore.find(key)
                            .orElseThrow(() -> new IllegalStateException("updated record state is missing"));
                    return new StateTransition(RebuildResult.REPLACED, Optional.of(state), Optional.of(after));
                }
                if (updated.status() == RecordStateUpdateResult.Status.UNCHANGED) {
                    RecordStateSnapshot after = stateStore.find(key).orElse(state);
                    return new StateTransition(RebuildResult.UNCHANGED, Optional.of(state), Optional.of(after));
                }
            }
            return new StateTransition(RebuildResult.RETRY_EXHAUSTED, Optional.empty(), Optional.empty());
        }
        return removeAbsentCanonicalTargetTransitionWithinTransaction(key);
    }

    private RebuildResult removeAbsentCanonicalTargetWithinTransaction(
            de.venomenon.gridwordsbot.domain.record.RecordStateKey key) {
        return removeAbsentCanonicalTargetTransitionWithinTransaction(key).result();
    }

    private StateTransition removeAbsentCanonicalTargetTransitionWithinTransaction(
            de.venomenon.gridwordsbot.domain.record.RecordStateKey key) {
        for (int attempts = 0; attempts < 3; attempts++) {
            Optional<RecordStateSnapshot> current = stateStore.find(key);
            if (current.isEmpty()) {
                return new StateTransition(RebuildResult.UNCHANGED, Optional.empty(), Optional.empty());
            }
            RecordStateSnapshot state = current.orElseThrow();
            if (stateStore.remove(key, state.lockVersion())) {
                return new StateTransition(RebuildResult.REMOVED, Optional.of(state), Optional.empty());
            }
        }
        return new StateTransition(RebuildResult.RETRY_EXHAUSTED, Optional.empty(), Optional.empty());
    }

    /** Removes a state only after a fresh CAS read; audit facts intentionally remain. */
    public RebuildResult removeIfNoSource(de.venomenon.gridwordsbot.domain.record.RecordStateKey key) {
        return removeAbsentCanonicalTarget(key);
    }

    /** Removes only a key proven absent from a freshly computed canonical target set. */
    public RebuildResult removeAbsentCanonicalTarget(
            de.venomenon.gridwordsbot.domain.record.RecordStateKey key) {
        return transactions.inTransaction(() -> {
            for (int attempts = 0; attempts < 3; attempts++) {
                Optional<RecordStateSnapshot> current = stateStore.find(key);
                if (current.isEmpty()) return RebuildResult.UNCHANGED;
                if (stateStore.remove(key, current.orElseThrow().lockVersion())) {
                    return RebuildResult.REMOVED;
                }
            }
            return RebuildResult.RETRY_EXHAUSTED;
        });
    }

    public List<RecordStateSnapshot> states(
            long guildId,
            de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion version) {
        return stateStore.findAll(guildId, version);
    }

    /** Running states are the durable proof that the corresponding live crossing was consumed at bootstrap. */
    public Set<StreakCrossingKey> consumedCrossings(
            long guildId,
            de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion version) {
        return states(guildId, version).stream()
                .filter(RecordStateSnapshot::running)
                .filter(state -> state.source() instanceof RecordSourceReference.StreakRun)
                .map(state -> {
                    RecordSourceReference.StreakRun source =
                            (RecordSourceReference.StreakRun) state.source();
                    RecordScope owner = switch (source.owner()) {
                        case RecordSourceReference.StreakRunOwner.Player player ->
                                new RecordScope.Personal(player.playerId());
                        case RecordSourceReference.StreakRunOwner.Shared ignored ->
                                new RecordScope.Shared();
                    };
                    return new StreakCrossingKey(
                            version,
                            state.key().definitionKey(),
                            new StreakRunIdentity(source.metric(), owner, source.startDate()));
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static boolean same(RecordStateSnapshot state, RecordStateWrite write) {
        return state.holderPlayerId().equals(write.holderPlayerId())
                && state.value().equals(write.value())
                && state.source().equals(write.source())
                && state.sourceGameFirstAcceptedAt().equals(write.sourceGameFirstAcceptedAt())
                && state.running() == write.running();
    }

    private boolean stateIsAtLeastAsGood(RecordStateSnapshot state, RecordStateWrite candidate) {
        RecordDefinition<?> definition = catalog.find(state.key().definitionKey())
                .filter(found -> found.definitionVersion().equals(state.key().definitionVersion()))
                .orElseThrow(() -> new IllegalStateException(
                        "record state references an unknown active definition"));
        RecordComparison comparison = definition.compareValues(candidate.value(), state.value());
        if (comparison == RecordComparison.WORSE) return true;
        if (comparison == RecordComparison.BETTER) return false;
        return canonicalSourceOrder(state, candidate) <= 0;
    }

    private static int canonicalSourceOrder(RecordStateSnapshot left, RecordStateWrite right) {
        if (left.source() instanceof RecordSourceReference.GameResult leftResult
                && right.source() instanceof RecordSourceReference.GameResult rightResult) {
            int date = leftResult.gameDate().compareTo(rightResult.gameDate());
            if (date != 0) return date;
            int acceptedAt = compareAcceptanceTime(
                    left.sourceGameFirstAcceptedAt(), right.sourceGameFirstAcceptedAt());
            if (acceptedAt != 0) return acceptedAt;
            return Long.compare(leftResult.resultId(), rightResult.resultId());
        }
        if (left.source() instanceof RecordSourceReference.StreakRun leftRun
                && right.source() instanceof RecordSourceReference.StreakRun rightRun) {
            int metric = leftRun.metric().compareTo(rightRun.metric());
            if (metric != 0) return metric;
            de.venomenon.gridwordsbot.domain.record.StreakRecordValue leftValue =
                    (de.venomenon.gridwordsbot.domain.record.StreakRecordValue) left.value();
            de.venomenon.gridwordsbot.domain.record.StreakRecordValue rightValue =
                    (de.venomenon.gridwordsbot.domain.record.StreakRecordValue) right.value();
            int end = leftValue.endDate().compareTo(rightValue.endDate());
            if (end != 0) return end;
            int start = leftRun.startDate().compareTo(rightRun.startDate());
            return start != 0
                    ? start
                    : leftRun.owner().toString().compareTo(rightRun.owner().toString());
        }
        return left.source().sourceType().compareTo(right.source().sourceType());
    }

    /** A complete timestamp is canonical before a legacy state whose ordering metadata is absent. */
    private static int compareAcceptanceTime(Optional<Instant> left, Optional<Instant> right) {
        if (left.isPresent() && right.isPresent()) {
            return left.orElseThrow().compareTo(right.orElseThrow());
        }
        if (left.isPresent()) return -1;
        if (right.isPresent()) return 1;
        return 0;
    }

    private enum InitializationAnchorValidation {
        STRICT_INITIALIZATION_REPLAY,
        HISTORICAL_IDENTITY_ONLY
    }

    public enum RebuildResult {
        CREATED,
        REPLACED,
        REMOVED,
        UNCHANGED,
        RETRY_EXHAUSTED
    }

    /** A successful write is the only authorization for dependent audit facts. */
    public record StateTransition(
            RebuildResult result,
            Optional<RecordStateSnapshot> before,
            Optional<RecordStateSnapshot> after) {
        public StateTransition {
            java.util.Objects.requireNonNull(result, "result");
            before = java.util.Objects.requireNonNull(before, "before");
            after = java.util.Objects.requireNonNull(after, "after");
        }

        public boolean changed() {
            return result == RebuildResult.CREATED || result == RebuildResult.REPLACED || result == RebuildResult.REMOVED;
        }
    }
}
