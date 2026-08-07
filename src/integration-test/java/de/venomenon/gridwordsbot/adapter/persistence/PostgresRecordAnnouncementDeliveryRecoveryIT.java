package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.application.record.RecordAnnouncementDeliveryCoordinator;
import de.venomenon.gridwordsbot.application.record.RecordAnnouncementRenderer;
import de.venomenon.gridwordsbot.application.record.RenderedRecordAnnouncementPage;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementClaim;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementMessage;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementPhase;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSubject;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementMessageGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresRecordAnnouncementDeliveryRecoveryIT {
    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private PostgresRecordEventStore events;
    private final List<ScheduledExecutorService> heartbeatExecutors = new ArrayList<>();

    @BeforeAll
    void migrate() throws Exception {
        source = dataSource();
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
        events = new PostgresRecordEventStore(jdbc, fixedClock(NOW));
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM record_announcement_message");
        jdbc.update("DELETE FROM record_announcement_event");
        jdbc.update("DELETE FROM record_announcement");
        jdbc.update("DELETE FROM record_event");
    }

    @AfterEach
    void stopHeartbeats() {
        heartbeatExecutors.forEach(ScheduledExecutorService::shutdownNow);
        heartbeatExecutors.clear();
    }

    @Test
    void concurrentClaimNextWorkersCannotOwnTheSameAnnouncement() throws Exception {
        UUID eventId = appendEvent("claim-next-race");
        RecordAnnouncementKey key = new RecordAnnouncementKey(1, 2, "result:claim-next-race:live");
        new PostgresRecordAnnouncementStore(jdbc, fixedClock(NOW)).registerOrUpdate(registration(key, eventId));

        JdbcTemplate firstJdbc = isolatedJdbc();
        JdbcTemplate secondJdbc = isolatedJdbc();
        PostgresRecordAnnouncementStore firstStore = new PostgresRecordAnnouncementStore(firstJdbc, fixedClock(NOW));
        PostgresRecordAnnouncementStore secondStore = new PostgresRecordAnnouncementStore(secondJdbc, fixedClock(NOW));
        TransactionTemplate firstTx = transactions(firstJdbc);
        TransactionTemplate secondTx = transactions(secondJdbc);
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Optional<RecordAnnouncementClaim>> first = pool.submit(() -> firstTx.execute(status -> {
                Optional<RecordAnnouncementClaim> claim = firstStore.claimNext(
                        request(NOW, NOW.plusSeconds(60)), true);
                assertThat(claim).isPresent();
                firstClaimed.countDown();
                await(releaseFirst);
                return claim;
            }));

            assertThat(firstClaimed.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                Optional<RecordAnnouncementClaim> second = secondTx.execute(status -> secondStore.claimNext(
                        request(NOW, NOW.plusSeconds(60)), true));
                assertThat(second).isEmpty();
            } finally {
                releaseFirst.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isPresent();
        }

        assertThat(new PostgresRecordAnnouncementStore(jdbc, fixedClock(NOW)).find(key).orElseThrow().state())
                .isEqualTo(RecordWorkState.CLAIMED);
    }

    @Test
    void disabledRestartRemovesPersistedAndUnknownCreatePagesBeforeTerminalSuppression() {
        UUID eventId = appendEvent("disable-after-partial-create");
        RecordAnnouncementKey key = new RecordAnnouncementKey(1, 2, "result:disable-after-partial-create:live");
        PostgresRecordAnnouncementStore activeStore = new PostgresRecordAnnouncementStore(jdbc, fixedClock(NOW), true);
        activeStore.registerOrUpdate(registration(key, eventId));
        RecordLeaseClaim original = activeStore.claim(key, request(NOW, NOW.plusSeconds(10))).orElseThrow();
        assertThat(Boolean.TRUE.equals(new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).execute(
                status -> activeStore.replaceMessages(
                        key, original.token(), List.of(new RecordAnnouncementMessage(0, 100)))))).isTrue();

        Instant restartedAt = NOW.plusSeconds(11);
        JdbcTemplate restartedJdbc = isolatedJdbc();
        PostgresRecordAnnouncementStore disabledStore =
                new PostgresRecordAnnouncementStore(restartedJdbc, fixedClock(restartedAt), false);
        Gateway gateway = new Gateway(List.of(
                new RecordAnnouncementMessageGateway.PublishedPage(100, 0),
                new RecordAnnouncementMessageGateway.PublishedPage(200, 1)));
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutors.add(heartbeat);
        PlayerStore unusedPlayers = new PlayerStore() {
            @Override public StoredPlayer upsert(PlayerUpsert request) { throw new UnsupportedOperationException(); }
            @Override public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) { return Optional.empty(); }
        };
        RecordAnnouncementDeliveryCoordinator coordinator = new RecordAnnouncementDeliveryCoordinator(
                disabledStore,
                new PostgresRecordEventStore(restartedJdbc, fixedClock(restartedAt)),
                unusedPlayers,
                gateway,
                new RecordAnnouncementRenderer(),
                fixedClock(restartedAt),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                Duration.ofSeconds(10),
                heartbeat,
                false,
                (result, duration) -> { });

        assertThat(coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.SUPPRESSED);
        assertThat(gateway.deleted).containsExactly(100L, 200L);

        var suppressed = disabledStore.find(key).orElseThrow();
        assertThat(suppressed.state()).isEqualTo(RecordWorkState.SUPPRESSED);
        assertThat(suppressed.messages()).isEmpty();
        assertThat(suppressed.claimToken()).isEmpty();

        PostgresRecordAnnouncementStore reenabled =
                new PostgresRecordAnnouncementStore(isolatedJdbc(), fixedClock(restartedAt.plusSeconds(1)), true);
        assertThat(reenabled.claimNext(
                request(restartedAt.plusSeconds(1), restartedAt.plusSeconds(61)), true)).isEmpty();
        assertThat(reenabled.renewLease(
                key, original.token(), request(restartedAt.plusSeconds(1), restartedAt.plusSeconds(61)))).isFalse();
    }

    @Test
    void restartReconcilesAPageCreatedBeforeTheDeliveryAcknowledgementWithoutDuplicatingIt() {
        UUID eventId = appendEvent("create-before-ack");
        RecordAnnouncementKey key = new RecordAnnouncementKey(1, 2, "result:create-before-ack:live");
        PostgresRecordAnnouncementStore firstStore = new PostgresRecordAnnouncementStore(jdbc, fixedClock(NOW));
        firstStore.registerOrUpdate(registration(key, eventId));

        IllegalStateException lostAcknowledgement = new IllegalStateException("Discord acknowledgement was lost");
        CreateThenCrashGateway gateway = new CreateThenCrashGateway(lostAcknowledgement);
        RecordAnnouncementDeliveryCoordinator firstProcess = coordinator(firstStore, jdbc, gateway, NOW, true);

        assertThatThrownBy(firstProcess::runNext).isSameAs(lostAcknowledgement);
        assertThat(firstStore.find(key).orElseThrow()).satisfies(snapshot -> {
            assertThat(snapshot.state()).isEqualTo(RecordWorkState.CLAIMED);
            assertThat(snapshot.messages()).isEmpty();
            assertThat(snapshot.attemptCount()).isEqualTo(1);
        });

        Instant restartedAt = NOW.plusSeconds(31);
        JdbcTemplate restartedJdbc = isolatedJdbc();
        PostgresRecordAnnouncementStore restartedStore =
                new PostgresRecordAnnouncementStore(restartedJdbc, fixedClock(restartedAt));
        RecordAnnouncementDeliveryCoordinator restartedProcess =
                coordinator(restartedStore, restartedJdbc, gateway, restartedAt, true);

        assertThat(restartedProcess.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        assertThat(restartedProcess.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        assertThat(gateway.createCalls).isEqualTo(1);
        assertThat(restartedStore.find(key).orElseThrow()).satisfies(snapshot -> {
            assertThat(snapshot.state()).isEqualTo(RecordWorkState.SYNCHRONIZED);
            assertThat(snapshot.messages()).containsExactly(new RecordAnnouncementMessage(0, 300));
            assertThat(snapshot.attemptCount()).isEqualTo(2);
            assertThat(snapshot.publishedAt()).contains(restartedAt);
        });
    }

    @Test
    void createStabilityEditPartialReductionAndDeleteUsePersistedMessageIds() {
        RecordAnnouncementKey key = new RecordAnnouncementKey(1, 2, "result:id-first-lifecycle:live");
        List<UUID> initialEvents = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            initialEvents.add(appendEvent("id-first-lifecycle-" + index));
        }
        PostgresRecordAnnouncementStore store = new PostgresRecordAnnouncementStore(jdbc, fixedClock(NOW));
        LifecycleGateway gateway = new LifecycleGateway();
        store.registerOrUpdate(registration(
                key, initialEvents, RecordAnnouncementProjection.CREATE, "a"));
        RecordAnnouncementDeliveryCoordinator coordinator = coordinator(store, jdbc, gateway, NOW, true);

        assertThat(coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        var delivered = store.find(key).orElseThrow();
        assertThat(delivered.state()).isEqualTo(RecordWorkState.DELIVERED);
        assertThat(delivered.attemptCount()).isEqualTo(1);
        assertThat(delivered.messages()).hasSizeGreaterThan(1);
        List<Long> originalMessageIds = delivered.messages().stream()
                .map(RecordAnnouncementMessage::messageId).toList();

        assertThat(coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        var stable = store.find(key).orElseThrow();
        assertThat(stable.state()).isEqualTo(RecordWorkState.SYNCHRONIZED);
        assertThat(stable.attemptCount()).isEqualTo(1);
        assertThat(gateway.existenceChecks).containsExactlyElementsOf(originalMessageIds);
        assertThat(gateway.discoveryCalls).isZero();
        assertThat(coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.NOT_CLAIMED);
        assertThat(store.find(key).orElseThrow().attemptCount()).isEqualTo(1);

        store.registerOrUpdate(registration(
                key, initialEvents, RecordAnnouncementProjection.EDIT, "b"));
        assertThat(coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        var edited = store.find(key).orElseThrow();
        assertThat(edited.state()).isEqualTo(RecordWorkState.SYNCHRONIZED);
        assertThat(edited.attemptCount()).isEqualTo(2);
        assertThat(gateway.edited).containsExactlyElementsOf(originalMessageIds);
        assertThat(gateway.discoveryCalls).isZero();

        store.registerOrUpdate(registration(
                key, List.of(initialEvents.getFirst()), RecordAnnouncementProjection.EDIT, "c"));
        assertThat(coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        var reduced = store.find(key).orElseThrow();
        assertThat(reduced.state()).isEqualTo(RecordWorkState.SYNCHRONIZED);
        assertThat(reduced.attemptCount()).isEqualTo(3);
        assertThat(reduced.messages()).containsExactly(new RecordAnnouncementMessage(0, originalMessageIds.getFirst()));
        assertThat(gateway.deleted).containsExactlyElementsOf(originalMessageIds.subList(1, originalMessageIds.size()));
        assertThat(gateway.discoveryCalls).isZero();

        store.registerOrUpdate(registration(
                key, List.of(), RecordAnnouncementProjection.DELETE, "d"));
        assertThat(coordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        var deleted = store.find(key).orElseThrow();
        assertThat(deleted.state()).isEqualTo(RecordWorkState.SYNCHRONIZED);
        assertThat(deleted.attemptCount()).isEqualTo(4);
        assertThat(deleted.messages()).isEmpty();
        assertThat(deleted.deletedAt()).isPresent();
        assertThat(gateway.deleted).containsExactlyInAnyOrderElementsOf(originalMessageIds);
        assertThat(gateway.discoveryCalls).isZero();
    }

    @Test
    void retryableEditPersistsTheConcreteGatewayCauseAndRecoversByMessageId() {
        RecordAnnouncementKey key = new RecordAnnouncementKey(1, 2, "result:retry-observability:live");
        UUID eventId = appendEvent("retry-observability");
        PostgresRecordAnnouncementStore initialStore = new PostgresRecordAnnouncementStore(jdbc, fixedClock(NOW));
        LifecycleGateway gateway = new LifecycleGateway();
        initialStore.registerOrUpdate(registration(key, eventId));
        RecordAnnouncementDeliveryCoordinator initialCoordinator = coordinator(initialStore, jdbc, gateway, NOW, true);
        assertThat(initialCoordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        assertThat(initialCoordinator.runNext()).isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);

        initialStore.registerOrUpdate(registration(
                key, List.of(eventId), RecordAnnouncementProjection.EDIT, "b"));
        gateway.editFailure = new RecordAnnouncementMessageGateway.RetryableMessageException(
                "record announcement edit failed (discord_error=SERVER_ERROR)", null);
        assertThat(initialCoordinator.runNext())
                .isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.FAILED_RETRYABLE);
        var retryable = initialStore.find(key).orElseThrow();
        assertThat(retryable.state()).isEqualTo(RecordWorkState.RETRYABLE);
        assertThat(retryable.attemptCount()).isEqualTo(2);
        assertThat(retryable.failure()).hasValueSatisfying(failure -> assertThat(failure.safeMessage())
                .isEqualTo("record announcement edit failed (discord_error=SERVER_ERROR)"));

        Instant retryAt = retryable.nextRetryAt().orElseThrow();
        JdbcTemplate retryJdbc = isolatedJdbc();
        PostgresRecordAnnouncementStore retryStore =
                new PostgresRecordAnnouncementStore(retryJdbc, fixedClock(retryAt));
        gateway.editFailure = null;
        RecordAnnouncementDeliveryCoordinator retryCoordinator =
                coordinator(retryStore, retryJdbc, gateway, retryAt, true);
        assertThat(retryCoordinator.runNext())
                .isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        assertThat(retryStore.find(key).orElseThrow()).satisfies(snapshot -> {
            assertThat(snapshot.state()).isEqualTo(RecordWorkState.SYNCHRONIZED);
            assertThat(snapshot.attemptCount()).isEqualTo(3);
            assertThat(snapshot.failure()).isEmpty();
        });
        assertThat(gateway.discoveryCalls).isZero();
    }

    private RecordAnnouncementDeliveryCoordinator coordinator(
            PostgresRecordAnnouncementStore store, JdbcTemplate workerJdbc,
            RecordAnnouncementMessageGateway gateway, Instant now, boolean publicAnnouncementsEnabled) {
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutors.add(heartbeat);
        PlayerStore unusedPlayers = new PlayerStore() {
            @Override public StoredPlayer upsert(PlayerUpsert request) { throw new UnsupportedOperationException(); }
            @Override public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) { return Optional.empty(); }
            @Override public List<StoredPlayer> findAllPlayers() { return List.of(); }
        };
        return new RecordAnnouncementDeliveryCoordinator(
                store,
                new PostgresRecordEventStore(workerJdbc, fixedClock(now)),
                unusedPlayers,
                gateway,
                new RecordAnnouncementRenderer(),
                fixedClock(now),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                Duration.ofSeconds(10),
                heartbeat,
                publicAnnouncementsEnabled,
                (result, duration) -> { });
    }

    private UUID appendEvent(String suffix) {
        UUID id = UUID.randomUUID();
        RecordEventDraft draft = new RecordEventDraft(
                id,
                "event:" + suffix,
                new RecordStateKey(1,
                        new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
                        RecordDefinitionVersion.RECORDS_V1,
                        new RecordScope.Personal(1)),
                RecordEventType.RESULT_RECORD_BROKEN,
                Optional.empty(),
                new AttemptsDurationRecordValue(2, Duration.ofSeconds(60)),
                Optional.empty(),
                Optional.of(1L),
                Optional.empty(),
                new RecordSourceReference.GameResult(
                        1, 0, 1, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6)),
                "result:" + suffix,
                RecordProcessingOrigin.LIVE_SUBMISSION,
                NOW);
        return events.append(draft).snapshot().draft().eventId();
    }

    private static RecordAnnouncementRegistration registration(RecordAnnouncementKey key, UUID eventId) {
        return registration(key, List.of(eventId), RecordAnnouncementProjection.CREATE, "a");
    }

    private static RecordAnnouncementRegistration registration(
            RecordAnnouncementKey key,
            List<UUID> eventIds,
            RecordAnnouncementProjection projection,
            String fingerprintCharacter) {
        return new RecordAnnouncementRegistration(
                key,
                RecordAnnouncementSubject.player(1),
                RecordAnnouncementPhase.LIVE_EVALUATION,
                projection,
                RecordAnnouncementRenderer.VERSION,
                fingerprintCharacter.repeat(64),
                eventIds);
    }

    private JdbcTemplate isolatedJdbc() {
        return new JdbcTemplate(dataSource());
    }

    private static TransactionTemplate transactions(JdbcTemplate jdbc) {
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    private static RecordLeaseClaimRequest request(Instant claimedAt, Instant leaseUntil) {
        return new RecordLeaseClaimRequest(claimedAt, leaseUntil);
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("announcement claim race timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for announcement claim race", exception);
        }
    }

    private static final class Gateway implements RecordAnnouncementMessageGateway {
        private final List<PublishedPage> discovered;
        private final List<Long> deleted = new ArrayList<>();

        private Gateway(List<PublishedPage> discovered) {
            this.discovered = List.copyOf(discovered);
        }

        @Override
        public long create(long channelId, RenderedRecordAnnouncementPage page) {
            throw new AssertionError("disabled suppression must not create Discord messages");
        }

        @Override
        public void edit(long channelId, long messageId, RenderedRecordAnnouncementPage page) {
            throw new AssertionError("disabled suppression must not edit Discord messages");
        }

        @Override
        public void delete(long channelId, long messageId) {
            deleted.add(messageId);
        }

        @Override
        public boolean exists(long channelId, long messageId) {
            return discovered.stream().anyMatch(page -> page.messageId() == messageId);
        }

        @Override
        public List<PublishedPage> discoverCreatedPages(
                long channelId, String publicationKey, List<RenderedRecordAnnouncementPage> expectedPages) {
            return discovered;
        }
    }

    private static final class CreateThenCrashGateway implements RecordAnnouncementMessageGateway {
        private final RuntimeException failure;
        private final List<PublishedPage> discovered = new ArrayList<>();
        private int createCalls;

        private CreateThenCrashGateway(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public long create(long channelId, RenderedRecordAnnouncementPage page) {
            createCalls++;
            discovered.add(new PublishedPage(300, page.position()));
            throw failure;
        }

        @Override
        public void edit(long channelId, long messageId, RenderedRecordAnnouncementPage page) {
            throw new AssertionError("recovery must adopt the discovered page without editing it");
        }

        @Override
        public void delete(long channelId, long messageId) {
            throw new AssertionError("recovery must retain the discovered page");
        }

        @Override
        public boolean exists(long channelId, long messageId) {
            return discovered.stream().anyMatch(page -> page.messageId() == messageId);
        }

        @Override
        public List<PublishedPage> discoverCreatedPages(
                long channelId, String publicationKey, List<RenderedRecordAnnouncementPage> expectedPages) {
            return List.copyOf(discovered);
        }
    }

    private static final class LifecycleGateway implements RecordAnnouncementMessageGateway {
        private final Map<Long, RenderedRecordAnnouncementPage> pages = new LinkedHashMap<>();
        private final List<Long> edited = new ArrayList<>();
        private final List<Long> deleted = new ArrayList<>();
        private final List<Long> existenceChecks = new ArrayList<>();
        private int discoveryCalls;
        private long nextMessageId = 1_000;
        private RuntimeException editFailure;

        @Override
        public long create(long channelId, RenderedRecordAnnouncementPage page) {
            long messageId = nextMessageId++;
            pages.put(messageId, page);
            return messageId;
        }

        @Override
        public void edit(long channelId, long messageId, RenderedRecordAnnouncementPage page) {
            if (editFailure != null) throw editFailure;
            if (!pages.containsKey(messageId)) throw new AssertionError("persisted message ID is missing");
            pages.put(messageId, page);
            edited.add(messageId);
        }

        @Override
        public void delete(long channelId, long messageId) {
            pages.remove(messageId);
            deleted.add(messageId);
        }

        @Override
        public boolean exists(long channelId, long messageId) {
            existenceChecks.add(messageId);
            return pages.containsKey(messageId);
        }

        @Override
        public List<PublishedPage> discoverCreatedPages(
                long channelId, String publicationKey, List<RenderedRecordAnnouncementPage> expectedPages) {
            discoveryCalls++;
            throw new AssertionError("ordinary delivery must not scan Discord history");
        }
    }
}
