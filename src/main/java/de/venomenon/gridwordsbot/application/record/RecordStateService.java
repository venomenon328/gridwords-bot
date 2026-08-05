package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordBootstrapProjection;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordStateInitialization;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The only writer of coupled record-state and audit-anchor changes. */
public class RecordStateService {
    private final RecordStateStore stateStore;
    private final RecordEventStore eventStore;
    private final RecordTransactionRunner transactions;

    public RecordStateService(RecordStateStore stateStore, RecordEventStore eventStore, RecordTransactionRunner transactions) {
        this.stateStore = java.util.Objects.requireNonNull(stateStore); this.eventStore = java.util.Objects.requireNonNull(eventStore);
        this.transactions = java.util.Objects.requireNonNull(transactions);
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
}
