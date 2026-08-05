package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordBootstrapProjection;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordStateInitialization;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdate;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinition;
import de.venomenon.gridwordsbot.domain.record.RecordComparison;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.StreakCrossingKey;
import de.venomenon.gridwordsbot.domain.record.StreakRunIdentity;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Set;

/** The only writer of coupled record-state and audit-anchor changes. */
public class RecordStateService {
    private final RecordStateStore stateStore;
    private final RecordEventStore eventStore;
    private final RecordTransactionRunner transactions;
    private final RecordDefinitionCatalog catalog;

    public RecordStateService(RecordStateStore stateStore, RecordEventStore eventStore, RecordTransactionRunner transactions,
            RecordDefinitionCatalog catalog) {
        this.stateStore = java.util.Objects.requireNonNull(stateStore); this.eventStore = java.util.Objects.requireNonNull(eventStore);
        this.transactions = java.util.Objects.requireNonNull(transactions);
        this.catalog = java.util.Objects.requireNonNull(catalog);
    }

    /** Idempotently creates a state and its one stable, silent bootstrap audit anchor in one transaction. */
    public boolean initializeSilently(RecordBootstrapProjection.Candidate candidate, String bootstrapKey, Instant detectedAt) {
        return transactions.inTransaction(() -> initializeInTransaction(candidate, bootstrapKey, detectedAt));
    }
    private boolean initializeInTransaction(RecordBootstrapProjection.Candidate candidate, String bootstrapKey, Instant detectedAt) {
        java.util.Objects.requireNonNull(candidate); java.util.Objects.requireNonNull(bootstrapKey); java.util.Objects.requireNonNull(detectedAt);
        RecordStateInitialization initialized = stateStore.initialize(candidate.key(), candidate.write());
        if (initialized instanceof RecordStateInitialization.Existing) return false;
        RecordStateSnapshot state = initialized.snapshot();
        String stable = bootstrapKey + ":" + state.key().definitionKey().value() + ":" + state.key().scopeKey();
        UUID eventId = UUID.nameUUIDFromBytes(stable.getBytes(StandardCharsets.UTF_8));
        RecordStateWrite write = candidate.write();
        eventStore.append(new RecordEventDraft(eventId, "record-initialized:" + stable, state.key(),
                RecordEventType.RECORD_INITIALIZED, Optional.empty(), write.value(), Optional.empty(), write.holderPlayerId(),
                Optional.empty(), write.source(), stable, RecordProcessingOrigin.BOOTSTRAP, detectedAt));
        return true;
    }

    /** Reconciles one projection to its exact canonical source, retrying only optimistic-lock conflicts. */
    public RebuildResult rebuild(RecordBootstrapProjection.Candidate candidate, String bootstrapKey, Instant detectedAt) {
        return transactions.inTransaction(() -> rebuildInTransaction(candidate, bootstrapKey, detectedAt));
    }
    private RebuildResult rebuildInTransaction(RecordBootstrapProjection.Candidate candidate, String bootstrapKey, Instant detectedAt) {
        for (int attempts = 0; attempts < 3; attempts++) {
            Optional<RecordStateSnapshot> current = stateStore.find(candidate.key());
            if (current.isEmpty()) {
                return initializeSilently(candidate, bootstrapKey, detectedAt) ? RebuildResult.CREATED : RebuildResult.RETRY_EXHAUSTED;
            }
            RecordStateSnapshot state = current.orElseThrow();
            if (same(state, candidate.write())) return RebuildResult.UNCHANGED;
            RecordStateUpdateResult updated = stateStore.update(new RecordStateUpdate(candidate.key(), state.lockVersion(), candidate.write()));
            if (updated.status() == RecordStateUpdateResult.Status.UPDATED) return RebuildResult.REPLACED;
            if (updated.status() == RecordStateUpdateResult.Status.UNCHANGED) return RebuildResult.UNCHANGED;
        }
        return RebuildResult.RETRY_EXHAUSTED;
    }
    /** Removes a state only after a fresh CAS read; audit facts intentionally remain. */
    public RebuildResult removeIfNoSource(de.venomenon.gridwordsbot.domain.record.RecordStateKey key) {
        return transactions.inTransaction(() -> {
            for (int attempts = 0; attempts < 3; attempts++) {
                Optional<RecordStateSnapshot> current = stateStore.find(key);
                if (current.isEmpty()) return RebuildResult.UNCHANGED;
                if (stateStore.remove(key, current.orElseThrow().lockVersion())) return RebuildResult.REMOVED;
            }
            return RebuildResult.RETRY_EXHAUSTED;
        });
    }
    public List<RecordStateSnapshot> states(long guildId, de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion version) {
        return stateStore.findAll(guildId, version);
    }
    /** Running states are the durable proof that the corresponding live crossing was consumed at bootstrap. */
    public Set<StreakCrossingKey> consumedCrossings(long guildId, de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion version) {
        return states(guildId, version).stream().filter(RecordStateSnapshot::running)
                .filter(state -> state.source() instanceof RecordSourceReference.StreakRun)
                .map(state -> {
                    RecordSourceReference.StreakRun source = (RecordSourceReference.StreakRun) state.source();
                    RecordScope owner = switch (source.owner()) {
                        case RecordSourceReference.StreakRunOwner.Player player -> new RecordScope.Personal(player.playerId());
                        case RecordSourceReference.StreakRunOwner.Shared ignored -> new RecordScope.Shared();
                    };
                    return new StreakCrossingKey(version, state.key().definitionKey(),
                            new StreakRunIdentity(source.metric(), owner, source.startDate()));
                }).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static boolean same(RecordStateSnapshot state, RecordStateWrite write) {
        return state.holderPlayerId().equals(write.holderPlayerId()) && state.value().equals(write.value())
                && state.source().equals(write.source()) && state.running() == write.running();
    }
    public enum RebuildResult { CREATED, REPLACED, REMOVED, UNCHANGED, RETRY_EXHAUSTED }
}
