package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementClaim;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementMessage;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventValidity;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementMessageGateway;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claims and synchronizes one persisted announcement at a time. Every database write is token-fenced and every
 * Discord call occurs after the claiming transaction has ended. Unknown technical failures escape unchanged.
 */
public final class RecordAnnouncementDeliveryCoordinator {
    private static final Logger log = LoggerFactory.getLogger(RecordAnnouncementDeliveryCoordinator.class);
    private final RecordAnnouncementStore store;
    private final RecordEventStore events;
    private final PlayerStore players;
    private final RecordAnnouncementMessageGateway messages;
    private final RecordAnnouncementRenderer renderer;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final Duration initialRetryBackoff;
    private final Duration maxRetryBackoff;
    private final ScheduledExecutorService heartbeatExecutor;
    private final boolean publicAnnouncementsEnabled;
    private final RecordAnnouncementDeliveryMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean();

    public RecordAnnouncementDeliveryCoordinator(
            RecordAnnouncementStore store, RecordEventStore events, PlayerStore players,
            RecordAnnouncementMessageGateway messages, RecordAnnouncementRenderer renderer, Clock clock,
            Duration leaseDuration, Duration heartbeatInterval, Duration initialRetryBackoff, Duration maxRetryBackoff,
            ScheduledExecutorService heartbeatExecutor, boolean publicAnnouncementsEnabled,
            RecordAnnouncementDeliveryMetrics metrics) {
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.events = java.util.Objects.requireNonNull(events, "events");
        this.players = java.util.Objects.requireNonNull(players, "players");
        this.messages = java.util.Objects.requireNonNull(messages, "messages");
        this.renderer = java.util.Objects.requireNonNull(renderer, "renderer");
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
        this.publicAnnouncementsEnabled = publicAnnouncementsEnabled;
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
    }

    /** Runs one token-fenced delivery; the heartbeat remains active throughout blocking Discord I/O. */
    public RunResult runNext() {
        if (!running.compareAndSet(false, true)) return RunResult.SKIPPED_ALREADY_RUNNING;
        long started = System.nanoTime();
        RecordAnnouncementClaim claim = null;
        RecordAnnouncementProjection projection = null;
        RunResult result = null;
        LeaseHeartbeat heartbeat = null;
        try {
            if (!publicAnnouncementsEnabled) {
                int suppressed = store.suppressPendingCreates(clock.instant());
                if (suppressed > 0) {
                    log.info("record_announcement_delivery outcome=SUPPRESSED scope=never_attempted_create count={}", suppressed);
                }
            }
            Instant now = clock.instant();
            claim = store.claimNext(new RecordLeaseClaimRequest(now, now.plus(leaseDuration)), publicAnnouncementsEnabled)
                    .orElse(null);
            if (claim == null) return RunResult.NOT_CLAIMED;
            RecordAnnouncementSnapshot snapshot = owned(claim).orElse(null);
            if (snapshot == null) return result = RunResult.LOST_LEASE;
            projection = effectiveProjection(snapshot);
            heartbeat = new LeaseHeartbeat(claim);
            if (!heartbeat.renewNow()) return result = RunResult.LOST_LEASE;
            heartbeat.start();
            if (isStabilityCheck(snapshot)) {
                result = verifyStability(claim, snapshot, heartbeat)
                        ? RunResult.COMPLETED : RunResult.LOST_LEASE;
                return result;
            }
            if (!publicAnnouncementsEnabled && snapshot.publishedAt().isEmpty()
                    && projection == RecordAnnouncementProjection.CREATE) {
                result = suppressAttemptedCreate(claim, snapshot, heartbeat)
                        ? RunResult.SUPPRESSED : RunResult.LOST_LEASE;
                return result;
            }
            result = synchronize(claim, snapshot, heartbeat) ? RunResult.COMPLETED : RunResult.LOST_LEASE;
            return result;
        } catch (ExternallyRemoved removed) {
            result = RunResult.EXTERNALLY_REMOVED;
            return result;
        } catch (LostLease lostLease) {
            result = RunResult.LOST_LEASE;
            return result;
        } catch (RecordAnnouncementMessageGateway.PermanentMessageException failure) {
            if (claim == null) throw failure;
            if (!leaseOwnedAfterStoppingHeartbeat(heartbeat)) return result = RunResult.LOST_LEASE;
            log.warn("record_announcement_delivery gateway_failure=PERMANENT announcement_key={} safe_error={}",
                    claim.key().idempotencyKey(), failure.getMessage(), failure);
            result = markPermanent(claim, heartbeat, failure) ? RunResult.FAILED_PERMANENT : RunResult.LOST_LEASE;
            return result;
        } catch (RecordAnnouncementMessageGateway.MessageGatewayException failure) {
            if (claim == null) throw failure;
            if (!leaseOwnedAfterStoppingHeartbeat(heartbeat)) return result = RunResult.LOST_LEASE;
            log.warn("record_announcement_delivery gateway_failure=RETRYABLE announcement_key={} safe_error={}",
                    claim.key().idempotencyKey(), failure.getMessage(), failure);
            result = markRetryable(claim, heartbeat, failure) ? RunResult.FAILED_RETRYABLE : RunResult.LOST_LEASE;
            return result;
        } catch (RuntimeException unknown) {
            result = claim == null ? null : RunResult.UNKNOWN;
            throw unknown;
        } finally {
            if (heartbeat != null) heartbeat.close();
            if (claim != null && result != null) {
                metrics.record(result, elapsed(started));
                log.info("record_announcement_delivery outcome={} announcement_key={} projection={} attempt={} elapsed_ms={}",
                        result, claim.key().idempotencyKey(), projection, claim.attemptCount(), elapsed(started).toMillis());
            }
            running.set(false);
        }
    }

