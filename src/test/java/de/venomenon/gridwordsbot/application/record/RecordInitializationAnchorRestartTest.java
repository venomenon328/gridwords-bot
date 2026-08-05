package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapProjection;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventAppendResult;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordEventValidity;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordLockVersion;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateInitialization;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdate;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordEventIdempotencyConflictException;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordInitializationAnchorRestartTest {
    private static final long GUILD_ID = 1L;
    private static final long PLAYER_ID = 7L;
    private static final Instant STARTED_AT = Instant.parse("2026-08-05T08:00:00Z");
    private static final RecordStateKey KEY = new RecordStateKey(
            GUILD_ID,
            new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
            RecordDefinitionVersion.RECORDS_V1,
            new RecordScope.Personal(PLAYER_ID));

    @Test
    void canonicalReconciliationKeepsTheHistoricalAnchorAcrossAServiceRestart() {
        Harness harness = new Harness();
        RecordBootstrapProjection.Candidate initialized = candidate(1, 99);
        RecordBootstrapProjection.Candidate canonical = candidate(3, 10);
        RecordStateService firstProcess = harness.service();

        assertThat(firstProcess.initializeSilently(initialized, bootstrapKey(), STARTED_AT)).isTrue();
        assertThat(firstProcess.reconcileCanonicalTarget(canonical, bootstrapKey(), STARTED_AT))
                .isEqualTo(RecordStateService.RebuildResult.REPLACED);

        RecordStateService restartedProcess = harness.service();
        assertThat(restartedProcess.reconcileCanonicalTarget(canonical, bootstrapKey(), STARTED_AT))
                .isEqualTo(RecordStateService.RebuildResult.UNCHANGED);

        assertThat(harness.states.find(KEY).orElseThrow().source()).isEqualTo(canonical.write().source());
        assertThat(harness.events.values).hasSize(1);
        assertThat(harness.events.values.values().iterator().next().draft().newSource())
                .isEqualTo(initialized.write().source());
    }

    @Test
    void coordinatorCompletesAfterReplacementWasCommittedButThePreviousAttemptLostItsLease() {
        Harness harness = new Harness();
        RecordBootstrapProjection.Candidate initialized = candidate(1, 99);
        assertThat(harness.service().initializeSilently(initialized, bootstrapKey(), STARTED_AT)).isTrue();

        RecordHistorySnapshot history = new RecordHistorySnapshot(
                List.of(new RecordHistorySnapshot.Result(
                        10,
                        0,
                        PLAYER_ID,
                        GameType.GRIDWORDS,
                        LocalDate.of(2026, 8, 4),
                        new ShareOutcome.Solved(3, 6),
                        Duration.ofSeconds(50),
                        STARTED_AT)),
                List.of());
        BootstrapStore bootstrapStore = new BootstrapStore();
        bootstrapStore.failNextCompletion = true;

        RecordBootstrapCoordinator firstProcess = new RecordBootstrapCoordinator(
                bootstrapStore,
                guildId -> history,
                harness.service(),
                RecordDefinitionCatalog.recordsV1(),
                Clock.fixed(STARTED_AT, ZoneOffset.UTC));
        assertThat(firstProcess.run(GUILD_ID))
                .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.LOST_LEASE);
        assertThat(((RecordSourceReference.GameResult) harness.states.find(KEY).orElseThrow().source()).resultId())
                .isEqualTo(10);
        int anchorsAfterInterruptedAttempt = harness.events.values.size();

        RecordBootstrapCoordinator restartedProcess = new RecordBootstrapCoordinator(
                bootstrapStore,
                guildId -> history,
                harness.service(),
                RecordDefinitionCatalog.recordsV1(),
                Clock.fixed(STARTED_AT, ZoneOffset.UTC));
        assertThat(restartedProcess.run(GUILD_ID))
                .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);

        assertThat(harness.events.values).hasSize(anchorsAfterInterruptedAttempt);
        RecordEventDraft originalAnchor = harness.events.find(anchorId()).orElseThrow().draft();
        assertThat(((RecordSourceReference.GameResult) originalAnchor.newSource()).resultId()).isEqualTo(99);
    }

    @Test
    void changedImmutableAnchorIdentityStillFailsLoudly() {
        Harness harness = new Harness();
        RecordBootstrapProjection.Candidate initialized = candidate(1, 99);
        harness.states.initialize(KEY, initialized.write());
        RecordEventDraft conflicting = anchor(initialized, STARTED_AT.plusSeconds(1));
        harness.events.put(conflicting);

        assertThatThrownBy(() -> harness.service().initializeSilently(initialized, bootstrapKey(), STARTED_AT))
                .isInstanceOf(RecordEventIdempotencyConflictException.class);
        assertThat(harness.events.values).hasSize(1);
    }

    private static RecordBootstrapProjection.Candidate candidate(int attempts, long resultId) {
        return new RecordBootstrapProjection.Candidate(
                KEY,
                new RecordStateWrite(
                        Optional.of(PLAYER_ID),
                        new AttemptsDurationRecordValue(attempts, Duration.ofSeconds(50)),
                        new RecordSourceReference.GameResult(
                                resultId,
                                0,
                                PLAYER_ID,
                                GameType.GRIDWORDS,
                                LocalDate.of(2026, 8, 4)),
                        Optional.of(STARTED_AT.minusSeconds(60)),
                        false));
    }

    private static String bootstrapKey() {
        return GUILD_ID + ":" + RecordDefinitionVersion.RECORDS_V1.value();
    }

    private static String stableKey() {
        return bootstrapKey() + ":" + KEY.definitionKey().value() + ":" + KEY.scopeKey();
    }

    private static UUID anchorId() {
        return UUID.nameUUIDFromBytes(stableKey().getBytes(StandardCharsets.UTF_8));
    }

    private static RecordEventDraft anchor(
            RecordBootstrapProjection.Candidate candidate,
            Instant detectedAt) {
        RecordStateWrite write = candidate.write();
        return new RecordEventDraft(
                anchorId(),
                "record-initialized:" + stableKey(),
                candidate.key(),
                RecordEventType.RECORD_INITIALIZED,
                Optional.empty(),
                write.value(),
                Optional.empty(),
                write.holderPlayerId(),
                Optional.empty(),
                write.source(),
                stableKey(),
                RecordProcessingOrigin.BOOTSTRAP,
                detectedAt);
    }

    private static final class Harness implements RecordTransactionRunner {
        private final MemoryStates states = new MemoryStates();
        private final MemoryEvents events = new MemoryEvents();

        RecordStateService service() {
            return new RecordStateService(states, events, this, RecordDefinitionCatalog.recordsV1());
        }

        @Override
        public <T> T inTransaction(java.util.function.Supplier<T> work) {
            Map<RecordStateKey, RecordStateSnapshot> priorStates = new LinkedHashMap<>(states.values);
            Map<String, RecordEventSnapshot> priorEvents = new LinkedHashMap<>(events.values);
            try {
                return work.get();
            } catch (RuntimeException failure) {
                states.values = priorStates;
                events.values = priorEvents;
                throw failure;
            }
        }
    }

    private static final class MemoryStates implements RecordStateStore {
        private Map<RecordStateKey, RecordStateSnapshot> values = new LinkedHashMap<>();

        @Override
        public Optional<RecordStateSnapshot> find(RecordStateKey key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public List<RecordStateSnapshot> findAll(long guildId, RecordDefinitionVersion version) {
            return values.values().stream()
                    .filter(state -> state.key().guildId() == guildId)
                    .filter(state -> state.key().definitionVersion().equals(version))
                    .toList();
        }

        @Override
        public RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write) {
            RecordStateSnapshot existing = values.get(key);
            if (existing != null) {
                return new RecordStateInitialization.Existing(existing);
            }
            RecordStateSnapshot created = snapshot(key, write, RecordLockVersion.initial());
            values.put(key, created);
            return new RecordStateInitialization.Created(created);
        }

        @Override
        public RecordStateUpdateResult update(RecordStateUpdate update) {
            RecordStateSnapshot current = values.get(update.key());
            if (current == null || !current.lockVersion().equals(update.expectedLockVersion())) {
                return new RecordStateUpdateResult(
                        RecordStateUpdateResult.Status.VERSION_CONFLICT,
                        Optional.empty());
            }
            RecordStateSnapshot changed = snapshot(
                    update.key(),
                    update.write(),
                    current.lockVersion().next());
            values.put(update.key(), changed);
            return new RecordStateUpdateResult(
                    RecordStateUpdateResult.Status.UPDATED,
                    Optional.of(changed));
        }

        @Override
        public boolean remove(RecordStateKey key, RecordLockVersion expectedLockVersion) {
            RecordStateSnapshot current = values.get(key);
            if (current == null || !current.lockVersion().equals(expectedLockVersion)) {
                return false;
            }
            values.remove(key);
            return true;
        }

        private static RecordStateSnapshot snapshot(
                RecordStateKey key,
                RecordStateWrite write,
                RecordLockVersion lockVersion) {
            return new RecordStateSnapshot(
                    key,
                    write.holderPlayerId(),
                    write.value(),
                    write.source(),
                    write.sourceGameFirstAcceptedAt(),
                    write.running(),
                    lockVersion,
                    STARTED_AT,
                    STARTED_AT);
        }
    }

    private static final class MemoryEvents implements RecordEventStore {
        private Map<String, RecordEventSnapshot> values = new LinkedHashMap<>();

        @Override
        public RecordEventAppendResult append(RecordEventDraft draft) {
            RecordEventSnapshot existing = values.get(draft.idempotencyKey());
            if (existing != null) {
                if (!existing.draft().equals(draft)) {
                    throw new RecordEventIdempotencyConflictException(draft.idempotencyKey());
                }
                return new RecordEventAppendResult(false, existing);
            }
            RecordEventSnapshot created = snapshot(draft);
            values.put(draft.idempotencyKey(), created);
            return new RecordEventAppendResult(true, created);
        }

        void put(RecordEventDraft draft) {
            values.put(draft.idempotencyKey(), snapshot(draft));
        }

        @Override
        public Optional<RecordEventSnapshot> find(UUID eventId) {
            return values.values().stream()
                    .filter(snapshot -> snapshot.draft().eventId().equals(eventId))
                    .findFirst();
        }

        @Override
        public List<RecordEventSnapshot> findByTriggerKey(long guildId, String triggerKey) {
            return values.values().stream()
                    .filter(snapshot -> snapshot.draft().stateKey().guildId() == guildId)
                    .filter(snapshot -> snapshot.draft().triggerKey().equals(triggerKey))
                    .toList();
        }

        @Override
        public boolean invalidate(UUID eventId, Instant invalidatedAt) {
            return false;
        }

        @Override
        public boolean supersede(UUID eventId, UUID successor, Instant invalidatedAt) {
            return false;
        }

        private static RecordEventSnapshot snapshot(RecordEventDraft draft) {
            return new RecordEventSnapshot(
                    draft,
                    RecordEventValidity.VALID,
                    Optional.empty(),
                    Optional.empty(),
                    STARTED_AT,
                    STARTED_AT);
        }
    }

    private static final class BootstrapStore implements RecordBootstrapStore {
        private final UUID token = UUID.randomUUID();
        private boolean failNextCompletion;

        @Override
        public RecordBootstrapSnapshot register(RecordBootstrapKey key) {
            return snapshot(key, RecordWorkState.OPEN);
        }

        @Override
        public Optional<RecordBootstrapSnapshot> find(RecordBootstrapKey key) {
            return Optional.of(snapshot(key, RecordWorkState.CLAIMED));
        }

        @Override
        public Optional<RecordLeaseClaim> claim(
                RecordBootstrapKey key,
                RecordLeaseClaimRequest request) {
            return Optional.of(new RecordLeaseClaim(token, request.leaseUntil()));
        }

        @Override
        public boolean renewLease(
                RecordBootstrapKey key,
                UUID claimToken,
                RecordLeaseClaimRequest request) {
            return token.equals(claimToken);
        }

        @Override
        public boolean markSucceeded(
                RecordBootstrapKey key,
                UUID claimToken,
                Instant completedAt) {
            if (!token.equals(claimToken)) {
                return false;
            }
            if (failNextCompletion) {
                failNextCompletion = false;
                return false;
            }
            return true;
        }

        @Override
        public boolean markRetryableFailure(
                RecordBootstrapKey key,
                UUID claimToken,
                RecordWorkFailure failure,
                Instant retryAt) {
            return token.equals(claimToken);
        }

        @Override
        public boolean markPermanentFailure(
                RecordBootstrapKey key,
                UUID claimToken,
                RecordWorkFailure failure,
                Instant completedAt) {
            return token.equals(claimToken);
        }

        private RecordBootstrapSnapshot snapshot(
                RecordBootstrapKey key,
                RecordWorkState state) {
            Optional<UUID> claim = state == RecordWorkState.CLAIMED
                    ? Optional.of(token)
                    : Optional.empty();
            Optional<Instant> leaseUntil = state == RecordWorkState.CLAIMED
                    ? Optional.of(STARTED_AT.plusSeconds(120))
                    : Optional.empty();
            return new RecordBootstrapSnapshot(
                    key,
                    state,
                    claim,
                    leaseUntil,
                    Optional.of(STARTED_AT),
                    Optional.empty(),
                    1,
                    Optional.empty(),
                    Optional.empty(),
                    STARTED_AT,
                    STARTED_AT);
        }
    }
}
