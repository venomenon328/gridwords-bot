package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapProjection;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventAppendResult;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLockVersion;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateInitialization;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdate;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordStateSourceOrderTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-05T08:00:00Z");
    private static final LocalDate GAME_DATE = LocalDate.of(2026, 8, 4);
    private static final RecordStateKey KEY = new RecordStateKey(
            1,
            new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
            RecordDefinitionVersion.RECORDS_V1,
            new RecordScope.Personal(7));
    private static final AttemptsDurationRecordValue VALUE =
            new AttemptsDurationRecordValue(2, Duration.ofSeconds(50));

    @Test
    void bootstrapProjectionCarriesFirstAcceptanceIntoEveryResultState() {
        RecordHistorySnapshot history = new RecordHistorySnapshot(
                List.of(new RecordHistorySnapshot.Result(
                        10,
                        0,
                        7,
                        GameType.GRIDWORDS,
                        GAME_DATE,
                        new ShareOutcome.Solved(2, 6),
                        Duration.ofSeconds(50),
                        Instant.parse("2026-08-04T09:00:00Z"))),
                List.of());
        RecordBootstrapProjection projection = new RecordBootstrapProjection(
                RecordDefinitionCatalog.recordsV1(),
                new de.venomenon.gridwordsbot.domain.record.StreakRunAnalyzer());

        List<RecordBootstrapProjection.Candidate> resultCandidates = projection.project(
                        1,
                        history,
                        new de.venomenon.gridwordsbot.domain.record.StreakRunAnalysisWindow(
                                GAME_DATE, GAME_DATE, false))
                .stream()
                .filter(candidate -> candidate.write().source()
                        instanceof RecordSourceReference.GameResult)
                .toList();

        assertThat(resultCandidates).isNotEmpty();
        assertThat(resultCandidates)
                .allSatisfy(candidate -> assertThat(candidate.write().sourceGameFirstAcceptedAt())
                        .contains(Instant.parse("2026-08-04T09:00:00Z")));
    }

    @Test
    void earlierFirstAcceptanceWinsBeforeResultIdAfterRestart() {
        RecordStateWrite current = resultWrite(10, Instant.parse("2026-08-04T10:00:00Z"));
        RecordStateWrite candidate = resultWrite(20, Instant.parse("2026-08-04T09:00:00Z"));
        MemoryStates states = new MemoryStates(snapshot(current));

        assertThat(service(states).rebuild(
                new RecordBootstrapProjection.Candidate(KEY, candidate), "bootstrap", CREATED_AT))
                .isEqualTo(RecordStateService.RebuildResult.REPLACED);
        assertThat(states.current.source()).isEqualTo(candidate.source());
        assertThat(states.current.sourceGameFirstAcceptedAt())
                .contains(Instant.parse("2026-08-04T09:00:00Z"));
    }

    @Test
    void resultIdRemainsTheFinalTieBreaker() {
        Instant acceptedAt = Instant.parse("2026-08-04T09:00:00Z");
        RecordStateWrite current = resultWrite(20, acceptedAt);
        RecordStateWrite candidate = resultWrite(10, acceptedAt);
        MemoryStates states = new MemoryStates(snapshot(current));

        assertThat(service(states).rebuild(
                new RecordBootstrapProjection.Candidate(KEY, candidate), "bootstrap", CREATED_AT))
                .isEqualTo(RecordStateService.RebuildResult.REPLACED);
        assertThat(((RecordSourceReference.GameResult) states.current.source()).resultId()).isEqualTo(10);
    }

    @Test
    void completeCandidateRepairsLegacyStateWithoutAcceptanceTimestamp() {
        RecordStateWrite legacy = new RecordStateWrite(
                Optional.of(7L), VALUE, gameResult(10), Optional.empty(), false);
        RecordStateWrite candidate = resultWrite(10, Instant.parse("2026-08-04T09:00:00Z"));
        MemoryStates states = new MemoryStates(snapshot(legacy));

        assertThat(service(states).rebuild(
                new RecordBootstrapProjection.Candidate(KEY, candidate), "bootstrap", CREATED_AT))
                .isEqualTo(RecordStateService.RebuildResult.REPLACED);
        assertThat(states.current.sourceGameFirstAcceptedAt())
                .contains(Instant.parse("2026-08-04T09:00:00Z"));
    }

    private static RecordStateService service(MemoryStates states) {
        RecordTransactionRunner transactions = new RecordTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return work.get();
            }
        };
        return new RecordStateService(states, new NoEvents(), transactions, RecordDefinitionCatalog.recordsV1());
    }

    private static RecordStateWrite resultWrite(long resultId, Instant firstAcceptedAt) {
        return new RecordStateWrite(
                Optional.of(7L),
                VALUE,
                gameResult(resultId),
                Optional.of(firstAcceptedAt),
                false);
    }

    private static RecordSourceReference.GameResult gameResult(long resultId) {
        return new RecordSourceReference.GameResult(
                resultId, 0, 7, GameType.GRIDWORDS, GAME_DATE);
    }

    private static RecordStateSnapshot snapshot(RecordStateWrite write) {
        return new RecordStateSnapshot(
                KEY,
                write.holderPlayerId(),
                write.value(),
                write.source(),
                write.sourceGameFirstAcceptedAt(),
                write.running(),
                RecordLockVersion.initial(),
                CREATED_AT,
                CREATED_AT);
    }

    private static final class MemoryStates implements RecordStateStore {
        private RecordStateSnapshot current;

        private MemoryStates(RecordStateSnapshot current) {
            this.current = current;
        }

        @Override
        public Optional<RecordStateSnapshot> find(RecordStateKey key) {
            return KEY.equals(key) ? Optional.ofNullable(current) : Optional.empty();
        }

        @Override
        public List<RecordStateSnapshot> findAll(long guildId, RecordDefinitionVersion definitionVersion) {
            return current == null ? List.of() : List.of(current);
        }

        @Override
        public RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write) {
            if (current != null) return new RecordStateInitialization.Existing(current);
            current = snapshot(write);
            return new RecordStateInitialization.Created(current);
        }

        @Override
        public RecordStateUpdateResult update(RecordStateUpdate update) {
            if (current == null || !current.lockVersion().equals(update.expectedLockVersion())) {
                return new RecordStateUpdateResult(
                        RecordStateUpdateResult.Status.VERSION_CONFLICT, Optional.empty());
            }
            RecordStateWrite write = update.write();
            current = new RecordStateSnapshot(
                    update.key(),
                    write.holderPlayerId(),
                    write.value(),
                    write.source(),
                    write.sourceGameFirstAcceptedAt(),
                    write.running(),
                    current.lockVersion().next(),
                    current.createdAt(),
                    CREATED_AT.plusSeconds(1));
            return new RecordStateUpdateResult(
                    RecordStateUpdateResult.Status.UPDATED, Optional.of(current));
        }

        @Override
        public boolean remove(RecordStateKey key, RecordLockVersion expectedLockVersion) {
            if (current == null || !current.lockVersion().equals(expectedLockVersion)) return false;
            current = null;
            return true;
        }
    }

    private static final class NoEvents implements RecordEventStore {
        @Override
        public RecordEventAppendResult append(RecordEventDraft draft) {
            throw new AssertionError("must not append");
        }

        @Override
        public Optional<RecordEventSnapshot> find(UUID eventId) {
            return Optional.empty();
        }

        @Override
        public List<RecordEventSnapshot> findByTriggerKey(long guildId, String triggerKey) {
            return List.of();
        }

        @Override
        public boolean invalidate(UUID eventId, Instant invalidatedAt) {
            return false;
        }

        @Override
        public boolean supersede(UUID eventId, UUID supersedingEventId, Instant invalidatedAt) {
            return false;
        }
    }
}
