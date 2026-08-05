package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator.BootstrapRunResult;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordLockVersion;
import de.venomenon.gridwordsbot.domain.record.RecordStateInitialization;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdate;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapMetrics;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordEventIdempotencyConflictException;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordPermanentFailure;
import de.venomenon.gridwordsbot.port.out.RecordRetryableFailure;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RecordBootstrapOperationalEdgeCasesTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void classifiesInitializationAnchorConflictFromStateMaterializationAsPermanent() {
        Store store = new Store(true, true);
        CapturingMetrics metrics = new CapturingMetrics();
        RecordStateService states = new RecordStateService(
                new CreatingStateStore(), new ConflictingEvents(), directTransactions(),
                RecordDefinitionCatalog.recordsV1());
        RecordBootstrapCoordinator coordinator = coordinator(store, guild -> oneSolvedResult(), states, metrics);

        assertThat(coordinator.run(1)).isEqualTo(BootstrapRunResult.FAILED_PERMANENT);
        assertThat(store.permanentFailurePersisted).isTrue();
        assertThat(metrics.observations).singleElement().satisfies(observation -> {
            assertThat(observation.result()).isEqualTo(BootstrapRunResult.FAILED_PERMANENT);
            assertThat(observation.category()).contains(RecordWorkFailureCategory.PERMANENT);
        });
    }

    @Test
    void staleFailureTokensLogOnlyTheActualLostLeaseOutcome() {
        Logger logger = (Logger) LoggerFactory.getLogger(RecordBootstrapCoordinator.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            CapturingMetrics retryMetrics = new CapturingMetrics();
            assertThat(coordinator(
                            new Store(false, true),
                            guild -> { throw new RecordRetryableFailure("retry", null); },
                            emptyStateService(),
                            retryMetrics)
                    .run(1))
                    .isEqualTo(BootstrapRunResult.LOST_LEASE);
            assertLostLeaseOnly(logs, "record_bootstrap_retry");
            assertObservation(retryMetrics, RecordWorkFailureCategory.RETRYABLE);

            logs.list.clear();
            CapturingMetrics permanentMetrics = new CapturingMetrics();
            assertThat(coordinator(
                            new Store(true, false),
                            guild -> { throw new RecordPermanentFailure("permanent", null); },
                            emptyStateService(),
                            permanentMetrics)
                    .run(1))
                    .isEqualTo(BootstrapRunResult.LOST_LEASE);
            assertLostLeaseOnly(logs, "record_bootstrap_permanent_failure");
            assertObservation(permanentMetrics, RecordWorkFailureCategory.PERMANENT);
        } finally {
            logger.detachAppender(logs);
        }
    }

    private static void assertLostLeaseOnly(ListAppender<ILoggingEvent> logs, String misleadingMessage) {
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains("record_bootstrap_lost_lease");
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
        });
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains(misleadingMessage));
    }

    private static void assertObservation(CapturingMetrics metrics, RecordWorkFailureCategory category) {
        assertThat(metrics.observations).singleElement().satisfies(observation -> {
            assertThat(observation.result()).isEqualTo(BootstrapRunResult.LOST_LEASE);
            assertThat(observation.category()).contains(category);
        });
    }

    private static RecordBootstrapCoordinator coordinator(
            Store store, RecordHistoryQuery history, RecordStateService states, CapturingMetrics metrics) {
        return new RecordBootstrapCoordinator(
                store, history, states, RecordDefinitionCatalog.recordsV1(), CLOCK,
                Duration.ofSeconds(5), Duration.ofSeconds(5), metrics);
    }

    private static RecordStateService emptyStateService() {
        return new RecordStateService(
                new EmptyStateStore(), new NoEvents(), directTransactions(), RecordDefinitionCatalog.recordsV1());
    }

    private static RecordHistorySnapshot oneSolvedResult() {
        return new RecordHistorySnapshot(
                List.of(new RecordHistorySnapshot.Result(
                        1L, 0L, 7L, GameType.GRIDWORDS, LocalDate.of(2026, 8, 4),
                        new ShareOutcome.Solved(3, 6), Duration.ofSeconds(50), NOW.minusSeconds(60))),
                List.of());
    }

    private static RecordTransactionRunner directTransactions() {
        return new RecordTransactionRunner() {
            @Override public <T> T inTransaction(java.util.function.Supplier<T> work) { return work.get(); }
        };
    }

    private record Observation(
            BootstrapRunResult result,
            Optional<RecordWorkFailureCategory> category,
            Duration duration) { }

    private static final class CapturingMetrics implements RecordBootstrapMetrics {
        private final List<Observation> observations = new ArrayList<>();
        @Override public void record(
                BootstrapRunResult result,
                Optional<RecordWorkFailureCategory> category,
                Duration duration) {
            observations.add(new Observation(result, category, duration));
        }
    }

    private static final class Store implements RecordBootstrapStore {
        private final boolean acceptRetryFailure;
        private final boolean acceptPermanentFailure;
        private final UUID token = UUID.randomUUID();
        private boolean permanentFailurePersisted;

        Store(boolean acceptRetryFailure, boolean acceptPermanentFailure) {
            this.acceptRetryFailure = acceptRetryFailure;
            this.acceptPermanentFailure = acceptPermanentFailure;
        }

        @Override public RecordBootstrapSnapshot register(RecordBootstrapKey key) {
            return snapshot(key, RecordWorkState.OPEN);
        }
        @Override public Optional<RecordBootstrapSnapshot> find(RecordBootstrapKey key) {
            return Optional.of(snapshot(key, RecordWorkState.CLAIMED));
        }
        @Override public Optional<RecordLeaseClaim> claim(
                RecordBootstrapKey key, RecordLeaseClaimRequest request) {
            return Optional.of(new RecordLeaseClaim(token, request.leaseUntil()));
        }
        @Override public boolean renewLease(
                RecordBootstrapKey key, UUID candidate, RecordLeaseClaimRequest request) {
            return token.equals(candidate);
        }
        @Override public boolean markSucceeded(
                RecordBootstrapKey key, UUID candidate, Instant completedAt) {
            return token.equals(candidate);
        }
        @Override public boolean markRetryableFailure(
                RecordBootstrapKey key, UUID candidate, RecordWorkFailure failure, Instant retryAt) {
            return acceptRetryFailure && token.equals(candidate);
        }
        @Override public boolean markPermanentFailure(
                RecordBootstrapKey key, UUID candidate, RecordWorkFailure failure, Instant completedAt) {
            permanentFailurePersisted = acceptPermanentFailure && token.equals(candidate);
            return permanentFailurePersisted;
        }
        private RecordBootstrapSnapshot snapshot(RecordBootstrapKey key, RecordWorkState state) {
            Optional<UUID> claimToken = state == RecordWorkState.CLAIMED ? Optional.of(token) : Optional.empty();
            Optional<Instant> claimUntil = state == RecordWorkState.CLAIMED
                    ? Optional.of(NOW.plusSeconds(60))
                    : Optional.empty();
            return new RecordBootstrapSnapshot(
                    key, state, claimToken, claimUntil, Optional.of(NOW),
                    Optional.empty(), 1, Optional.empty(), Optional.empty(), NOW, NOW);
        }
    }

    private static final class CreatingStateStore implements RecordStateStore {
        @Override public Optional<RecordStateSnapshot> find(RecordStateKey key) { return Optional.empty(); }
        @Override public List<RecordStateSnapshot> findAll(
                long guildId, de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion version) {
            return List.of();
        }
        @Override public RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write) {
            return new RecordStateInitialization.Created(new RecordStateSnapshot(
                    key, write.holderPlayerId(), write.value(), write.source(),
                    write.sourceGameFirstAcceptedAt(), write.running(), RecordLockVersion.initial(), NOW, NOW));
        }
        @Override public RecordStateUpdateResult update(RecordStateUpdate update) {
            throw new AssertionError("conflict occurs during initialization");
        }
        @Override public boolean remove(RecordStateKey key, RecordLockVersion version) {
            throw new AssertionError("conflict occurs during initialization");
        }
    }

    private static final class ConflictingEvents implements RecordEventStore {
        @Override public de.venomenon.gridwordsbot.domain.record.RecordEventAppendResult append(
                de.venomenon.gridwordsbot.domain.record.RecordEventDraft draft) {
            throw new RecordEventIdempotencyConflictException(draft.idempotencyKey());
        }
        @Override public Optional<de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot> find(UUID id) {
            return Optional.empty();
        }
        @Override public List<de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot> findByTriggerKey(
                long guildId, String triggerKey) {
            return List.of();
        }
        @Override public boolean invalidate(UUID id, Instant invalidatedAt) { return false; }
        @Override public boolean supersede(UUID id, UUID supersedingEventId, Instant invalidatedAt) { return false; }
    }

    private static final class EmptyStateStore implements RecordStateStore {
        @Override public Optional<RecordStateSnapshot> find(RecordStateKey key) { return Optional.empty(); }
        @Override public List<RecordStateSnapshot> findAll(
                long guildId, de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion version) {
            return List.of();
        }
        @Override public RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write) {
            throw new AssertionError("empty projection");
        }
        @Override public RecordStateUpdateResult update(RecordStateUpdate update) {
            throw new AssertionError("empty projection");
        }
        @Override public boolean remove(RecordStateKey key, RecordLockVersion version) {
            throw new AssertionError("empty projection");
        }
    }

    private static final class NoEvents implements RecordEventStore {
        @Override public de.venomenon.gridwordsbot.domain.record.RecordEventAppendResult append(
                de.venomenon.gridwordsbot.domain.record.RecordEventDraft draft) {
            throw new AssertionError("empty projection");
        }
        @Override public Optional<de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot> find(UUID id) {
            return Optional.empty();
        }
        @Override public List<de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot> findByTriggerKey(
                long guildId, String triggerKey) {
            return List.of();
        }
        @Override public boolean invalidate(UUID id, Instant invalidatedAt) { return false; }
        @Override public boolean supersede(UUID id, UUID supersedingEventId, Instant invalidatedAt) { return false; }
    }
}
