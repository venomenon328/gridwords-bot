package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.achievement.AchievementAnnouncementDeliveryCoordinator;
import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.application.achievement.RenderedAchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementMessageGateway;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL delivery/recovery boundary with only the external Discord message gateway faked. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresAchievementAnnouncementDeliveryRecoveryIT {
    private static final long GUILD_ID = 10L;
    private static final long CHANNEL_ID = 20L;
    private static final long PARTICIPANT_ID = 30L;
    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final AchievementDefinitionCatalog CATALOG = AchievementDefinitionCatalog.achievementsV1();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;

    @BeforeAll
    void migrate() throws Exception {
        source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM achievement_announcement_item");
        jdbc.update("DELETE FROM achievement_announcement");
        jdbc.update("DELETE FROM achievement_event");
        jdbc.update("DELETE FROM achievement_bootstrap_state");
        jdbc.update("DELETE FROM achievement_award_state");
        jdbc.execute("TRUNCATE TABLE player CASCADE");
    }

    @Test
    void acceptedCreateWithLostAckIsDiscoveredAfterRestartAndDuplicateArtifactsAreRemoved() {
        Clock initialClock = Clock.fixed(NOW, BERLIN);
        AchievementAnnouncement.Key key = seedLiveAnnouncement(initialClock, "live:recovery:ack-loss");
        RecordingGateway gateway = new RecordingGateway();
        gateway.loseNextCreateAck = true;

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            assertThat(coordinator(initialClock, gateway, heartbeat).runNext())
                    .isEqualTo(AchievementAnnouncementDeliveryCoordinator.RunResult.FAILED_RETRYABLE);
        }

        assertThat(gateway.createCalls.get()).isOne();
        assertThat(announcementStore(initialClock).find(key).orElseThrow().deliveryState())
                .isEqualTo(AchievementAnnouncement.DeliveryState.RETRYABLE);
        long originalMessageId = gateway.onlyMessageId();
        long duplicateMessageId = gateway.addDuplicateOfOnlyMessage();

        Clock restartClock = Clock.fixed(NOW.plusSeconds(2), BERLIN);
        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            assertThat(coordinator(restartClock, gateway, heartbeat).runNext())
                    .isEqualTo(AchievementAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        }

        AchievementAnnouncement.Snapshot synchronizedAnnouncement = announcementStore(restartClock).find(key).orElseThrow();
        assertThat(synchronizedAnnouncement.deliveryState()).isEqualTo(AchievementAnnouncement.DeliveryState.SYNCHRONIZED);
        assertThat(synchronizedAnnouncement.discordMessageId()).contains(originalMessageId);
        assertThat(gateway.createCalls.get()).isOne();
        assertThat(gateway.discoveryCalls.get()).isOne();
        assertThat(gateway.deletedMessageIds).containsExactly(duplicateMessageId);
        assertThat(gateway.exists(CHANNEL_ID, originalMessageId)).isTrue();
        assertThat(gateway.exists(CHANNEL_ID, duplicateMessageId)).isFalse();
    }

    @Test
    void restartAfterPersistedMessageIdSynchronizesWithoutCreateOrDiscovery() {
        Clock initialClock = Clock.fixed(NOW, BERLIN);
        AchievementAnnouncement.Key key = seedLiveAnnouncement(initialClock, "live:recovery:delivered-before-crash");
        PostgresAchievementAnnouncementStore store = announcementStore(initialClock);
        AchievementWork.LeaseClaim claim = store.claim(
                key, new AchievementWork.LeaseClaimRequest(NOW, NOW.plusSeconds(1))).orElseThrow();
        assertThat(store.markDelivered(key, claim.token(), 9_500L, NOW)).isTrue();

        RecordingGateway gateway = new RecordingGateway();
        gateway.seedExisting(9_500L);
        Clock restartClock = Clock.fixed(NOW.plusSeconds(2), BERLIN);
        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            assertThat(coordinator(restartClock, gateway, heartbeat).runNext())
                    .isEqualTo(AchievementAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        }

        AchievementAnnouncement.Snapshot synchronizedAnnouncement = announcementStore(restartClock).find(key).orElseThrow();
        assertThat(synchronizedAnnouncement.deliveryState()).isEqualTo(AchievementAnnouncement.DeliveryState.SYNCHRONIZED);
        assertThat(synchronizedAnnouncement.discordMessageId()).contains(9_500L);
        assertThat(gateway.createCalls.get()).isZero();
        assertThat(gateway.discoveryCalls.get()).isZero();
    }

    @Test
    void concurrentDeliveryWorkersCreateExactlyOneExternalMessage() throws Exception {
        Clock clock = Clock.fixed(NOW, BERLIN);
        seedLiveAnnouncement(clock, "live:recovery:concurrent-workers");
        RecordingGateway gateway = new RecordingGateway();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ScheduledExecutorService firstHeartbeat = Executors.newSingleThreadScheduledExecutor();
                ScheduledExecutorService secondHeartbeat = Executors.newSingleThreadScheduledExecutor();
                ExecutorService workers = Executors.newFixedThreadPool(2)) {
            AchievementAnnouncementDeliveryCoordinator first = coordinator(clock, gateway, firstHeartbeat);
            AchievementAnnouncementDeliveryCoordinator second = coordinator(clock, gateway, secondHeartbeat);

            var firstResult = workers.submit(() -> {
                ready.countDown();
                start.await();
                return first.runNext();
            });
            var secondResult = workers.submit(() -> {
                ready.countDown();
                start.await();
                return second.runNext();
            });

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            AchievementAnnouncementDeliveryCoordinator.RunResult.COMPLETED,
                            AchievementAnnouncementDeliveryCoordinator.RunResult.NOT_CLAIMED);
        } finally {
            start.countDown();
        }

        assertThat(gateway.createCalls.get()).isOne();
        assertThat(gateway.messageCount()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM achievement_announcement WHERE delivery_state='SYNCHRONIZED'",
                Integer.class)).isOne();
    }

    private AchievementAnnouncement.Key seedLiveAnnouncement(Clock clock, String idempotencyKey) {
        jdbc.update("""
                INSERT INTO player (discord_user_id,display_name,active,administrator,reminder_opt_in,created_at,updated_at)
                VALUES (?,?,TRUE,FALSE,TRUE,?,?)
                """, PARTICIPANT_ID, "Achievement Player", Timestamp.from(NOW), Timestamp.from(NOW));

        AchievementKey achievementKey = new AchievementKey("participation.1.gridwords");
        AchievementAwardState.Key awardKey = new AchievementAwardState.Key(GUILD_ID, PARTICIPANT_ID, achievementKey);
        PostgresAchievementAwardStateStore awards = new PostgresAchievementAwardStateStore(jdbc, clock);
        assertThat(awards.initialize(awardKey, new AchievementAwardState.Write(
                CATALOG.version(), AchievementAwardState.Status.ACTIVE, LocalDate.of(2026, 8, 8), NOW,
                AchievementEvidence.Kind.GAME_RESULT, "result:1", Optional.empty())).status())
                .isEqualTo(AchievementAwardState.InitializationStatus.CREATED);

        UUID eventId = UUID.randomUUID();
        PostgresAchievementEventStore events = new PostgresAchievementEventStore(jdbc, clock);
        assertThat(events.append(new AchievementEventFact.Draft(
                eventId, "event:" + idempotencyKey, awardKey, CATALOG.version(), AchievementEventFact.Type.UNLOCKED,
                LocalDate.of(2026, 8, 8), AchievementEvidence.Kind.GAME_RESULT, "result:1",
                AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION, NOW)).appended()).isTrue();

        PostgresAchievementAnnouncementStore announcements = announcementStore(clock);
        AchievementAnnouncement.Registration registration = new AchievementAnnouncement.Registration(
                GUILD_ID, CHANNEL_ID, PARTICIPANT_ID, CATALOG.version(),
                AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH, idempotencyKey,
                "achievement-renderer-v1", "0".repeat(64));
        announcements.register(registration);
        assertThat(announcements.replaceItems(registration.key(), List.of(eventId))).isTrue();
        markBootstrapSucceeded(clock);
        return registration.key();
    }

    private void markBootstrapSucceeded(Clock clock) {
        PostgresAchievementBootstrapStore bootstraps = new PostgresAchievementBootstrapStore(jdbc, clock);
        AchievementWork.BootstrapKey key = new AchievementWork.BootstrapKey(GUILD_ID, CATALOG.version());
        bootstraps.register(key);
        AchievementWork.LeaseClaim claim = bootstraps.claim(
                key, new AchievementWork.LeaseClaimRequest(NOW, NOW.plusSeconds(60))).orElseThrow();
        assertThat(bootstraps.markSucceeded(key, claim.token(), NOW)).isTrue();
    }

    private PostgresAchievementAnnouncementStore announcementStore(Clock clock) {
        return new PostgresAchievementAnnouncementStore(jdbc, clock);
    }

    private AchievementAnnouncementDeliveryCoordinator coordinator(
            Clock clock, RecordingGateway gateway, ScheduledExecutorService heartbeat) {
        return new AchievementAnnouncementDeliveryCoordinator(
                announcementStore(clock),
                new PostgresAchievementEventStore(jdbc, clock),
                new PostgresAchievementAwardStateStore(jdbc, clock),
                new PostgresPersistenceAdapter(jdbc, clock, BERLIN),
                gateway,
                CATALOG,
                AchievementEmojiResolver.unicodeOnly(),
                clock,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                Duration.ofMinutes(1),
                heartbeat);
    }

    private static final class RecordingGateway implements AchievementAnnouncementMessageGateway {
        private final AtomicInteger createCalls = new AtomicInteger();
        private final AtomicInteger discoveryCalls = new AtomicInteger();
        private final AtomicInteger nextId = new AtomicInteger(9_000);
        private final Map<Long, RenderedAchievementAnnouncement> messages = new HashMap<>();
        private final List<Long> deletedMessageIds = new ArrayList<>();
        private volatile boolean loseNextCreateAck;

        @Override
        public synchronized long create(long channelId, RenderedAchievementAnnouncement announcement) {
            assertThat(channelId).isEqualTo(CHANNEL_ID);
            createCalls.incrementAndGet();
            long id = nextId.getAndIncrement();
            messages.put(id, announcement);
            if (loseNextCreateAck) {
                loseNextCreateAck = false;
                throw new RetryableMessageException("simulated lost Discord acknowledgement", null);
            }
            return id;
        }

        @Override
        public synchronized boolean exists(long channelId, long messageId) {
            assertThat(channelId).isEqualTo(CHANNEL_ID);
            return messages.containsKey(messageId);
        }

        @Override
        public synchronized List<Long> discoverCreatedMessages(
                long channelId, String publicationKey, RenderedAchievementAnnouncement expected) {
            assertThat(channelId).isEqualTo(CHANNEL_ID);
            discoveryCalls.incrementAndGet();
            return messages.entrySet().stream()
                    .filter(entry -> entry.getValue().publicationKey().equals(publicationKey))
                    .filter(entry -> entry.getValue().contentFingerprint().equals(expected.contentFingerprint()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
        }

        @Override
        public synchronized void delete(long channelId, long messageId) {
            assertThat(channelId).isEqualTo(CHANNEL_ID);
            deletedMessageIds.add(messageId);
            messages.remove(messageId);
        }

        synchronized long onlyMessageId() {
            assertThat(messages).hasSize(1);
            return messages.keySet().iterator().next();
        }

        synchronized long addDuplicateOfOnlyMessage() {
            RenderedAchievementAnnouncement announcement = messages.get(onlyMessageId());
            long id = nextId.getAndIncrement();
            messages.put(id, announcement);
            return id;
        }

        synchronized void seedExisting(long messageId) {
            messages.put(messageId, new RenderedAchievementAnnouncement(
                    "seeded-existing-message", "f".repeat(64),
                    List.of(new RenderedAchievementAnnouncement.Embed("seed", "seed"))));
        }

        synchronized int messageCount() {
            return messages.size();
        }
    }
}
