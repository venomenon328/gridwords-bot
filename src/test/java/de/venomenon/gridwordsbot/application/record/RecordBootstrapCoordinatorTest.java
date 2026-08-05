package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import de.venomenon.gridwordsbot.domain.record.*;
import de.venomenon.gridwordsbot.port.out.*;
import org.junit.jupiter.api.Test;

class RecordBootstrapCoordinatorTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private final RecordHistorySnapshot history = new RecordHistorySnapshot(List.of(), List.of(
            new GameParticipationPeriod(7L, GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), null)));

    @Test void keepsYesterdayOpenBeforeTheBusinessCutoff() {
        var window = RecordBootstrapCoordinator.analysisWindow(history,
                Clock.fixed(Instant.parse("2026-08-05T03:59:59Z"), BERLIN));
        assertThat(window.asOfDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(window.asOfDateClosed()).isFalse();
        assertThat(window.dayClosed(LocalDate.of(2026, 8, 4))).isFalse();
    }

    @Test void closesYesterdayAtTheBusinessCutoffWithoutClosingToday() {
        var window = RecordBootstrapCoordinator.analysisWindow(history,
                Clock.fixed(Instant.parse("2026-08-05T04:00:00Z"), BERLIN));
        assertThat(window.asOfDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(window.dayClosed(LocalDate.of(2026, 8, 4))).isTrue();
        assertThat(window.dayClosed(LocalDate.of(2026, 8, 5))).isFalse();
    }

    @Test void staleBootstrapCandidateCannotReplaceABetterConcurrentState() {
        RecordStateKey key = new RecordStateKey(1, new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(7));
        RecordStateWrite better = new RecordStateWrite(Optional.of(7L), new AttemptsDurationRecordValue(1, Duration.ofSeconds(40)),
                new RecordSourceReference.GameResult(2, 1, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 4)), false);
        MemoryStates states = new MemoryStates(key, better);
        RecordStateService service = new RecordStateService(states, new NoEvents(), new RecordTransactionRunner() {
            public <T> T inTransaction(java.util.function.Supplier<T> work) { return work.get(); }
        }, RecordDefinitionCatalog.recordsV1());
        RecordStateWrite stale = new RecordStateWrite(Optional.of(7L), new AttemptsDurationRecordValue(3, Duration.ofSeconds(50)),
                new RecordSourceReference.GameResult(1, 0, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 3)), false);
        assertThat(service.rebuild(new RecordBootstrapProjection.Candidate(key, stale), "bootstrap", Instant.EPOCH))
                .isEqualTo(RecordStateService.RebuildResult.UNCHANGED);
        assertThat(states.updated).isFalse();
    }

    @Test void completeHistoryRecomputeReplacesInvalidSourceAndRemovesAbsentStateThroughCoordinator() {
        Instant now = Instant.parse("2026-08-05T08:00:00Z");
        RecordStateKey fallbackKey = new RecordStateKey(1,
                new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(7));
        RecordStateKey absentKey = new RecordStateKey(1,
                new RecordDefinitionKey("result.quadwords.fastest-solution.personal"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(7));
        MapStates states = new MapStates();
        states.put(fallbackKey, write(1, 99, GameType.GRIDWORDS));
        states.put(absentKey, write(2, 88, GameType.QUADWORDS));
        RecordStateService service = new RecordStateService(states, new MemoryEvents(),
                new RecordTransactionRunner() { public <T> T inTransaction(java.util.function.Supplier<T> work) { return work.get(); } },
                RecordDefinitionCatalog.recordsV1());
        RecordHistorySnapshot canonical = new RecordHistorySnapshot(List.of(new RecordHistorySnapshot.Result(
                10, 0, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 4),
                new de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved(3, 6), Duration.ofSeconds(50), now)), List.of());
        RecordBootstrapCoordinator coordinator = new RecordBootstrapCoordinator(new ClaimedBootstrapStore(now), guild -> canonical,
                service, RecordDefinitionCatalog.recordsV1(), Clock.fixed(now, ZoneId.of("UTC")));

        assertThat(coordinator.run(1)).isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        assertThat(((RecordSourceReference.GameResult) states.find(fallbackKey).orElseThrow().source()).resultId()).isEqualTo(10);
        assertThat(states.find(absentKey)).isEmpty();
    }

    @Test void partialBootstrapInterruptionResumesIdempotentlyWithoutDuplicateAnchors() {
        Instant now = Instant.parse("2026-08-05T08:00:00Z");
        MapStates states = new MapStates();
        MemoryEvents events = new MemoryEvents();
        InterruptingStateService service = new InterruptingStateService(states, events);
        RecordHistorySnapshot canonical = new RecordHistorySnapshot(List.of(new RecordHistorySnapshot.Result(
                10, 0, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 4),
                new de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved(3, 6), Duration.ofSeconds(50), now)), List.of());
        RecordBootstrapCoordinator coordinator = new RecordBootstrapCoordinator(new ClaimedBootstrapStore(now), guild -> canonical,
                service, RecordDefinitionCatalog.recordsV1(), Clock.fixed(now, ZoneId.of("UTC")));

        assertThat(coordinator.run(1)).isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.RETRY_SCHEDULED);
        assertThat(coordinator.run(1)).isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        assertThat(events.drafts).hasSize(states.states.size());
    }

    @Test void bootstrapKeepsActiveDefinitionVersionSeparateFromHistoricalStates() {
        Instant now = Instant.parse("2026-08-05T08:00:00Z");
        RecordDefinitionVersion currentVersion = new RecordDefinitionVersion("records-v2");
        RecordDefinitionKey definitionKey = new RecordDefinitionKey("result.gridwords.fewest-attempts.personal");
        RecordDefinition<AttemptsDurationRecordValue> currentDefinition = new RecordDefinition<>(
                definitionKey, currentVersion, ResultRecordMetric.FEWEST_ATTEMPTS, Optional.of(GameType.GRIDWORDS),
                RecordScopeType.PERSONAL, RecordComparators.fewestAttempts(),
                new RecordSourceEligibility.SolvedGameResult(GameType.GRIDWORDS),
                new RecordAnnouncementThreshold.Result(5, 1));
        RecordDefinitionCatalog currentCatalog = RecordDefinitionCatalog.of(currentVersion, List.of(currentDefinition));
        MapStates states = new MapStates();
        RecordStateKey formerKey = new RecordStateKey(1, definitionKey, RecordDefinitionVersion.RECORDS_V1,
                new RecordScope.Personal(7));
        states.put(formerKey, write(1, 9, GameType.GRIDWORDS));
        RecordStateService service = new RecordStateService(states, new MemoryEvents(),
                new RecordTransactionRunner() { public <T> T inTransaction(java.util.function.Supplier<T> work) { return work.get(); } },
                currentCatalog);
        RecordHistorySnapshot canonical = new RecordHistorySnapshot(List.of(new RecordHistorySnapshot.Result(
                10, 0, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 4),
                new de.venomenon.gridwordsbot.domain.model.ShareOutcome.Solved(3, 6), Duration.ofSeconds(50), now)), List.of());
        ClaimedBootstrapStore bootstrapStore = new ClaimedBootstrapStore(now);
        RecordBootstrapCoordinator coordinator = new RecordBootstrapCoordinator(bootstrapStore, guild -> canonical,
                service, currentCatalog, Clock.fixed(now, ZoneId.of("UTC")));

        assertThat(coordinator.run(1)).isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        assertThat(bootstrapStore.registered).isEqualTo(new RecordBootstrapKey(1, currentVersion));
        assertThat(states.find(formerKey)).isPresent();
        assertThat(states.find(new RecordStateKey(1, definitionKey, currentVersion, new RecordScope.Personal(7))))
                .isPresent();
    }

    private static RecordStateWrite write(int attempts, long resultId, GameType game) {
        int maxAttempts = game == GameType.GRIDWORDS ? 6 : 9;
        return new RecordStateWrite(Optional.of(7L), new AttemptsDurationRecordValue(attempts, Duration.ofSeconds(50)),
                new RecordSourceReference.GameResult(resultId, 0, 7, game, LocalDate.of(2026, 8, 4)), false);
    }

    private static final class ClaimedBootstrapStore implements RecordBootstrapStore {
        private final Instant now; private final UUID token = UUID.randomUUID();
        private RecordBootstrapKey registered;
        ClaimedBootstrapStore(Instant now) { this.now = now; }
        public RecordBootstrapSnapshot register(RecordBootstrapKey key) { registered = key; return snapshot(key, RecordWorkState.OPEN); }
        public Optional<RecordBootstrapSnapshot> find(RecordBootstrapKey key) { return Optional.of(snapshot(key, RecordWorkState.CLAIMED)); }
        public Optional<RecordLeaseClaim> claim(RecordBootstrapKey key, RecordLeaseClaimRequest request) { return Optional.of(new RecordLeaseClaim(token, request.leaseUntil())); }
        public boolean renewLease(RecordBootstrapKey key, UUID token, RecordLeaseClaimRequest request) { return this.token.equals(token); }
        public boolean markSucceeded(RecordBootstrapKey key, UUID token, Instant completedAt) { return this.token.equals(token); }
        public boolean markRetryableFailure(RecordBootstrapKey key, UUID token, RecordWorkFailure failure, Instant retryAt) { return this.token.equals(token); }
        public boolean markPermanentFailure(RecordBootstrapKey key, UUID token, RecordWorkFailure failure, Instant completedAt) { return false; }
        private RecordBootstrapSnapshot snapshot(RecordBootstrapKey key, RecordWorkState state) {
            Optional<UUID> claim = state == RecordWorkState.CLAIMED ? Optional.of(token) : Optional.empty();
            Optional<Instant> until = state == RecordWorkState.CLAIMED ? Optional.of(now.plusSeconds(60)) : Optional.empty();
            return new RecordBootstrapSnapshot(key, state, claim, until, Optional.of(now), Optional.empty(), 1,
                    Optional.empty(), Optional.empty(), now, now);
        }
    }

    private static final class MapStates implements RecordStateStore {
        private final Map<RecordStateKey, RecordStateSnapshot> states = new HashMap<>();
        void put(RecordStateKey key, RecordStateWrite write) { states.put(key, snapshot(key, write, RecordLockVersion.initial())); }
        public Optional<RecordStateSnapshot> find(RecordStateKey key) { return Optional.ofNullable(states.get(key)); }
        public List<RecordStateSnapshot> findAll(long guild, RecordDefinitionVersion version) { return states.values().stream().filter(state -> state.key().guildId() == guild && state.key().definitionVersion().equals(version)).toList(); }
        public RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write) { RecordStateSnapshot state = states.get(key); if (state != null) return new RecordStateInitialization.Existing(state); RecordStateSnapshot created = snapshot(key, write, RecordLockVersion.initial()); states.put(key, created); return new RecordStateInitialization.Created(created); }
        public RecordStateUpdateResult update(RecordStateUpdate update) { RecordStateSnapshot state = states.get(update.key()); if (state == null || !state.lockVersion().equals(update.expectedLockVersion())) return new RecordStateUpdateResult(RecordStateUpdateResult.Status.VERSION_CONFLICT, Optional.empty()); RecordStateSnapshot changed = snapshot(update.key(), update.write(), state.lockVersion().next()); states.put(update.key(), changed); return new RecordStateUpdateResult(RecordStateUpdateResult.Status.UPDATED, Optional.of(changed)); }
        public boolean remove(RecordStateKey key, RecordLockVersion version) { RecordStateSnapshot state = states.get(key); if (state == null || !state.lockVersion().equals(version)) return false; states.remove(key); return true; }
        private static RecordStateSnapshot snapshot(RecordStateKey key, RecordStateWrite write, RecordLockVersion version) { return new RecordStateSnapshot(key, write.holderPlayerId(), write.value(), write.source(), write.sourceGameFirstAcceptedAt(), write.running(), version, Instant.EPOCH, Instant.EPOCH); }
    }

    private static final class MemoryEvents implements RecordEventStore {
        private final Map<String, RecordEventDraft> drafts = new LinkedHashMap<>();
        public RecordEventAppendResult append(RecordEventDraft draft) {
            RecordEventDraft existing = drafts.putIfAbsent(draft.idempotencyKey(), draft);
            if (existing != null && !existing.equals(draft)) throw new de.venomenon.gridwordsbot.port.out.RecordEventIdempotencyConflictException(draft.idempotencyKey());
            return new RecordEventAppendResult(existing == null, new RecordEventSnapshot(existing == null ? draft : existing,
                    RecordEventValidity.VALID, Optional.empty(), Optional.empty(), Instant.EPOCH, Instant.EPOCH));
        }
        public Optional<RecordEventSnapshot> find(UUID id) { return Optional.empty(); }
        public List<RecordEventSnapshot> findByTriggerKey(long guild, String trigger) { return List.of(); }
        public boolean invalidate(UUID id, Instant at) { return false; }
        public boolean supersede(UUID id, UUID successor, Instant at) { return false; }
    }

    private static final class InterruptingStateService extends RecordStateService {
        private int calls;
        private boolean interrupted;
        InterruptingStateService(MapStates states, MemoryEvents events) {
            super(states, events, new RecordTransactionRunner() {
                public <T> T inTransaction(java.util.function.Supplier<T> work) { return work.get(); }
            }, RecordDefinitionCatalog.recordsV1());
        }
        @Override public RebuildResult reconcileCanonicalTarget(
                RecordBootstrapProjection.Candidate candidate, String bootstrapKey, Instant detectedAt) {
            calls++;
            if (!interrupted && calls == 2) {
                interrupted = true;
                return RebuildResult.RETRY_EXHAUSTED;
            }
            return super.reconcileCanonicalTarget(candidate, bootstrapKey, detectedAt);
        }
    }

    private static final class MemoryStates implements RecordStateStore {
        private final RecordStateKey key; private RecordStateSnapshot current; boolean updated;
        MemoryStates(RecordStateKey key, RecordStateWrite write) {
            this.key = key; current = new RecordStateSnapshot(key, write.holderPlayerId(), write.value(), write.source(), write.running(),
                    RecordLockVersion.initial(), Instant.EPOCH, Instant.EPOCH);
        }
        public Optional<RecordStateSnapshot> find(RecordStateKey requested) { return key.equals(requested) ? Optional.of(current) : Optional.empty(); }
        public List<RecordStateSnapshot> findAll(long guild, RecordDefinitionVersion version) { return List.of(current); }
        public RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write) { return new RecordStateInitialization.Existing(current); }
        public RecordStateUpdateResult update(RecordStateUpdate update) { updated = true; return new RecordStateUpdateResult(RecordStateUpdateResult.Status.UPDATED, Optional.of(current)); }
        public boolean remove(RecordStateKey key, RecordLockVersion version) { return false; }
    }
    private static final class NoEvents implements RecordEventStore {
        public RecordEventAppendResult append(RecordEventDraft draft) { throw new AssertionError("must not append"); }
        public Optional<RecordEventSnapshot> find(java.util.UUID id) { return Optional.empty(); }
        public List<RecordEventSnapshot> findByTriggerKey(long guild, String trigger) { return List.of(); }
        public boolean invalidate(java.util.UUID id, Instant at) { return false; }
        public boolean supersede(java.util.UUID id, java.util.UUID successor, Instant at) { return false; }
    }
}