    private boolean suppressAttemptedCreate(
            RecordAnnouncementClaim claim, RecordAnnouncementSnapshot snapshot, LeaseHeartbeat heartbeat) {
        String key = RecordAnnouncementRenderer.publicationKey(claim.key().idempotencyKey());
        Set<Long> ids = new HashSet<>(snapshot.messages().stream().map(RecordAnnouncementMessage::messageId).toList());
        if (hasUnclearCreateOutcome(snapshot)) {
            discovery(claim, heartbeat, key, List.of()).values().forEach(ids::addAll);
        }
        for (Long id : ids.stream().sorted().toList()) {
            renew(claim, heartbeat);
            messages.delete(claim.key().channelId(), id);
            ensureLease(heartbeat);
        }
        ensureLease(heartbeat);
        if (!store.replaceMessages(claim.key(), claim.token(), List.of())) return false;
        ensureLease(heartbeat);
        return store.markSuppressed(claim.key(), claim.token(), clock.instant());
    }

    private boolean synchronize(
            RecordAnnouncementClaim claim, RecordAnnouncementSnapshot snapshot, LeaseHeartbeat heartbeat) {
        RecordAnnouncementProjection projection = effectiveProjection(snapshot);
        if (projection == RecordAnnouncementProjection.DELETE) {
            return delete(claim, snapshot, heartbeat);
        }
        if (projection == RecordAnnouncementProjection.NO_OP) {
            return verifyNoOp(claim, snapshot, heartbeat);
        }
        List<RecordEventSnapshot> facts = snapshot.registration().eventIds().stream()
                .map(events::find).flatMap(Optional::stream)
                .filter(event -> event.validity() == RecordEventValidity.VALID).toList();
        if (facts.size() != snapshot.registration().eventIds().size()) {
            throw new IllegalStateException("record announcement contains an absent or invalid event");
        }
        Map<Long, String> displays = new HashMap<>();
        players.findAllPlayers().forEach(player -> displays.put(player.discordUserId(), player.displayName()));
        RenderedRecordAnnouncement rendered = renderer.render(new RecordAnnouncementRenderInput(
                snapshot.registration(), facts, displays));
        Map<Integer, Long> persisted = snapshot.messages().stream()
                .collect(java.util.stream.Collectors.toMap(RecordAnnouncementMessage::position,
                        RecordAnnouncementMessage::messageId, (left, right) -> left));
        Map<Integer, List<Long>> discovered = needsCreateDiscovery(snapshot, rendered, persisted)
                ? discovery(claim, heartbeat, rendered.publicationKey(), rendered.pages())
                : Map.of();

        List<RecordAnnouncementMessage> stable = new ArrayList<>();
        Set<Long> previouslyKnown = new HashSet<>(persisted.values());
        discovered.values().forEach(previouslyKnown::addAll);
        Set<Long> deleted = new HashSet<>();
        for (RenderedRecordAnnouncementPage page : rendered.pages()) {
            renew(claim, heartbeat);
            List<Long> matches = discovered.getOrDefault(page.position(), List.of());
            long winner;
            if (persisted.containsKey(page.position())) {
                winner = persisted.get(page.position());
                if (projection == RecordAnnouncementProjection.EDIT) {
                    try {
                        messages.edit(claim.key().channelId(), winner, page);
                        ensureLease(heartbeat);
                    } catch (RecordAnnouncementMessageGateway.MissingMessageException missing) {
                        if (snapshot.publishedAt().isPresent()) {
                            markExternallyRemoved(claim, heartbeat);
                        }
                        winner = messages.create(claim.key().channelId(), page);
                        ensureLease(heartbeat);
                    }
                }
                removeDuplicates(claim, heartbeat, matches, winner, deleted);
            } else if (!matches.isEmpty()) {
                winner = matches.getFirst();
                removeDuplicates(claim, heartbeat, matches, winner, deleted);
            } else {
                winner = messages.create(claim.key().channelId(), page);
                ensureLease(heartbeat);
            }
            stable.add(new RecordAnnouncementMessage(page.position(), winner));
            ensureLease(heartbeat);
            if (!store.replaceMessages(claim.key(), claim.token(), stable)) return false;
        }
        Set<Long> currentIds = stable.stream().map(RecordAnnouncementMessage::messageId)
                .collect(java.util.stream.Collectors.toSet());
        for (long oldId : previouslyKnown.stream().sorted().toList()) {
            if (!currentIds.contains(oldId) && deleted.add(oldId)) {
                renew(claim, heartbeat);
                messages.delete(claim.key().channelId(), oldId);
                ensureLease(heartbeat);
            }
        }
        ensureLease(heartbeat);
        return projection == RecordAnnouncementProjection.CREATE
                ? store.markDelivered(claim.key(), claim.token(), clock.instant())
                : store.markSynchronized(claim.key(), claim.token(), clock.instant());
    }

