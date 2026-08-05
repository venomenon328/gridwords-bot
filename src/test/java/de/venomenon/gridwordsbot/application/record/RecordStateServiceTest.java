package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapProjection;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventAppendResult;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventValidity;
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
import de.venomenon.gridwordsbot.domain.record.StreakCrossingKey;
import de.venomenon.gridwordsbot.domain.record.StreakRecordMetric;
import de.venomenon.gridwordsbot.domain.record.StreakRecordValue;
import de.venomenon.gridwordsbot.domain.record.StreakRunIdentity;
import de.venomenon.gridwordsbot.port.out.RecordEventIdempotencyConflictException;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordStateServiceTest {
    private static final Instant DETECTED_AT = Instant.parse("2026-08-05T08:00:00Z");
    private static final RecordStateKey KEY = new RecordStateKey(1,
            new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
            RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(7));

    @Test
    void initializationAndAuditAnchorRollbackTogetherWhenAppendingFails() {
        Harness harness = new Harness();
        harness.events.failAfterAppend = true;

        assertThatThrownBy(() -> service(harness).initializeSilently(candidate(3, 10), "1:records-v1", DETECTED_AT))
                .isInstanceOf(IllegalStateException.class);

        assertThat(harness.states.values).isEmpty();
        assertThat(harness.events.values).isEmpty();
    }

    @Test
    void identicalInitializationReplayIsANoOpWithExactlyOneSilentAnchor() {
        Harness harness = new Harness();
        RecordStateService service = service(harness);

        assertThat(service.initializeSilently(candidate(3, 10), "1:records-v1", DETECTED_AT)).isTrue();
        assertThat(service.initializeSilently(candidate(3, 10), "1:records-v1", DETECTED_AT)).isFalse();

        assertThat(harness.states.values).hasSize(1);
        assertThat(harness.events.values).hasSize(1);
        RecordEventDraft anchor = harness.events.values.values().iterator().next().draft();
        assertThat(anchor.processingOrigin()).isEqualTo(RecordProcessingOrigin.BOOTSTRAP);
        assertThat(anchor.detectedAt()).isEqualTo(DETECTED_AT);
    }

    @Test
    void existingStateWithoutAuditGetsItsOneDeterministicAnchorRestored() {
        Harness harness = new Harness();
        harness.states.values.put(KEY, snapshot(candidate(3, 10), RecordLockVersion.initial()));

        assertThat(service(harness).initializeSilently(candidate(3, 10), "1:records-v1", DETECTED_AT)).isFalse();

        assertThat(harness.states.values).hasSize(1);
        assertThat(harness.events.values).hasSize(1);
    }

    @Test
    void existingStateWithADifferentInitializationAuditFailsLoudly() {
        Harness harness = new Harness();
        harness.states.values.put(KEY, snapshot(candidate(3, 10), RecordLockVersion.initial()));
        String stable = "1:records-v1:" + KEY.definitionKey().value() + ":" + KEY.scopeKey();
        harness.events.put(anchor(candidate(4, 9), stable, DETECTED_AT));

        assertThatThrownBy(() -> service(harness).initializeSilently(candidate(3, 10), "1:records-v1", DETECTED_AT))
                .isInstanceOf(RecordEventIdempotencyConflictException.class);

        assertThat(harness.states.values).hasSize(1);
        assertThat(harness.events.values).hasSize(1);
    }

    @Test
    void conflictingDeterministicAnchorIsNotSilentlyHidden() {
        Harness harness = new Harness();
        String stable = "1:records-v1:" + KEY.definitionKey().value() + ":" + KEY.scopeKey();
        RecordEventDraft conflicting = anchor(candidate(4, 9), stable, DETECTED_AT.plusSeconds(1));
        harness.events.put(conflicting);

        assertThatThrownBy(() -> service(harness).initializeSilently(candidate(3, 10), "1:records-v1", DETECTED_AT))
                .isInstanceOf(RecordEventIdempotencyConflictException.class);

        assertThat(harness.states.values).isEmpty();
        assertThat(harness.events.values).hasSize(1);
    }

    @Test
    void uniqueRaceIsRereadAndDoesNotReplaceABetterExistingState() {
        Harness harness = new Harness();
        harness.states.nextInitializeExisting = snapshot(candidate(1, 20), RecordLockVersion.initial());

        assertThat(service(harness).rebuild(candidate(3, 10), "1:records-v1", DETECTED_AT))
                .isEqualTo(RecordStateService.RebuildResult.UNCHANGED);
        assertThat(harness.states.find(KEY).orElseThrow().value())
                .isEqualTo(candidate(1, 20).write().value());
    }

    @Test
    void casRetryRereadsTheCurrentStateAndDoesNotOverwriteABetterSource() {
        Harness harness = new Harness();
        harness.states.values.put(KEY, snapshot(candidate(4, 5), RecordLockVersion.initial()));
        harness.states.conflictReplacement = snapshot(candidate(1, 20), new RecordLockVersion(1));

        assertThat(service(harness).rebuild(candidate(2, 10), "1:records-v1", DETECTED_AT))
                .isEqualTo(RecordStateService.RebuildResult.UNCHANGED);
        assertThat(harness.states.find(KEY).orElseThrow().value())
                .isEqualTo(candidate(1, 20).write().value());
    }

    @Test
    void rebuildRepairsCanonicalEqualSourceAndRemovalLeavesAuditFactsIntact() {
        Harness harness = new Harness();
        harness.states.values.put(KEY, snapshot(candidate(3, 20), RecordLockVersion.initial()));
        RecordStateService service = service(harness);

        assertThat(service.rebuild(candidate(3, 10), "1:records-v1", DETECTED_AT))
                .isEqualTo(RecordStateService.RebuildResult.REPLACED);
        RecordEventDraft audit = anchor(candidate(3, 10), "audit", DETECTED_AT);
        harness.events.put(audit);

        assertThat(service.removeIfNoSource(KEY)).isEqualTo(RecordStateService.RebuildResult.REMOVED);
        assertThat(harness.states.find(KEY)).isEmpty();
        assertThat(harness.events.values).containsKey(audit.idempotencyKey());
    }

    @Test
    void canonicalRecomputeReplacesAnInvalidFormerBestWithTheNextValidSource() {
        Harness harness = new Harness();
        harness.states.values.put(KEY, snapshot(candidate(1, 20), RecordLockVersion.initial()));

        assertThat(service(harness).reconcileCanonicalTarget(candidate(3, 10), "1:records-v1", DETECTED_AT))
                .isEqualTo(RecordStateService.RebuildResult.REPLACED);
        assertThat(harness.states.find(KEY).orElseThrow().source()).isEqualTo(candidate(3, 10).write().source());
    }

    @Test
    void canonicalTargetAbsenceRemovesOnlyTheMaterializedStateAndKeepsAuditHistory() {
        Harness harness = new Harness();
        harness.states.values.put(KEY, snapshot(candidate(3, 10), RecordLockVersion.initial()));
        RecordEventDraft audit = anchor(candidate(3, 10), "removed-state", DETECTED_AT);
        harness.events.put(audit);

        assertThat(service(harness).removeAbsentCanonicalTarget(KEY))
                .isEqualTo(RecordStateService.RebuildResult.REMOVED);
        assertThat(harness.states.find(KEY)).isEmpty();
        assertThat(harness.events.values).containsKey(audit.idempotencyKey());
    }

    @Test
    void runningBootstrapStateReconstructsOnlyTheConsumedCrossingIdentity() {
        Harness harness = new Harness();
        LocalDate start = LocalDate.of(2026, 7, 29);
        RecordStateKey streakKey = new RecordStateKey(1,
                new RecordDefinitionKey("streak.gridwords-solved.personal"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(7));
        RecordBootstrapProjection.Candidate running = new RecordBootstrapProjection.Candidate(streakKey,
                new RecordStateWrite(Optional.of(7L), new StreakRecordValue(8, start, LocalDate.of(2026, 8, 5)),
                        new RecordSourceReference.StreakRun(StreakRecordMetric.GRIDWORDS_SOLVED,
                                new RecordSourceReference.StreakRunOwner.Player(7), start),
                        Optional.empty(), true));
        harness.states.values.put(streakKey, snapshot(running, RecordLockVersion.initial()));

        assertThat(service(harness).consumedCrossings(1, RecordDefinitionVersion.RECORDS_V1)).containsExactly(
                new StreakCrossingKey(RecordDefinitionVersion.RECORDS_V1, streakKey.definitionKey(),
                        new StreakRunIdentity(StreakRecordMetric.GRIDWORDS_SOLVED, new RecordScope.Personal(7), start)));
    }

    @Test
    void equalLengthStreakRebuildUsesTheSameEndDateTieBreakAsTheProjection() {
        Harness harness = new Harness();
        RecordStateKey streakKey = new RecordStateKey(1,
                new RecordDefinitionKey("streak.gridwords-solved.personal"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(7));
        RecordBootstrapProjection.Candidate persisted = streakCandidate(streakKey,
                LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 5));
        RecordBootstrapProjection.Candidate canonical = streakCandidate(streakKey,
                LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 4));
        harness.states.values.put(streakKey, snapshot(persisted, RecordLockVersion.initial()));

        assertThat(service(harness).rebuild(canonical, "1:records-v1", DETECTED_AT))
                .isEqualTo(RecordStateService.RebuildResult.REPLACED);
        assertThat(harness.states.find(streakKey).orElseThrow().source()).isEqualTo(canonical.write().source());
    }

    private static RecordStateService service(Harness harness) {
        return new RecordStateService(harness.states, harness.events, harness, RecordDefinitionCatalog.recordsV1());
    }

    private static RecordBootstrapProjection.Candidate candidate(int attempts, long resultId) {
        return new RecordBootstrapProjection.Candidate(KEY, new RecordStateWrite(Optional.of(7L),
                new AttemptsDurationRecordValue(attempts, Duration.ofSeconds(50)),
                new RecordSourceReference.GameResult(resultId, 0, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 4)),
                Optional.of(Instant.parse("2026-08-04T09:00:00Z")), false));
    }

    private static RecordStateSnapshot snapshot(RecordBootstrapProjection.Candidate candidate, RecordLockVersion version) {
        RecordStateWrite write = candidate.write();
        return new RecordStateSnapshot(candidate.key(), write.holderPlayerId(), write.value(), write.source(),
                write.sourceGameFirstAcceptedAt(), write.running(), version, DETECTED_AT, DETECTED_AT);
    }

    private static RecordBootstrapProjection.Candidate streakCandidate(
            RecordStateKey key, LocalDate start, LocalDate end) {
        return new RecordBootstrapProjection.Candidate(key,
                new RecordStateWrite(Optional.of(7L), new StreakRecordValue(7, start, end),
                        new RecordSourceReference.StreakRun(StreakRecordMetric.GRIDWORDS_SOLVED,
                                new RecordSourceReference.StreakRunOwner.Player(7), start),
                        Optional.empty(), false));
    }

    private static RecordEventDraft anchor(RecordBootstrapProjection.Candidate candidate, String stable, Instant detectedAt) {
        RecordStateWrite write = candidate.write();
        return new RecordEventDraft(UUID.nameUUIDFromBytes(stable.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "record-initialized:" + stable, KEY, de.venomenon.gridwordsbot.domain.record.RecordEventType.RECORD_INITIALIZED,
                Optional.empty(), write.value(), Optional.empty(), write.holderPlayerId(), Optional.empty(), write.source(), stable,
                RecordProcessingOrigin.BOOTSTRAP, detectedAt);
    }

    private static final class Harness implements RecordTransactionRunner {
        final MemoryStates states = new MemoryStates();
        final MemoryEvents events = new MemoryEvents();

        @Override public <T> T inTransaction(java.util.function.Supplier<T> work) {
            Map<RecordStateKey, RecordStateSnapshot> priorStates = new LinkedHashMap<>(states.values);
            Map<String, RecordEventSnapshot> priorEvents = new LinkedHashMap<>(events.values);
            try { return work.get(); }
            catch (RuntimeException ex) { states.values = priorStates; events.values = priorEvents; throw ex; }
        }
    }

    private static final class MemoryStates implements RecordStateStore {
        Map<RecordStateKey, RecordStateSnapshot> values = new LinkedHashMap<>();
        RecordStateSnapshot nextInitializeExisting;
        RecordStateSnapshot conflictReplacement;

        @Override public Optional<RecordStateSnapshot> find(RecordStateKey key) { return Optional.ofNullable(values.get(key)); }
        @Override public List<RecordStateSnapshot> findAll(long guildId, RecordDefinitionVersion version) { return List.copyOf(values.values()); }
        @Override public RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write) {
            if (nextInitializeExisting != null) {
                RecordStateSnapshot existing = nextInitializeExisting; nextInitializeExisting = null; values.put(key, existing);
                return new RecordStateInitialization.Existing(existing);
            }
            RecordStateSnapshot existing = values.get(key);
            if (existing != null) return new RecordStateInitialization.Existing(existing);
            RecordStateSnapshot created = snapshot(new RecordBootstrapProjection.Candidate(key, write), RecordLockVersion.initial());
            values.put(key, created); return new RecordStateInitialization.Created(created);
        }
        @Override public RecordStateUpdateResult update(RecordStateUpdate update) {
            if (conflictReplacement != null) {
                values.put(update.key(), conflictReplacement); conflictReplacement = null;
                return new RecordStateUpdateResult(RecordStateUpdateResult.Status.VERSION_CONFLICT, Optional.empty());
            }
            RecordStateSnapshot current = values.get(update.key());
            if (current == null || !current.lockVersion().equals(update.expectedLockVersion())) {
                return new RecordStateUpdateResult(RecordStateUpdateResult.Status.VERSION_CONFLICT, Optional.empty());
            }
            RecordStateSnapshot changed = snapshot(new RecordBootstrapProjection.Candidate(update.key(), update.write()), current.lockVersion().next());
            values.put(update.key(), changed);
            return new RecordStateUpdateResult(RecordStateUpdateResult.Status.UPDATED, Optional.of(changed));
        }
        @Override public boolean remove(RecordStateKey key, RecordLockVersion expected) {
            RecordStateSnapshot current = values.get(key);
            if (current == null || !current.lockVersion().equals(expected)) return false;
            values.remove(key); return true;
        }
    }

    private static final class MemoryEvents implements RecordEventStore {
        Map<String, RecordEventSnapshot> values = new LinkedHashMap<>();
        boolean failAfterAppend;
        @Override public RecordEventAppendResult append(RecordEventDraft draft) {
            RecordEventSnapshot existing = values.get(draft.idempotencyKey());
            if (existing != null) {
                if (!existing.draft().equals(draft)) throw new RecordEventIdempotencyConflictException(draft.idempotencyKey());
                return new RecordEventAppendResult(false, existing);
            }
            RecordEventSnapshot created = new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(), DETECTED_AT, DETECTED_AT);
            values.put(draft.idempotencyKey(), created);
            if (failAfterAppend) throw new IllegalStateException("simulated event failure");
            return new RecordEventAppendResult(true, created);
        }
        void put(RecordEventDraft draft) { values.put(draft.idempotencyKey(), new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(), DETECTED_AT, DETECTED_AT)); }
        @Override public Optional<RecordEventSnapshot> find(UUID id) { return values.values().stream().filter(event -> event.draft().eventId().equals(id)).findFirst(); }
        @Override public List<RecordEventSnapshot> findByTriggerKey(long guild, String trigger) { return values.values().stream().filter(event -> event.draft().triggerKey().equals(trigger)).toList(); }
        @Override public boolean invalidate(UUID id, Instant at) { return false; }
        @Override public boolean supersede(UUID id, UUID successor, Instant at) { return false; }
    }
}
