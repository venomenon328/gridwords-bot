package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.port.out.RecordEventIdempotencyConflictException;
import de.venomenon.gridwordsbot.port.out.RecordLiveEvaluationStore;
import de.venomenon.gridwordsbot.port.out.RecordPermanentFailure;
import de.venomenon.gridwordsbot.port.out.RecordRetryableFailure;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls exactly one durable live-evaluation job, keeps its lease alive, and
 * classifies only explicitly translated failures. Unknown technical failures
 * deliberately escape unchanged so PostgreSQL recovery can reclaim the job.
 */
public final class RecordLiveEvaluationCoordinator {
    private static final String RETRYABLE_FAILURE = "record live evaluation retryable failure";
    private static final String PERMANENT_FAILURE = "record live evaluation permanent failure";

    private final RecordLiveEvaluationStore work;
    private final RecordLiveEvaluationWorkProcessor processor;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final Duration initialRetryBackoff;
    private final Duration maxRetryBackoff;
    private final ScheduledExecutorService heartbeatExecutor;
    private final RecordLiveEvaluationMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean();

    public RecordLiveEvaluationCoordinator(
            RecordLiveEvaluationStore work,
            RecordLiveEvaluationProcessor processor,
            Clock clock,
            Duration leaseDuration,
            Duration heartbeatInterval,
            Duration initialRetryBackoff,
            Duration maxRetryBackoff,
            ScheduledExecutorService heartbeatExecutor,
            RecordLiveEvaluationMetrics metrics) {
        this(work, processor::process, clock, leaseDuration, heartbeatInterval, initialRetryBackoff,
                maxRetryBackoff, heartbeatExecutor, metrics);
    }

    public RecordLiveEvaluationCoordinator(
            RecordLiveEvaluationStore work,
            RecordLiveEvaluationWorkProcessor processor,
            Clock clock,
            Duration leaseDuration,
            Duration heartbeatInterval,
            Duration initialRetryBackoff,
            Duration maxRetryBackoff,
            ScheduledExecutorService heartbeatExecutor,
            RecordLiveEvaluationMetrics metrics) {
        this.work = java.util.Objects.requireNonNull(work, "work");
        this.processor = java.util.Objects.requireNonNull(processor, "processor");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.heartbeatInterval = positive(heartbeatInterval, "heartbeatInterval");
        this.initialRetryBackoff = positive(initialRetryBackoff, "initialRetryBackoff");
        this.maxRetryBackoff = positive(maxRetryBackoff, "maxRetryBackoff");
        if (heartbeatInterval.compareTo(leaseDuration) >= 0) {
            throw new IllegalArgumentException("heartbeatInterval must be shorter than leaseDuration");
        }
        if (initialRetryBackoff.compareTo(maxRetryBackoff) > 0) {
            throw new IllegalArgumentException("initialRetryBackoff must not exceed maxRetryBackoff");
        }
        this.heartbeatExecutor = java.util.Objects.requireNonNull(heartbeatExecutor, "heartbeatExecutor");
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
    }