    private boolean delete(
            RecordAnnouncementClaim claim, RecordAnnouncementSnapshot snapshot, LeaseHeartbeat heartbeat) {
        String key = RecordAnnouncementRenderer.publicationKey(claim.key().idempotencyKey());
        Set<Long> ids = new HashSet<>(snapshot.messages().stream().map(RecordAnnouncementMessage::messageId).toList());
        if (hasUnclearCreateOutcome(snapshot)) {
            discovery(claim, heartbeat, key, List.of()).values().forEach(ids::addAll);
        }
        for (Long id : ids.stream().sorted().toList()) {
            renew(claim, heartbeat);
            messages.delete(claim.key().channelId(), id);
            ensureLease(heartbeat);
        }
        ensureLease(heartbeat);
        return store.replaceMessages(claim.key(), claim.token(), List.of())
                && store.markSynchronized(claim.key(), claim.token(), clock.instant());
    }

    private Map<Integer, List<Long>> discovery(
            RecordAnnouncementClaim claim, LeaseHeartbeat heartbeat, String publicationKey,
            List<RenderedRecordAnnouncementPage> expectedPages) {
        renew(claim, heartbeat);
        Map<Integer, List<Long>> grouped = new HashMap<>();
        messages.discoverCreatedPages(claim.key().channelId(), publicationKey, expectedPages).forEach(page ->
                grouped.computeIfAbsent(page.position(), ignored -> new ArrayList<>()).add(page.messageId()));
        ensureLease(heartbeat);
        grouped.values().forEach(ids -> ids.sort(Long::compare));
        return grouped;
    }

