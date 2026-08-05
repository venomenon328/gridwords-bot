package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordBootstrapReadServiceTest {
    private static final RecordBootstrapKey KEY = new RecordBootstrapKey(1, RecordDefinitionVersion.RECORDS_V1);
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void publicReadinessIsOnlyReadyAfterSuccessfulBootstrap() {
        MemoryStore store = new MemoryStore();
        RecordBootstrapReadService service = new RecordBootstrapReadService(store);
        assertThat(service.readiness(KEY)).isEqualTo(RecordBootstrapReadiness.NOT_REGISTERED);

        for (RecordWorkState state : new RecordWorkState[] {
                RecordWorkState.OPEN, RecordWorkState.CLAIMED, RecordWorkState.RETRYABLE, RecordWorkState.FAILED_PERMANENT }) {
            store.snapshot = snapshot(state);
            assertThat(service.readiness(KEY)).isNotEqualTo(RecordBootstrapReadiness.READY);
        }
        store.snapshot = snapshot(RecordWorkState.SUCCEEDED);
        assertThat(service.readiness(KEY)).isEqualTo(RecordBootstrapReadiness.READY);
    }

    private static RecordBootstrapSnapshot snapshot(RecordWorkState state) {
        Optional<UUID> token = state == RecordWorkState.CLAIMED ? Optional.of(UUID.randomUUID()) : Optional.empty();
        Optional<Instant> until = state == RecordWorkState.CLAIMED ? Optional.of(NOW.plusSeconds(30)) : Optional.empty();
        Optional<Instant> completed = state == RecordWorkState.SUCCEEDED || state == RecordWorkState.FAILED_PERMANENT ? Optional.of(NOW) : Optional.empty();
        Optional<Instant> retry = state == RecordWorkState.RETRYABLE ? Optional.of(NOW.plusSeconds(30)) : Optional.empty();
        return new RecordBootstrapSnapshot(KEY, state, token, until, Optional.of(NOW), completed, 1, retry,
                Optional.empty(), NOW, NOW);
    }

    private static final class MemoryStore implements RecordBootstrapStore {
        RecordBootstrapSnapshot snapshot;
        @Override public RecordBootstrapSnapshot register(RecordBootstrapKey key) { return snapshot; }
        @Override public Optional<RecordBootstrapSnapshot> find(RecordBootstrapKey key) { return Optional.ofNullable(snapshot); }
        @Override public Optional<RecordLeaseClaim> claim(RecordBootstrapKey key, RecordLeaseClaimRequest request) { return Optional.empty(); }
        @Override public boolean renewLease(RecordBootstrapKey key, UUID token, RecordLeaseClaimRequest request) { return false; }
        @Override public boolean markSucceeded(RecordBootstrapKey key, UUID token, Instant completedAt) { return false; }
        @Override public boolean markRetryableFailure(RecordBootstrapKey key, UUID token, RecordWorkFailure failure, Instant nextRetryAt) { return false; }
        @Override public boolean markPermanentFailure(RecordBootstrapKey key, UUID token, RecordWorkFailure failure, Instant completedAt) { return false; }
    }
}