    /** Runs at most one local execution. Durable claim eligibility remains owned by PostgreSQL. */
    public RunResult runNext() {
        if (!running.compareAndSet(false, true)) {
            return RunResult.SKIPPED_ALREADY_RUNNING;
        }
        long startedNanos = System.nanoTime();
        RecordLiveEvaluationClaim claim = null;
        RunResult result = null;
        LeaseHeartbeat heartbeat = null;
        try {
            Instant now = clock.instant();
            Optional<RecordLiveEvaluationClaim> claimed = work.claimNext(leaseRequest(now));
            if (claimed.isEmpty()) {
                return RunResult.NOT_CLAIMED;
            }
            claim = claimed.orElseThrow();
            heartbeat = new LeaseHeartbeat(claim);
            if (!heartbeat.renewNow()) {
                result = RunResult.LOST_LEASE;
                RecordLiveEvaluationLog.lostLease(claim, elapsed(startedNanos));
                return result;
            }
            heartbeat.start();

            RecordLiveEvaluationProcessor.ProcessingResult processed = processor.process(claim, heartbeat::leaseOwned);
            if (processed == RecordLiveEvaluationProcessor.ProcessingResult.FENCED_OUT) {
                result = RunResult.LOST_LEASE;
                RecordLiveEvaluationLog.lostLease(claim, elapsed(startedNanos));
                return result;
            }
            result = RunResult.COMPLETED;
            RecordLiveEvaluationLog.completed(claim, elapsed(startedNanos));
            return result;
        } catch (RecordRetryableFailure failure) {
            if (claim == null) {
                throw failure;
            }
            try {
                if (heartbeat != null && !heartbeat.leaseOwned()) {
                    result = RunResult.LOST_LEASE;
                    RecordLiveEvaluationLog.lostLease(claim, elapsed(startedNanos));
                    return result;
                }
            } catch (RuntimeException unexpectedFailure) {
                result = RunResult.UNKNOWN;
                RecordLiveEvaluationLog.unknown(claim, elapsed(startedNanos), unexpectedFailure);
                throw unexpectedFailure;
            }
            Duration backoff = retryBackoff(claim.attemptCount());
            boolean marked = work.markRetryableFailure(
                    claim.key(), claim.token(),
                    new RecordWorkFailure(RecordWorkFailureCategory.RETRYABLE, RETRYABLE_FAILURE),
                    clock.instant().plus(backoff));
            result = marked ? RunResult.FAILED_RETRYABLE : RunResult.LOST_LEASE;
            if (marked) {
                RecordLiveEvaluationLog.retryable(claim, backoff, elapsed(startedNanos));
            } else {
                RecordLiveEvaluationLog.lostLease(claim, elapsed(startedNanos));
            }
            return result;
        } catch (RecordPermanentFailure | RecordEventIdempotencyConflictException failure) {
            if (claim == null) {
                throw failure;
            }
            try {
                if (heartbeat != null && !heartbeat.leaseOwned()) {
                    result = RunResult.LOST_LEASE;
                    RecordLiveEvaluationLog.lostLease(claim, elapsed(startedNanos));
                    return result;
                }
            } catch (RuntimeException unexpectedFailure) {
                result = RunResult.UNKNOWN;
                RecordLiveEvaluationLog.unknown(claim, elapsed(startedNanos), unexpectedFailure);
                throw unexpectedFailure;
            }
            boolean marked = work.markPermanentFailure(
                    claim.key(), claim.token(),
                    new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT, PERMANENT_FAILURE), clock.instant());
            result = marked ? RunResult.FAILED_PERMANENT : RunResult.LOST_LEASE;
            if (marked) {
                RecordLiveEvaluationLog.permanent(claim, elapsed(startedNanos));
            } else {
                RecordLiveEvaluationLog.lostLease(claim, elapsed(startedNanos));
            }
            return result;
        } catch (RuntimeException failure) {
            if (claim != null) {
                result = RunResult.UNKNOWN;
                RecordLiveEvaluationLog.unknown(claim, elapsed(startedNanos), failure);
            }
            throw failure;
        } finally {
            if (heartbeat != null) {
                heartbeat.close();
            }
            if (claim != null && result != null) {
                metrics.record(result, elapsed(startedNanos));
            }
            running.set(false);
        }
    }

    private RecordLeaseClaimRequest leaseRequest(Instant now) {
        return new RecordLeaseClaimRequest(now, now.plus(leaseDuration));
    }

    private Duration retryBackoff(int attemptCount) {
        Duration backoff = initialRetryBackoff;
        for (int retry = 1; retry < attemptCount && backoff.compareTo(maxRetryBackoff) < 0; retry++) {
            try {
                backoff = backoff.multipliedBy(2);
            } catch (ArithmeticException overflow) {
                return maxRetryBackoff;
            }
        }
        return backoff.compareTo(maxRetryBackoff) > 0 ? maxRetryBackoff : backoff;
    }

    private static Duration positive(Duration value, String name) {
        java.util.Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Duration elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    private final class LeaseHeartbeat implements AutoCloseable {
        private final RecordLiveEvaluationClaim claim;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean owned = new AtomicBoolean(true);
        private final AtomicReference<RuntimeException> unexpectedFailure = new AtomicReference<>();
        private volatile ScheduledFuture<?> future;

        private LeaseHeartbeat(RecordLiveEvaluationClaim claim) {
            this.claim = claim;
        }

        private boolean renewNow() {
            return renew();
        }

        private void start() {
            future = heartbeatExecutor.scheduleWithFixedDelay(
                    this::heartbeat,
                    heartbeatInterval.toNanos(),
                    heartbeatInterval.toNanos(),
                    TimeUnit.NANOSECONDS);
        }

        private void heartbeat() {
            if (closed.get()) {
                return;
            }
            try {
                boolean renewed = renew();
                if (!renewed && !closed.get()) {
                    owned.set(false);
                }
            } catch (RuntimeException failure) {
                if (!closed.get()) {
                    unexpectedFailure.compareAndSet(null, failure);
                }
            }
        }

        private boolean renew() {
            Instant now = clock.instant();
            return work.renewLease(claim.key(), claim.token(), leaseRequest(now));
        }

        private boolean leaseOwned() {
            RuntimeException failure = unexpectedFailure.get();
            if (failure != null) {
                throw failure;
            }
            return owned.get();
        }

        @Override
        public void close() {
            closed.set(true);
            ScheduledFuture<?> current = future;
            if (current != null) {
                current.cancel(false);
            }
        }
    }

    public enum RunResult {
        COMPLETED,
        FAILED_RETRYABLE,
        FAILED_PERMANENT,
        LOST_LEASE,
        UNKNOWN,
        NOT_CLAIMED,
        SKIPPED_ALREADY_RUNNING
    }
}