    private void removeDuplicates(
            RecordAnnouncementClaim claim, LeaseHeartbeat heartbeat, List<Long> ids, long winner, Set<Long> deleted) {
        for (long id : ids) {
            if (id != winner && deleted.add(id)) {
                renew(claim, heartbeat);
                messages.delete(claim.key().channelId(), id);
                ensureLease(heartbeat);
            }
        }
    }

    private boolean verifyStability(
            RecordAnnouncementClaim claim, RecordAnnouncementSnapshot snapshot, LeaseHeartbeat heartbeat) {
        if (snapshot.messages().isEmpty()) {
            throw new IllegalStateException("delivered record announcement has no persisted Discord message IDs");
        }
        for (RecordAnnouncementMessage page : snapshot.messages()) {
            renew(claim, heartbeat);
            if (!messages.exists(claim.key().channelId(), page.messageId())) {
                markExternallyRemoved(claim, heartbeat);
            }
            ensureLease(heartbeat);
        }
        ensureLease(heartbeat);
        return store.markSynchronized(claim.key(), claim.token(), clock.instant());
    }

    private boolean verifyNoOp(
            RecordAnnouncementClaim claim, RecordAnnouncementSnapshot snapshot, LeaseHeartbeat heartbeat) {
        if (snapshot.publishedAt().isEmpty() || snapshot.messages().isEmpty()) {
            ensureLease(heartbeat);
            return store.markSynchronized(claim.key(), claim.token(), clock.instant());
        }
        return verifyStability(claim, snapshot, heartbeat);
    }

    private static boolean isStabilityCheck(RecordAnnouncementSnapshot snapshot) {
        return snapshot.registration().desiredProjection() == RecordAnnouncementProjection.CREATE
                && snapshot.publishedAt().isPresent() && snapshot.deletedAt().isEmpty();
    }

    private static boolean needsCreateDiscovery(
            RecordAnnouncementSnapshot snapshot, RenderedRecordAnnouncement rendered,
            Map<Integer, Long> persisted) {
        return snapshot.attemptCount() > 1 && rendered.pages().stream()
                .anyMatch(page -> !persisted.containsKey(page.position()));
    }

    private static boolean hasUnclearCreateOutcome(RecordAnnouncementSnapshot snapshot) {
        return snapshot.attemptCount() > 1 && snapshot.publishedAt().isEmpty();
    }

    /** An unchanged published projection becomes a single ID-based existence check, not a repeated delivery. */
    private static RecordAnnouncementProjection effectiveProjection(RecordAnnouncementSnapshot snapshot) {
        RecordAnnouncementProjection desired = snapshot.registration().desiredProjection();
        if (desired == RecordAnnouncementProjection.CREATE && snapshot.publishedAt().isPresent()) {
            return RecordAnnouncementProjection.NO_OP;
        }
        if (desired == RecordAnnouncementProjection.EDIT && snapshot.changedAt().isPresent()) {
            return RecordAnnouncementProjection.NO_OP;
        }
        return desired;
    }

    private void markExternallyRemoved(RecordAnnouncementClaim claim, LeaseHeartbeat heartbeat) {
        ensureLease(heartbeat);
        if (store.markExternallyRemoved(claim.key(), claim.token(), clock.instant())) {
            throw new ExternallyRemoved();
        }
        throw new LostLease();
    }

    private Optional<RecordAnnouncementSnapshot> owned(RecordAnnouncementClaim claim) {
        return store.find(claim.key()).filter(snapshot -> snapshot.claimToken().filter(claim.token()::equals).isPresent());
    }

    private void renew(RecordAnnouncementClaim claim, LeaseHeartbeat heartbeat) {
        ensureLease(heartbeat);
        if (!heartbeat.renewNow()) throw new LostLease();
    }

    private void ensureLease(LeaseHeartbeat heartbeat) {
        if (!heartbeat.leaseOwned()) throw new LostLease();
    }

    private boolean leaseOwnedAfterStoppingHeartbeat(LeaseHeartbeat heartbeat) {
        if (heartbeat == null) return true;
        heartbeat.close();
        return heartbeat.leaseOwned();
    }

