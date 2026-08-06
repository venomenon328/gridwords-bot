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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Claims and synchronizes one persisted announcement at a time. Every database write is token-fenced and every
 * Discord call occurs after the claiming transaction has ended. Unknown technical failures escape unchanged.
 */
public final class RecordAnnouncementDeliveryCoordinator {
    private static final Logger log = LoggerFactory.getLogger(RecordAnnouncementDeliveryCoordinator.class);
    private static final String RETRYABLE_FAILURE = "record announcement Discord delivery is retryable";
    private static final String PERMANENT_FAILURE = "record announcement Discord delivery permanently failed";
    private final RecordAnnouncementStore store;
    private final RecordEventStore events;
    private final PlayerStore players;
    private final RecordAnnouncementMessageGateway messages;
    private final RecordAnnouncementRenderer renderer;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration initialRetryBackoff;
    private final Duration maxRetryBackoff;
    private final boolean publicAnnouncementsEnabled;
    private final RecordAnnouncementDeliveryMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean();

    public RecordAnnouncementDeliveryCoordinator(
            RecordAnnouncementStore store, RecordEventStore events, PlayerStore players,
            RecordAnnouncementMessageGateway messages, RecordAnnouncementRenderer renderer, Clock clock,
            Duration leaseDuration, Duration initialRetryBackoff, Duration maxRetryBackoff,
            boolean publicAnnouncementsEnabled, RecordAnnouncementDeliveryMetrics metrics) {
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.events = java.util.Objects.requireNonNull(events, "events");
        this.players = java.util.Objects.requireNonNull(players, "players");
        this.messages = java.util.Objects.requireNonNull(messages, "messages");
        this.renderer = java.util.Objects.requireNonNull(renderer, "renderer");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.initialRetryBackoff = positive(initialRetryBackoff, "initialRetryBackoff");
        this.maxRetryBackoff = positive(maxRetryBackoff, "maxRetryBackoff");
        if (initialRetryBackoff.compareTo(maxRetryBackoff) > 0) {
            throw new IllegalArgumentException("initialRetryBackoff must not exceed maxRetryBackoff");
        }
        this.publicAnnouncementsEnabled = publicAnnouncementsEnabled;
        this.metrics = java.util.Objects.requireNonNull(metrics, "metrics");
    }

    public RunResult runNext() {
        if (!running.compareAndSet(false, true)) return RunResult.SKIPPED_ALREADY_RUNNING;
        long started = System.nanoTime();
        RecordAnnouncementClaim claim = null;
        RunResult result = null;
        try {
            if (!publicAnnouncementsEnabled) store.suppressPendingCreates(clock.instant());
            Instant now = clock.instant();
            claim = store.claimNext(new RecordLeaseClaimRequest(now, now.plus(leaseDuration)), publicAnnouncementsEnabled)
                    .orElse(null);
            if (claim == null) return RunResult.NOT_CLAIMED;
            RecordAnnouncementSnapshot snapshot = owned(claim).orElse(null);
            if (snapshot == null) return result = RunResult.LOST_LEASE;
            if (!publicAnnouncementsEnabled && snapshot.publishedAt().isEmpty()
                    && snapshot.registration().desiredProjection() == RecordAnnouncementProjection.CREATE) {
                result = store.markSuppressed(claim.key(), claim.token(), clock.instant())
                        ? RunResult.SUPPRESSED : RunResult.LOST_LEASE;
                return result;
            }
            result = synchronize(claim, snapshot) ? RunResult.COMPLETED : RunResult.LOST_LEASE;
            return result;
        } catch (LostLease lostLease) {
            result = RunResult.LOST_LEASE;
            return result;
        } catch (RecordAnnouncementMessageGateway.PermanentMessageException failure) {
            if (claim == null) throw failure;
            result = markPermanent(claim) ? RunResult.FAILED_PERMANENT : RunResult.LOST_LEASE;
            return result;
        } catch (RecordAnnouncementMessageGateway.MessageGatewayException failure) {
            if (claim == null) throw failure;
            result = markRetryable(claim) ? RunResult.FAILED_RETRYABLE : RunResult.LOST_LEASE;
            return result;
        } catch (RuntimeException unknown) {
            if (claim != null) log.warn("record announcement delivery unknown failure key={}", claim.key().idempotencyKey(), unknown);
            result = claim == null ? null : RunResult.UNKNOWN;
            throw unknown;
        } finally {
            if (claim != null && result != null) metrics.record(result, elapsed(started));
            running.set(false);
        }
    }

