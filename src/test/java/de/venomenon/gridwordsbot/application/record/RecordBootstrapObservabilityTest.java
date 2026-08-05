package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator.BootstrapRunResult;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapMetrics;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordPermanentFailure;
import de.venomenon.gridwordsbot.port.out.RecordRetryableFailure;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RecordBootstrapObservabilityTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void observesExactlyOnceForEveryKnownCoordinatorOutcomeAndUsesConfiguredDurations() {
        assertObserved(BootstrapRunResult.SUCCEEDED, null, true, true, guild -> empty(), Duration.ofSeconds(43), Duration.ofSeconds(29));
        assertObserved(BootstrapRunResult.NOT_CLAIMED, null, false, true, guild -> empty(), Duration.ofSeconds(43), Duration.ofSeconds(29));
        assertObserved(BootstrapRunResult.LOST_LEASE, null, true, false, guild -> empty(), Duration.ofSeconds(43), Duration.ofSeconds(29));
        assertObserved(BootstrapRunResult.RETRY_SCHEDULED, RecordWorkFailureCategory.RETRYABLE, true, true,
                history -> { throw new RecordRetryableFailure("test transient", null); }, Duration.ofSeconds(43), Duration.ofSeconds(29));
        assertObserved(BootstrapRunResult.FAILED_PERMANENT, RecordWorkFailureCategory.PERMANENT, true, true,
                history -> { throw new RecordPermanentFailure("test permanent", null); }, Duration.ofSeconds(43), Duration.ofSeconds(29));
    }

    @Test
    void observesUnknownFailureOnceAndRethrowsItUnchanged() {
        CapturingMetrics metrics = new CapturingMetrics();
        OutcomeStore store = new OutcomeStore(true, true);
        IllegalStateException failure = new IllegalStateException("unmapped SQL failure");
        RecordBootstrapCoordinator coordinator = coordinator(store, guild -> { throw failure; }, metrics,
                Duration.ofSeconds(43), Duration.ofSeconds(29));

        assertThatThrownBy(() -> coordinator.run(1)).isSameAs(failure);
        assertThat(metrics.observations).singleElement().satisfies(observation -> {
            assertThat(observation.result()).isEqualTo(BootstrapRunResult.UNKNOWN);
            assertThat(observation.category()).contains(RecordWorkFailureCategory.UNKNOWN);
        });
    }

    @Test
    void notClaimedPollingDoesNotProduceInfoLogNoise() {
        Logger logger = (Logger) LoggerFactory.getLogger(RecordBootstrapCoordinator.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            RecordBootstrapCoordinator coordinator = coordinator(new OutcomeStore(false, true), guild -> empty(),
                    new CapturingMetrics(), Duration.ofSeconds(5), Duration.ofSeconds(5));
            coordinator.run(1);
            coordinator.run(1);
            assertThat(logs.list).noneMatch(event -> event.getLevel() == Level.INFO);
        } finally {
            logger.detachAppender(logs);
        }
    }

    @Test
    void usesInfoForSuccessWarnForRecoverableWorkAndErrorForPermanentOrUnknownFailures() {
        Logger logger = (Logger) LoggerFactory.getLogger(RecordBootstrapCoordinator.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertLog(logs, coordinator(new OutcomeStore(true, true), guild -> empty(), new CapturingMetrics(),
                    Duration.ofSeconds(5), Duration.ofSeconds(5)), BootstrapRunResult.SUCCEEDED, "record_bootstrap_succeeded", Level.INFO);
            assertLog(logs, coordinator(new OutcomeStore(true, true), guild -> { throw new RecordRetryableFailure("retry", null); }, new CapturingMetrics(),
                    Duration.ofSeconds(5), Duration.ofSeconds(5)), BootstrapRunResult.RETRY_SCHEDULED, "record_bootstrap_retry", Level.WARN);
            assertLog(logs, coordinator(new OutcomeStore(true, true), guild -> { throw new RecordPermanentFailure("permanent", null); }, new CapturingMetrics(),
                    Duration.ofSeconds(5), Duration.ofSeconds(5)), BootstrapRunResult.FAILED_PERMANENT, "record_bootstrap_permanent_failure", Level.ERROR);
            logs.list.clear();
            RecordBootstrapCoordinator unknown = coordinator(new OutcomeStore(true, true), guild -> { throw new IllegalStateException("unknown"); },
                    new CapturingMetrics(), Duration.ofSeconds(5), Duration.ofSeconds(5));
            assertThatThrownBy(() -> unknown.run(1)).isInstanceOf(IllegalStateException.class);
            assertThat(logs.list).anySatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("record_bootstrap_unknown_failure");
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            });
        } finally {
            logger.detachAppender(logs);
        }
    }

    private static void assertLog(ListAppender<ILoggingEvent> logs, RecordBootstrapCoordinator coordinator,
            BootstrapRunResult expected, String message, Level level) {
        logs.list.clear();
        assertThat(coordinator.run(1)).isEqualTo(expected);
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getFormattedMessage()).contains(message);
            assertThat(event.getLevel()).isEqualTo(level);
        });
    }

    private static void assertObserved(BootstrapRunResult expectedResult, RecordWorkFailureCategory expectedCategory,
            boolean claim, boolean renew, RecordHistoryQuery history, Duration lease, Duration retryBackoff) {
        CapturingMetrics metrics = new CapturingMetrics();
        OutcomeStore store = new OutcomeStore(claim, renew);
        RecordBootstrapCoordinator coordinator = coordinator(store, history, metrics, lease, retryBackoff);

        assertThat(coordinator.run(1)).isEqualTo(expectedResult);
        assertThat(metrics.observations).singleElement().satisfies(observation -> {
            assertThat(observation.result()).isEqualTo(expectedResult);
            if (expectedCategory == null) assertThat(observation.category()).isEmpty();
            else assertThat(observation.category()).contains(expectedCategory);
            assertThat(observation.duration().isNegative()).isFalse();
        });
        if (claim) assertThat(store.claimRequest.leaseUntil()).isEqualTo(NOW.plus(lease));
        if (expectedResult == BootstrapRunResult.RETRY_SCHEDULED) {
            assertThat(store.retryAt).isEqualTo(NOW.plus(retryBackoff));
        }
    }

    private static RecordBootstrapCoordinator coordinator(OutcomeStore store, RecordHistoryQuery history,
            CapturingMetrics metrics, Duration lease, Duration retryBackoff) {
        RecordStateService states = new RecordStateService(new EmptyStateStore(), new NoEvents(), directTransactions(),
                RecordDefinitionCatalog.recordsV1());
        return new RecordBootstrapCoordinator(store, history, states, RecordDefinitionCatalog.recordsV1(), CLOCK,
                lease, retryBackoff, metrics);
    }

    private static de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot empty() {
        return new de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot(List.of(), List.of());
    }
    private static RecordTransactionRunner directTransactions() { return new RecordTransactionRunner() {
        @Override public <T> T inTransaction(java.util.function.Supplier<T> work) { return work.get(); }
    }; }

    private record Observation(BootstrapRunResult result, Optional<RecordWorkFailureCategory> category, Duration duration) { }
    private static final class CapturingMetrics implements RecordBootstrapMetrics {
        private final List<Observation> observations = new ArrayList<>();
        @Override public void record(BootstrapRunResult result, Optional<RecordWorkFailureCategory> category, Duration duration) {
            observations.add(new Observation(result, category, duration));
        }
    }
    private static final class OutcomeStore implements RecordBootstrapStore {
        private final boolean claim; private final boolean renew; private final UUID token = UUID.randomUUID();
        private RecordLeaseClaimRequest claimRequest; private Instant retryAt;
        OutcomeStore(boolean claim, boolean renew) { this.claim = claim; this.renew = renew; }
        @Override public RecordBootstrapSnapshot register(RecordBootstrapKey key) { return snapshot(key, RecordWorkState.OPEN); }
        @Override public Optional<RecordBootstrapSnapshot> find(RecordBootstrapKey key) { return Optional.of(snapshot(key, RecordWorkState.CLAIMED)); }
        @Override public Optional<RecordLeaseClaim> claim(RecordBootstrapKey key, RecordLeaseClaimRequest request) {
            claimRequest = request; return claim ? Optional.of(new RecordLeaseClaim(token, request.leaseUntil())) : Optional.empty();
        }
        @Override public boolean renewLease(RecordBootstrapKey key, UUID candidate, RecordLeaseClaimRequest request) { return renew && token.equals(candidate); }
        @Override public boolean markSucceeded(RecordBootstrapKey key, UUID candidate, Instant completedAt) { return token.equals(candidate); }
        @Override public boolean markRetryableFailure(RecordBootstrapKey key, UUID candidate, RecordWorkFailure failure, Instant at) { retryAt = at; return token.equals(candidate); }
        @Override public boolean markPermanentFailure(RecordBootstrapKey key, UUID candidate, RecordWorkFailure failure, Instant completedAt) { return token.equals(candidate); }
        private RecordBootstrapSnapshot snapshot(RecordBootstrapKey key, RecordWorkState state) {
            Optional<UUID> claimToken = state == RecordWorkState.CLAIMED ? Optional.of(token) : Optional.empty();
            Optional<Instant> claimUntil = state == RecordWorkState.CLAIMED ? Optional.of(NOW.plusSeconds(60)) : Optional.empty();
            return new RecordBootstrapSnapshot(key, state, claimToken, claimUntil, Optional.of(NOW), Optional.empty(), 1,
                    Optional.empty(), Optional.empty(), NOW, NOW);
        }
    }
    private static final class EmptyStateStore implements RecordStateStore {
        @Override public Optional<de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot> find(de.venomenon.gridwordsbot.domain.record.RecordStateKey key) { return Optional.empty(); }
        @Override public List<de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot> findAll(long guild, de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion version) { return List.of(); }
        @Override public de.venomenon.gridwordsbot.domain.record.RecordStateInitialization initialize(de.venomenon.gridwordsbot.domain.record.RecordStateKey key, de.venomenon.gridwordsbot.domain.record.RecordStateWrite write) { throw new AssertionError("no targets"); }
        @Override public de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult update(de.venomenon.gridwordsbot.domain.record.RecordStateUpdate update) { throw new AssertionError("no targets"); }
        @Override public boolean remove(de.venomenon.gridwordsbot.domain.record.RecordStateKey key, de.venomenon.gridwordsbot.domain.record.RecordLockVersion version) { throw new AssertionError("no targets"); }
    }
    private static final class NoEvents implements RecordEventStore {
        @Override public de.venomenon.gridwordsbot.domain.record.RecordEventAppendResult append(de.venomenon.gridwordsbot.domain.record.RecordEventDraft draft) { throw new AssertionError("no targets"); }
        @Override public Optional<de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot> find(UUID id) { return Optional.empty(); }
        @Override public List<de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot> findByTriggerKey(long guild, String key) { return List.of(); }
        @Override public boolean invalidate(UUID id, Instant at) { return false; }
        @Override public boolean supersede(UUID id, UUID successor, Instant at) { return false; }
    }
}
