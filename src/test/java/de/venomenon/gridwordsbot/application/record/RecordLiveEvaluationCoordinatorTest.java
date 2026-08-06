package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationCoordinator.RunResult;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.port.out.RecordLiveEvaluationStore;
import de.venomenon.gridwordsbot.port.out.RecordPermanentFailure;
import de.venomenon.gridwordsbot.port.out.RecordRetryableFailure;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecordLiveEvaluationCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

    @Test
    void completesOneClaimAndReportsExactlyOneCompletedOutcome() {
        RecordLiveEvaluationStore work = org.mockito.Mockito.mock(RecordLiveEvaluationStore.class);
        RecordLiveEvaluationClaim claim = claim(1);
        when(work.claimNext(any())).thenReturn(Optional.of(claim));
        when(work.renewLease(any(), any(), any())).thenReturn(true);
        when(work.markSucceeded(claim.key(), claim.token(), NOW)).thenReturn(true);
        RecordingMetrics metrics = new RecordingMetrics();

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            RecordLiveEvaluationCoordinator coordinator = coordinator(work, (owned, ignored) -> {
                assertThat(ignored.getAsBoolean()).isTrue();
                assertThat(work.markSucceeded(claim.key(), claim.token(), NOW)).isTrue();
                return RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED;
            }, heartbeat, metrics);

            assertThat(coordinator.runNext()).isEqualTo(RunResult.COMPLETED);
        }

        assertThat(metrics.outcomes).containsExactly(RunResult.COMPLETED);
        verify(work, times(1)).markSucceeded(claim.key(), claim.token(), NOW);
    }

    @Test
    void retryableFailureUsesBoundedExponentialBackoffAndReportsOneRetryableOutcome() {
        RecordLiveEvaluationStore work = org.mockito.Mockito.mock(RecordLiveEvaluationStore.class);
        RecordLiveEvaluationClaim claim = claim(4);
        when(work.claimNext(any())).thenReturn(Optional.of(claim));
        when(work.renewLease(any(), any(), any())).thenReturn(true);
        when(work.markRetryableFailure(any(), any(), any(), any())).thenReturn(true);
        RecordingMetrics metrics = new RecordingMetrics();

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            RecordLiveEvaluationCoordinator coordinator = coordinator(work,
                    (ignoredClaim, owned) -> { throw new RecordRetryableFailure("transient", null); }, heartbeat, metrics);

            assertThat(coordinator.runNext()).isEqualTo(RunResult.FAILED_RETRYABLE);
        }

        ArgumentCaptor<Instant> nextRetry = ArgumentCaptor.forClass(Instant.class);
        verify(work).markRetryableFailure(
                org.mockito.ArgumentMatchers.eq(claim.key()),
                org.mockito.ArgumentMatchers.eq(claim.token()), any(), nextRetry.capture());
        assertThat(nextRetry.getValue()).isEqualTo(NOW.plusSeconds(5));
        assertThat(metrics.outcomes).containsExactly(RunResult.FAILED_RETRYABLE);
    }

    @Test
    void knownPermanentFailureIsPersistedWithoutTreatingItAsRetryable() {
        RecordLiveEvaluationStore work = org.mockito.Mockito.mock(RecordLiveEvaluationStore.class);
        RecordLiveEvaluationClaim claim = claim(1);
        when(work.claimNext(any())).thenReturn(Optional.of(claim));
        when(work.renewLease(any(), any(), any())).thenReturn(true);
        when(work.markPermanentFailure(any(), any(), any(), any())).thenReturn(true);
        RecordingMetrics metrics = new RecordingMetrics();

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            RecordLiveEvaluationCoordinator coordinator = coordinator(work,
                    (ignoredClaim, owned) -> { throw new RecordPermanentFailure("invalid invariant", null); }, heartbeat, metrics);

            assertThat(coordinator.runNext()).isEqualTo(RunResult.FAILED_PERMANENT);
        }

        verify(work).markPermanentFailure(any(), any(), any(), any());
        verify(work, never()).markRetryableFailure(any(), any(), any(), any());
        assertThat(metrics.outcomes).containsExactly(RunResult.FAILED_PERMANENT);
    }

    @Test
    void unknownTechnicalFailureEscapesUnchangedAndDoesNotWriteAClassification() {
        RecordLiveEvaluationStore work = org.mockito.Mockito.mock(RecordLiveEvaluationStore.class);
        RecordLiveEvaluationClaim claim = claim(1);
        IllegalStateException unknown = new IllegalStateException("database mapping exploded");
        when(work.claimNext(any())).thenReturn(Optional.of(claim));
        when(work.renewLease(any(), any(), any())).thenReturn(true);
        RecordingMetrics metrics = new RecordingMetrics();

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            RecordLiveEvaluationCoordinator coordinator = coordinator(work,
                    (ignoredClaim, owned) -> { throw unknown; }, heartbeat, metrics);

            assertThatThrownBy(coordinator::runNext).isSameAs(unknown);
        }

        verify(work, never()).markRetryableFailure(any(), any(), any(), any());
        verify(work, never()).markPermanentFailure(any(), any(), any(), any());
        assertThat(metrics.outcomes).containsExactly(RunResult.UNKNOWN);
    }

    @Test
    void heartbeatTechnicalFailureDuringRetryableHandlingEscapesUnchanged() throws Exception {
        RecordLiveEvaluationStore work = org.mockito.Mockito.mock(RecordLiveEvaluationStore.class);
        RecordLiveEvaluationClaim claim = claim(1);
        IllegalStateException unknown = new IllegalStateException("heartbeat database mapping exploded");
        CountDownLatch heartbeatFailed = new CountDownLatch(1);
        when(work.claimNext(any())).thenReturn(Optional.of(claim));
        when(work.renewLease(any(), any(), any())).thenReturn(true).thenAnswer(invocation -> {
            heartbeatFailed.countDown();
            throw unknown;
        });
        RecordingMetrics metrics = new RecordingMetrics();

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            RecordLiveEvaluationCoordinator coordinator = coordinator(work, (ignoredClaim, owned) -> {
                await(heartbeatFailed);
                throw new RecordRetryableFailure("transient", null);
            }, heartbeat, metrics, Duration.ofMillis(5));

            assertThatThrownBy(coordinator::runNext).isSameAs(unknown);
        }

        verify(work, never()).markRetryableFailure(any(), any(), any(), any());
        verify(work, never()).markPermanentFailure(any(), any(), any(), any());
        assertThat(metrics.outcomes).containsExactly(RunResult.UNKNOWN);
    }

    @Test
    void lostHeartbeatPreventsTerminalWritesAndIsObservedOnlyAsLostLease() throws Exception {
        RecordLiveEvaluationStore work = org.mockito.Mockito.mock(RecordLiveEvaluationStore.class);
        RecordLiveEvaluationClaim claim = claim(1);
        when(work.claimNext(any())).thenReturn(Optional.of(claim));
        when(work.renewLease(any(), any(), any())).thenReturn(true, false);
        RecordingMetrics metrics = new RecordingMetrics();
        CountDownLatch lost = new CountDownLatch(1);

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            RecordLiveEvaluationCoordinator coordinator = coordinator(work, (ignoredClaim, owned) -> {
                while (owned.getAsBoolean()) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("test interrupted", exception);
                    }
                }
                lost.countDown();
                return RecordLiveEvaluationProcessor.ProcessingResult.FENCED_OUT;
            }, heartbeat, metrics, Duration.ofMillis(5));

            assertThat(coordinator.runNext()).isEqualTo(RunResult.LOST_LEASE);
        }

        assertThat(lost.await(1, TimeUnit.SECONDS)).isTrue();
        verify(work, never()).markSucceeded(any(), any(), any());
        verify(work, never()).markRetryableFailure(any(), any(), any(), any());
        verify(work, never()).markPermanentFailure(any(), any(), any(), any());
        assertThat(metrics.outcomes).containsExactly(RunResult.LOST_LEASE);
    }

    @Test
    void overlappingLocalPollIsSkippedWhileTheClaimedExecutionRuns() throws Exception {
        RecordLiveEvaluationStore work = org.mockito.Mockito.mock(RecordLiveEvaluationStore.class);
        RecordLiveEvaluationClaim claim = claim(1);
        when(work.claimNext(any())).thenReturn(Optional.of(claim));
        when(work.renewLease(any(), any(), any())).thenReturn(true);
        when(work.markSucceeded(any(), any(), any())).thenReturn(true);
        RecordingMetrics metrics = new RecordingMetrics();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
                var callers = Executors.newSingleThreadExecutor()) {
            RecordLiveEvaluationCoordinator coordinator = coordinator(work, (ignoredClaim, owned) -> {
                entered.countDown();
                try {
                    if (!release.await(1, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test interrupted", exception);
                }
                work.markSucceeded(claim.key(), claim.token(), NOW);
                return RecordLiveEvaluationProcessor.ProcessingResult.PROCESSED;
            }, heartbeat, metrics);
            var first = callers.submit(coordinator::runNext);
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.runNext()).isEqualTo(RunResult.SKIPPED_ALREADY_RUNNING);
            release.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo(RunResult.COMPLETED);
        }

        verify(work, times(1)).claimNext(any(RecordLeaseClaimRequest.class));
        assertThat(metrics.outcomes).containsExactly(RunResult.COMPLETED);
    }

    private static RecordLiveEvaluationCoordinator coordinator(
            RecordLiveEvaluationStore work,
            RecordLiveEvaluationWorkProcessor processor,
            ScheduledExecutorService heartbeat,
            RecordingMetrics metrics) {
        return coordinator(work, processor, heartbeat, metrics, Duration.ofSeconds(1));
    }

    private static RecordLiveEvaluationCoordinator coordinator(
            RecordLiveEvaluationStore work,
            RecordLiveEvaluationWorkProcessor processor,
            ScheduledExecutorService heartbeat,
            RecordingMetrics metrics,
            Duration heartbeatInterval) {
        return new RecordLiveEvaluationCoordinator(work, processor, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(10), heartbeatInterval, Duration.ofSeconds(1), Duration.ofSeconds(5), heartbeat, metrics);
    }

    private static RecordLiveEvaluationClaim claim(int attempt) {
        return new RecordLiveEvaluationClaim(new RecordLiveEvaluationKey(7, 8, 2),
                RecordProcessingOrigin.NORMAL_CORRECTION, UUID.randomUUID(), NOW.plusSeconds(10), attempt);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", exception);
        }
    }

    private static final class RecordingMetrics implements RecordLiveEvaluationMetrics {
        private final java.util.List<RunResult> outcomes = new java.util.ArrayList<>();
        @Override public void record(RunResult result, Duration duration) { outcomes.add(result); }
    }
}
