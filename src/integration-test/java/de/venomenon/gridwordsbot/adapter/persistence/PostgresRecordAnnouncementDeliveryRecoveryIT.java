package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import java.util.List;
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
            Optional<RecordAnnouncementClaim> second = secondTx.execute(status -> secondStore.claimNext(
                    request(NOW, NOW.plusSeconds(60)), true));
            assertThat(second).isEmpty();

            releaseFirst.countDown();
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
        assertThat(replaceMessagesInTransaction(
                activeStore, key, original.token(), List.of(new RecordAnnouncementMessage(0, 100)))).isTrue();

        Instant restartedAt = NOW.plusSeconds(11);
        JdbcTemplate restartedJdbc = isolatedJdbc();
        PostgresRecordAnnouncementStore disabledStore =
                new PostgresRecordAnnouncementStore(restartedJdbc, fixedClock(restartedAt), false);
        Gateway gateway = new Gateway(List.of(
                new RecordAnnouncementMessageGateway.PublishedPage(100, 0),
                new RecordAnnouncementMessageGateway.PublishedPage(200, 1)));
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutors.add(heartbeat);
        RecordAnnouncementDeliveryCoordinator coordinator = new RecordAnnouncementDeliveryCoordinator(
                disabledStore,
                new PostgresRecordEventStore(restartedJdbc, fixedClock(restartedAt)),
                mock(PlayerStore.class),
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
        return new RecordAnnouncementRegistration(
                key,
                RecordAnnouncementSubject.player(1),
                RecordAnnouncementPhase.LIVE_EVALUATION,
                RecordAnnouncementProjection.CREATE,
                RecordAnnouncementRenderer.VERSION,
                "a".repeat(64),
                List.of(eventId));
    }

    private boolean replaceMessagesInTransaction(
            PostgresRecordAnnouncementStore store, RecordAnnouncementKey key, UUID token,
            List<RecordAnnouncementMessage> messages) {
        return Boolean.TRUE.equals(new TransactionTemplate(new DataSourceTransactionManager(source))
                .execute(status -> store.replaceMessages(key, token, messages)));
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
        public List<PublishedPage> findByPublicationKey(long channelId, String publicationKey) {
            return discovered;
        }
    }
}
