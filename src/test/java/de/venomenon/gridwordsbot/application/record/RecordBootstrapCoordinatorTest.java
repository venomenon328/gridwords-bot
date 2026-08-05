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
import java.util.Optional;
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