    private boolean markSuppressed(RecordAnnouncementClaim claim, LeaseHeartbeat heartbeat) {
        ensureLease(heartbeat);
        return store.markSuppressed(claim.key(), claim.token(), clock.instant());
    }

    private boolean markRetryable(
            RecordAnnouncementClaim claim, LeaseHeartbeat heartbeat, RuntimeException failure) {
        ensureLease(heartbeat);
        return store.markRetryableFailure(claim.key(), claim.token(),
                new RecordWorkFailure(RecordWorkFailureCategory.RETRYABLE, safeGatewayError(failure)),
                clock.instant().plus(backoff(claim.attemptCount())));
    }

    private boolean markPermanent(
            RecordAnnouncementClaim claim, LeaseHeartbeat heartbeat, RuntimeException failure) {
        ensureLease(heartbeat);
        return store.markPermanentFailure(claim.key(), claim.token(),
                new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT, safeGatewayError(failure)), clock.instant());
    }

    private static String safeGatewayError(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private Duration backoff(int attempts) {
        Duration value = initialRetryBackoff;
        for (int index = 1; index < attempts && value.compareTo(maxRetryBackoff) < 0; index++) {
            try {
                value = value.multipliedBy(2);
            } catch (ArithmeticException ignored) {
                return maxRetryBackoff;
            }
        }
        return value.compareTo(maxRetryBackoff) > 0 ? maxRetryBackoff : value;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static Duration elapsed(long start) { return Duration.ofNanos(System.nanoTime() - start); }
    private static final class LostLease extends RuntimeException { }
    private static final class ExternallyRemoved extends RuntimeException { }

    private final class LeaseHeartbeat implements AutoCloseable {
        private final RecordAnnouncementClaim claim;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean owned = new AtomicBoolean(true);
        private final AtomicReference<RuntimeException> unexpectedFailure = new AtomicReference<>();
        private final ReentrantLock executionLock = new ReentrantLock();
        private volatile ScheduledFuture<?> future;

        private LeaseHeartbeat(RecordAnnouncementClaim claim) { this.claim = claim; }

        private boolean renewNow() {
            executionLock.lock();
            try {
                if (!owned.get()) return false;
                boolean renewed = renewLease();
                if (!renewed) owned.set(false);
                return renewed;
            } finally {
                executionLock.unlock();
            }
        }

        private void start() {
            future = heartbeatExecutor.scheduleWithFixedDelay(
                    this::heartbeat, heartbeatInterval.toNanos(), heartbeatInterval.toNanos(), TimeUnit.NANOSECONDS);
        }

        private void heartbeat() {
            executionLock.lock();
            try {
                if (closed.get() || !owned.get()) return;
                try {
                    if (!renewLease()) owned.set(false);
                } catch (RuntimeException failure) {
                    unexpectedFailure.compareAndSet(null, failure);
                }
            } finally {
                executionLock.unlock();
            }
        }

        private boolean renewLease() {
            Instant now = clock.instant();
            return store.renewLease(claim.key(), claim.token(), new RecordLeaseClaimRequest(now, now.plus(leaseDuration)));
        }

        private boolean leaseOwned() {
            executionLock.lock();
            try {
                RuntimeException failure = unexpectedFailure.get();
                if (failure != null) throw failure;
                return owned.get();
            } finally {
                executionLock.unlock();
            }
        }

        @Override
        public void close() {
            closed.set(true);
            ScheduledFuture<?> current = future;
            if (current != null) current.cancel(false);
            executionLock.lock();
            try {
                // Acquiring the lock fences an in-flight heartbeat before terminal state is persisted.
            } finally {
                executionLock.unlock();
            }
        }
    }

    public enum RunResult {
        COMPLETED, FAILED_RETRYABLE, FAILED_PERMANENT, LOST_LEASE, EXTERNALLY_REMOVED,
        SUPPRESSED, UNKNOWN, NOT_CLAIMED, SKIPPED_ALREADY_RUNNING
    }
}
