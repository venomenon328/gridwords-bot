package de.venomenon.gridwordsbot.application.achievement;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementMessageGateway;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/** Token-fenced single-message delivery. Discord work is deliberately outside persistence transactions. */
public final class AchievementAnnouncementDeliveryCoordinator {
    private final AchievementAnnouncementStore announcements;
    private final AchievementEventStore events;
    private final AchievementAwardStateStore awards;
    private final PlayerStore players;
    private final AchievementAnnouncementMessageGateway messages;
    private final AchievementAnnouncementRenderer renderer;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final Duration initialRetryBackoff;
    private final Duration maxRetryBackoff;
    private final ScheduledExecutorService heartbeatExecutor;
    private final AtomicBoolean running = new AtomicBoolean();

    public AchievementAnnouncementDeliveryCoordinator(
            AchievementAnnouncementStore announcements, AchievementEventStore events, AchievementAwardStateStore awards,
            PlayerStore players, AchievementAnnouncementMessageGateway messages, AchievementDefinitionCatalog catalog,
            AchievementEmojiResolver emojis, Clock clock, Duration leaseDuration, Duration heartbeatInterval,
            Duration initialRetryBackoff, Duration maxRetryBackoff, ScheduledExecutorService heartbeatExecutor) {
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.events = Objects.requireNonNull(events, "events");
        this.awards = Objects.requireNonNull(awards, "awards");
        this.players = Objects.requireNonNull(players, "players");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.renderer = new AchievementAnnouncementRenderer(Objects.requireNonNull(catalog, "catalog"), Objects.requireNonNull(emojis, "emojis"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.heartbeatInterval = positive(heartbeatInterval, "heartbeatInterval");
        this.initialRetryBackoff = positive(initialRetryBackoff, "initialRetryBackoff");
        this.maxRetryBackoff = positive(maxRetryBackoff, "maxRetryBackoff");
        if (heartbeatInterval.compareTo(leaseDuration) >= 0 || initialRetryBackoff.compareTo(maxRetryBackoff) > 0) {
            throw new IllegalArgumentException("invalid Achievement delivery timing");
        }
        this.heartbeatExecutor = Objects.requireNonNull(heartbeatExecutor, "heartbeatExecutor");
    }

    public RunResult runNext() {
        if (!running.compareAndSet(false, true)) return RunResult.SKIPPED_ALREADY_RUNNING;
        AchievementAnnouncement.Snapshot claimed = null;
        LeaseHeartbeat heartbeat = null;
        try {
            Instant now = clock.instant();
            claimed = announcements.claimNext(new AchievementWork.LeaseClaimRequest(now, now.plus(leaseDuration))).orElse(null);
            if (claimed == null) return RunResult.NOT_CLAIMED;
            UUID token = claimed.claimToken().orElseThrow(() -> new IllegalStateException("claimed announcement lacks token"));
            heartbeat = new LeaseHeartbeat(claimed.registration().key(), token);
            if (!heartbeat.renewNow()) return RunResult.LOST_LEASE;
            heartbeat.start();
            return deliver(claimed, token, heartbeat);
        } catch (LostLease ignored) {
            return RunResult.LOST_LEASE;
        } catch (AchievementAnnouncementMessageGateway.PermanentMessageException failure) {
            if (claimed == null || !owned(heartbeat)) throw failure;
            return announcements.markPermanentFailure(claimed.registration().key(), claimed.claimToken().orElseThrow(),
                    new AchievementWork.Failure(AchievementWork.FailureCategory.PERMANENT, safe(failure)), clock.instant())
                    ? RunResult.FAILED_PERMANENT : RunResult.LOST_LEASE;
        } catch (AchievementAnnouncementMessageGateway.MessageGatewayException failure) {
            if (claimed == null || !owned(heartbeat)) throw failure;
            return announcements.markRetryableFailure(claimed.registration().key(), claimed.claimToken().orElseThrow(),
                    new AchievementWork.Failure(AchievementWork.FailureCategory.RETRYABLE, safe(failure)),
                    clock.instant().plus(backoff(claimed.attemptCount()))) ? RunResult.FAILED_RETRYABLE : RunResult.LOST_LEASE;
        } finally {
            if (heartbeat != null) heartbeat.close();
            running.set(false);
        }
    }

    private RunResult deliver(AchievementAnnouncement.Snapshot announcement, UUID token, LeaseHeartbeat heartbeat) {
        AchievementAnnouncement.Key key = announcement.registration().key();
        List<AchievementEventFact.Snapshot> active = activeFacts(announcement);
        if (active.isEmpty() && announcement.registration().type() == AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH) {
            ensure(heartbeat);
            if (!announcements.replaceClaimedItems(key, token, List.of())) return RunResult.LOST_LEASE;
            ensure(heartbeat);
            return announcements.markSuppressed(key, token, clock.instant()) ? RunResult.SUPPRESSED : RunResult.LOST_LEASE;
        }
        RenderedAchievementAnnouncement rendered = renderer.render(new AchievementAnnouncementRenderInput(
                announcement, active, players.findByDiscordUserId(announcement.registration().participantId())
                        .map(PlayerStore.StoredPlayer::displayName).orElse("Ehemaliger Spieler")));
        List<UUID> ids = active.stream().map(event -> event.fact().eventId()).toList();
        ensure(heartbeat);
        if (!announcements.replaceClaimedItems(key, token, ids)
                || !announcements.updateClaimedContent(key, token, AchievementAnnouncementRenderer.VERSION, rendered.contentFingerprint())) {
            return RunResult.LOST_LEASE;
        }
        ensure(heartbeat);
        if (announcement.discordMessageId().isPresent()) {
            if (!messages.exists(announcement.registration().channelId(), announcement.discordMessageId().orElseThrow())) {
                return announcements.markExternallyRemoved(key, token, clock.instant())
                        ? RunResult.EXTERNALLY_REMOVED : RunResult.LOST_LEASE;
            }
            return announcements.markSynchronized(key, token, clock.instant()) ? RunResult.COMPLETED : RunResult.LOST_LEASE;
        }
        long messageId = discoverOrCreate(announcement, rendered, heartbeat);
        ensure(heartbeat);
        if (!announcements.markDelivered(key, token, messageId, clock.instant())) return RunResult.LOST_LEASE;
        ensure(heartbeat);
        return announcements.markSynchronized(key, token, clock.instant()) ? RunResult.COMPLETED : RunResult.LOST_LEASE;
    }

    private long discoverOrCreate(
            AchievementAnnouncement.Snapshot announcement, RenderedAchievementAnnouncement rendered, LeaseHeartbeat heartbeat) {
        List<Long> found = announcement.attemptCount() > 1
                ? messages.discoverCreatedMessages(announcement.registration().channelId(), rendered.publicationKey(), rendered) : List.of();
        ensure(heartbeat);
        if (found.isEmpty()) {
            long created = messages.create(announcement.registration().channelId(), rendered);
            ensure(heartbeat);
            return created;
        }
        long winner = found.stream().min(Comparator.naturalOrder()).orElseThrow();
        for (long duplicate : found) {
            if (duplicate != winner) {
                messages.delete(announcement.registration().channelId(), duplicate);
                ensure(heartbeat);
            }
        }
        return winner;
    }

    private List<AchievementEventFact.Snapshot> activeFacts(AchievementAnnouncement.Snapshot announcement) {
        Map<AchievementKey, AchievementAwardState.Snapshot> currentStates = new HashMap<>();
        for (AchievementAwardState.Snapshot state : awards.findAll(
                announcement.registration().guildId(), announcement.registration().participantId())) {
            if (currentStates.put(state.key().achievementKey(), state) != null) {
                throw new IllegalStateException("duplicate achievement award state during delivery revalidation");
            }
        }
        return announcements.findItems(announcement.registration().key()).stream()
                .map(AchievementAnnouncement.Item::eventId)
                .map(eventId -> events.find(eventId).orElseThrow(
                        () -> new IllegalStateException("achievement announcement references missing event: " + eventId)))
                .filter(event -> {
                    AchievementAwardState.Snapshot state = currentStates.get(event.fact().awardKey().achievementKey());
                    if (state == null) {
                        throw new IllegalStateException("achievement event has no award state: "
                                + event.fact().awardKey().achievementKey().value());
                    }
                    return state.write().status() == AchievementAwardState.Status.ACTIVE;
                })
                .toList();
    }

    private void ensure(LeaseHeartbeat heartbeat) { if (!heartbeat.owned()) throw new LostLease(); }
    private boolean owned(LeaseHeartbeat heartbeat) { return heartbeat != null && heartbeat.owned(); }
    private Duration backoff(int attempts) {
        Duration value = initialRetryBackoff;
        for (int index = 1; index < attempts && value.compareTo(maxRetryBackoff) < 0; index++) value = value.multipliedBy(2);
        return value.compareTo(maxRetryBackoff) > 0 ? maxRetryBackoff : value;
    }
    private static String safe(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message.substring(0, Math.min(512, message.length()));
    }
    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
    private static final class LostLease extends RuntimeException { }

    private final class LeaseHeartbeat implements AutoCloseable {
        private final AchievementAnnouncement.Key key;
        private final UUID token;
        private final AtomicBoolean owned = new AtomicBoolean(true);
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ReentrantLock lock = new ReentrantLock();
        private volatile ScheduledFuture<?> future;
        private LeaseHeartbeat(AchievementAnnouncement.Key key, UUID token) { this.key = key; this.token = token; }
        boolean renewNow() {
            lock.lock();
            try {
                if (!owned.get()) return false;
                Instant now = clock.instant();
                boolean renewed = announcements.renewLease(key, token, new AchievementWork.LeaseClaimRequest(now, now.plus(leaseDuration)));
                if (!renewed) owned.set(false);
                return renewed;
            } finally { lock.unlock(); }
        }
        void start() { future = heartbeatExecutor.scheduleWithFixedDelay(this::heartbeat,
                heartbeatInterval.toNanos(), heartbeatInterval.toNanos(), TimeUnit.NANOSECONDS); }
        private void heartbeat() { if (!closed.get()) renewNow(); }
        boolean owned() { return owned.get(); }
        @Override public void close() {
            closed.set(true);
            if (future != null) future.cancel(false);
            lock.lock(); try { /* fence an in-flight renewal */ } finally { lock.unlock(); }
        }
    }

    public enum RunResult {
        COMPLETED, SUPPRESSED, EXTERNALLY_REMOVED, FAILED_RETRYABLE, FAILED_PERMANENT,
        LOST_LEASE, NOT_CLAIMED, SKIPPED_ALREADY_RUNNING
    }
}