    private boolean synchronize(RecordAnnouncementClaim claim, RecordAnnouncementSnapshot snapshot) {
        if (snapshot.registration().desiredProjection() == RecordAnnouncementProjection.DELETE) {
            return delete(claim, snapshot);
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
        Map<Integer, List<Long>> discovered = discovery(claim, rendered.publicationKey());
        if (snapshot.publishedAt().isPresent() && !snapshot.messages().isEmpty() && discovered.isEmpty()) {
            return store.markExternallyRemoved(claim.key(), claim.token(), clock.instant());
        }
        List<RecordAnnouncementMessage> stable = new ArrayList<>();
        Map<Integer, Long> persisted = snapshot.messages().stream()
                .collect(java.util.stream.Collectors.toMap(RecordAnnouncementMessage::position,
                        RecordAnnouncementMessage::messageId, (left, right) -> left));
        for (RenderedRecordAnnouncementPage page : rendered.pages()) {
            if (!renew(claim)) return false;
            List<Long> matches = discovered.getOrDefault(page.position(), List.of());
            long winner;
            if (!matches.isEmpty()) {
                winner = matches.getFirst();
                removeDuplicates(claim, matches, winner);
            } else if (persisted.containsKey(page.position())) {
                winner = persisted.get(page.position());
                if (snapshot.registration().desiredProjection() != RecordAnnouncementProjection.NO_OP) {
                    try {
                        messages.edit(claim.key().channelId(), winner, page);
                    } catch (RecordAnnouncementMessageGateway.MissingMessageException missing) {
                        if (snapshot.publishedAt().isPresent()) {
                            return store.markExternallyRemoved(claim.key(), claim.token(), clock.instant());
                        }
                        winner = messages.create(claim.key().channelId(), page);
                    }
                }
            } else {
                winner = messages.create(claim.key().channelId(), page);
            }
            stable.add(new RecordAnnouncementMessage(page.position(), winner));
            if (!store.replaceMessages(claim.key(), claim.token(), stable)) return false;
        }
        for (RecordAnnouncementMessage old : snapshot.messages()) {
            if (stable.stream().noneMatch(current -> current.messageId() == old.messageId())) {
                if (!renew(claim)) return false;
                messages.delete(claim.key().channelId(), old.messageId());
            }
        }
        return store.markSynchronized(claim.key(), claim.token(), clock.instant());
    }

    private boolean delete(RecordAnnouncementClaim claim, RecordAnnouncementSnapshot snapshot) {
        String key = RecordAnnouncementRenderer.publicationKey(claim.key().idempotencyKey());
        List<Long> ids = new ArrayList<>(snapshot.messages().stream().map(RecordAnnouncementMessage::messageId).toList());
        discovery(claim, key).values().forEach(ids::addAll);
        for (Long id : ids.stream().distinct().sorted().toList()) {
            if (!renew(claim)) return false;
            messages.delete(claim.key().channelId(), id);
        }
        return store.replaceMessages(claim.key(), claim.token(), List.of())
                && store.markSynchronized(claim.key(), claim.token(), clock.instant());
    }

    private Map<Integer, List<Long>> discovery(RecordAnnouncementClaim claim, String publicationKey) {
        if (!renew(claim)) throw new LostLease();
        Map<Integer, List<Long>> grouped = new HashMap<>();
        messages.findByPublicationKey(claim.key().channelId(), publicationKey).forEach(page ->
                grouped.computeIfAbsent(page.position(), ignored -> new ArrayList<>()).add(page.messageId()));
        grouped.values().forEach(ids -> ids.sort(Long::compare));
        return grouped;
    }

    private void removeDuplicates(RecordAnnouncementClaim claim, List<Long> ids, long winner) {
        for (long id : ids) {
            if (id != winner) {
                if (!renew(claim)) throw new LostLease();
                messages.delete(claim.key().channelId(), id);
            }
        }
    }

    private Optional<RecordAnnouncementSnapshot> owned(RecordAnnouncementClaim claim) {
        return store.find(claim.key()).filter(snapshot -> snapshot.claimToken().filter(claim.token()::equals).isPresent());
    }

    private boolean renew(RecordAnnouncementClaim claim) {
        Instant now = clock.instant();
        return store.renewLease(claim.key(), claim.token(), new RecordLeaseClaimRequest(now, now.plus(leaseDuration)));
    }

    private boolean markRetryable(RecordAnnouncementClaim claim) {
        return store.markRetryableFailure(claim.key(), claim.token(),
                new RecordWorkFailure(RecordWorkFailureCategory.RETRYABLE, RETRYABLE_FAILURE),
                clock.instant().plus(backoff(claim.attemptCount())));
    }

    private boolean markPermanent(RecordAnnouncementClaim claim) {
        return store.markPermanentFailure(claim.key(), claim.token(),
                new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT, PERMANENT_FAILURE), clock.instant());
    }

    private Duration backoff(int attempts) {
        Duration value = initialRetryBackoff;
        for (int index = 1; index < attempts && value.compareTo(maxRetryBackoff) < 0; index++) {
            try { value = value.multipliedBy(2); } catch (ArithmeticException ignored) { return maxRetryBackoff; }
        }
        return value.compareTo(maxRetryBackoff) > 0 ? maxRetryBackoff : value;
    }

    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static Duration elapsed(long start) { return Duration.ofNanos(System.nanoTime() - start); }
    private static final class LostLease extends RuntimeException { }

    public enum RunResult { COMPLETED, FAILED_RETRYABLE, FAILED_PERMANENT, LOST_LEASE, SUPPRESSED, UNKNOWN, NOT_CLAIMED, SKIPPED_ALREADY_RUNNING }
}
