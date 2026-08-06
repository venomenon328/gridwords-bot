package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementClaim;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementMessage;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementPhase;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSubject;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
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
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import liquibase.integration.spring.SpringLiquibase;
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
class PostgresRecordClaimLeaseFencingIT {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private PostgresRecordEventStore events;
    private PostgresRecordBootstrapStore bootstraps;
    private PostgresRecordAnnouncementStore announcements;

    @BeforeAll
    void migrate() throws Exception {
        var source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();

        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        Clock clock = fixedClock(NOW);
        events = new PostgresRecordEventStore(jdbc, clock);
        bootstraps = new PostgresRecordBootstrapStore(jdbc, clock);
        announcements = new PostgresRecordAnnouncementStore(jdbc, clock);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM record_announcement_message");
        jdbc.update("DELETE FROM record_announcement_event");
        jdbc.update("DELETE FROM record_announcement");
        jdbc.update("DELETE FROM record_event");
        jdbc.update("DELETE FROM record_bootstrap");
    }

    @Test
    void expiredBootstrapTokenCannotRenewCompleteOrFailWithoutTakeover() {
        RecordBootstrapKey key =
                new RecordBootstrapKey(1, RecordDefinitionVersion.RECORDS_V1);
        bootstraps.register(key);
        RecordLeaseClaim claim =
                bootstraps.claim(key, request(NOW, NOW.plusSeconds(10))).orElseThrow();

        PostgresRecordBootstrapStore expired =
                new PostgresRecordBootstrapStore(jdbc, fixedClock(NOW.plusSeconds(11)));

        assertThat(expired.renewLease(
                        key,
                        claim.token(),
                        request(NOW.plusSeconds(11), NOW.plusSeconds(21))))
                .isFalse();
        assertThat(expired.markSucceeded(key, claim.token(), NOW.plusSeconds(11)))
                .isFalse();
        assertThat(expired.markRetryableFailure(
                        key,
                        claim.token(),
                        new RecordWorkFailure(RecordWorkFailureCategory.UNKNOWN, "retry"),
                        NOW.plusSeconds(30)))
                .isFalse();
        assertThat(expired.markPermanentFailure(
                        key,
                        claim.token(),
                        new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT, "permanent"),
                        NOW.plusSeconds(11)))
                .isFalse();

        var persisted = expired.find(key).orElseThrow();
        assertThat(persisted.state()).isEqualTo(RecordWorkState.CLAIMED);
        assertThat(persisted.claimToken()).contains(claim.token());
    }

    @Test
    void expiredAnnouncementTokenCannotMutateProjectionWithoutTakeover() {
        UUID eventId = appendEvent("lease-expired");
        RecordAnnouncementKey key =
                new RecordAnnouncementKey(1, 2, "result:lease-expired:live");
        announcements.registerOrUpdate(registration(key, eventId));
        RecordLeaseClaim claim =
                announcements.claim(key, request(NOW, NOW.plusSeconds(10))).orElseThrow();

        List<RecordAnnouncementMessage> original =
                List.of(new RecordAnnouncementMessage(0, 100));
        assertThat(replaceMessagesInTransaction(
                        announcements, key, claim.token(), original))
                .isTrue();

        PostgresRecordAnnouncementStore expired =
                new PostgresRecordAnnouncementStore(jdbc, fixedClock(NOW.plusSeconds(11)));

        assertThat(expired.renewLease(
                        key,
                        claim.token(),
                        request(NOW.plusSeconds(11), NOW.plusSeconds(21))))
                .isFalse();
        assertThat(replaceMessagesInTransaction(
                        expired,
                        key,
                        claim.token(),
                        List.of(new RecordAnnouncementMessage(0, 200))))
                .isFalse();
        assertThat(expired.markSynchronized(key, claim.token(), NOW.plusSeconds(11)))
                .isFalse();
        assertThat(expired.markRetryableFailure(
                        key,
                        claim.token(),
                        new RecordWorkFailure(RecordWorkFailureCategory.UNKNOWN, "retry"),
                        NOW.plusSeconds(30)))
                .isFalse();
        assertThat(expired.markPermanentFailure(
                        key,
                        claim.token(),
                        new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT, "permanent"),
                        NOW.plusSeconds(11)))
                .isFalse();
        assertThat(expired.markExternallyRemoved(
                        key, claim.token(), NOW.plusSeconds(11)))
                .isFalse();

        var persisted = expired.find(key).orElseThrow();
        assertThat(persisted.state()).isEqualTo(RecordWorkState.CLAIMED);
        assertThat(persisted.claimToken()).contains(claim.token());
        assertThat(persisted.messages()).containsExactlyElementsOf(original);
    }

    @Test
    void messageReplacementCannotCommitAfterConcurrentLeaseTakeover() throws Exception {
        UUID eventId = appendEvent("lease-race");
        RecordAnnouncementKey key =
                new RecordAnnouncementKey(1, 2, "result:lease-race:live");
        announcements.registerOrUpdate(registration(key, eventId));
        RecordLeaseClaim originalClaim =
                announcements.claim(key, request(NOW, NOW.plusSeconds(10))).orElseThrow();

        List<RecordAnnouncementMessage> originalMessages =
                List.of(new RecordAnnouncementMessage(0, 100));
        assertThat(replaceMessagesInTransaction(
                        announcements, key, originalClaim.token(), originalMessages))
                .isTrue();

        JdbcTemplate takeoverJdbc = isolatedJdbc();
        JdbcTemplate staleJdbc = isolatedJdbc();
        TransactionTemplate takeoverTransactions = transactionsFor(takeoverJdbc);
        TransactionTemplate staleTransactions = transactionsFor(staleJdbc);
        PostgresRecordAnnouncementStore takeoverStore =
                new PostgresRecordAnnouncementStore(takeoverJdbc, fixedClock(NOW.plusSeconds(10)));
        PostgresRecordAnnouncementStore staleStore =
                new PostgresRecordAnnouncementStore(staleJdbc, fixedClock(NOW.plusSeconds(9)));

        CountDownLatch takeoverHasRowLock = new CountDownLatch(1);
        CountDownLatch releaseTakeover = new CountDownLatch(1);
        CountDownLatch staleReplacementStarted = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<RecordLeaseClaim> takeover = pool.submit(() ->
                    takeoverTransactions.execute(status -> {
                        RecordLeaseClaim replacement = takeoverStore
                                .claim(
                                        key,
                                        request(
                                                NOW.plusSeconds(10),
                                                NOW.plusSeconds(20)))
                                .orElseThrow();
                        takeoverHasRowLock.countDown();
                        await(releaseTakeover);
                        return replacement;
                    }));

            assertThat(takeoverHasRowLock.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> staleReplacement = pool.submit(() ->
                    staleTransactions.execute(status -> {
                        staleReplacementStarted.countDown();
                        return staleStore.replaceMessages(
                                key,
                                originalClaim.token(),
                                List.of(new RecordAnnouncementMessage(0, 200)));
                    }));

            assertThat(staleReplacementStarted.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                Thread.sleep(100);
                assertThat(staleReplacement.isDone()).isFalse();
            } finally {
                releaseTakeover.countDown();
            }

            RecordLeaseClaim replacementClaim = takeover.get(10, TimeUnit.SECONDS);
            assertThat(staleReplacement.get(10, TimeUnit.SECONDS)).isFalse();

            var persisted = announcements.find(key).orElseThrow();
            assertThat(persisted.claimToken()).contains(replacementClaim.token());
            assertThat(persisted.messages()).containsExactlyElementsOf(originalMessages);
        }
    }

    @Test
    void disabledRestartSuppressesAlreadyClaimedNeverPublishedCreateWithoutLaterBacklog() {
        UUID eventId = appendEvent("disable-restart");
        RecordAnnouncementKey key = new RecordAnnouncementKey(1, 2, "result:disable-restart:live");
        PostgresRecordAnnouncementStore firstWorker = announcementStore(NOW);
        firstWorker.registerOrUpdate(registration(key, eventId));
        RecordLeaseClaim claimed = firstWorker.claim(key, request(NOW, NOW.plusSeconds(60))).orElseThrow();

        PostgresRecordAnnouncementStore disabledRestart = announcementStore(NOW.plusSeconds(1));
        assertThat(disabledRestart.suppressPendingCreates(NOW.plusSeconds(1))).isEqualTo(1);
        assertThat(disabledRestart.find(key).orElseThrow().state()).isEqualTo(RecordWorkState.SUPPRESSED);
        assertThat(disabledRestart.find(key).orElseThrow().claimToken()).isEmpty();

        PostgresRecordAnnouncementStore enabledRestart = announcementStore(NOW.plusSeconds(2));
        assertThat(enabledRestart.claimNext(request(NOW.plusSeconds(2), NOW.plusSeconds(62)), true)).isEmpty();
        assertThat(enabledRestart.renewLease(key, claimed.token(), request(NOW.plusSeconds(2), NOW.plusSeconds(62))))
                .isFalse();
    }

    @Test
    void restartAfterMultipartCreateRetainsOrderedIdsAndSynchronizedProjection() {
        UUID eventId = appendEvent("multipart-restart");
        RecordAnnouncementKey key = new RecordAnnouncementKey(1, 2, "result:multipart-restart:live");
        JdbcTemplate crashedJdbc = isolatedJdbc();
        PostgresRecordAnnouncementStore crashedWorker =
                new PostgresRecordAnnouncementStore(crashedJdbc, fixedClock(NOW));
        crashedWorker.registerOrUpdate(registration(key, eventId));
        RecordLeaseClaim original = crashedWorker.claim(key, request(NOW, NOW.plusSeconds(10))).orElseThrow();
        List<RecordAnnouncementMessage> pages = List.of(
                new RecordAnnouncementMessage(0, 100), new RecordAnnouncementMessage(1, 200));
        assertThat(replaceMessagesInTransaction(
                        transactionsFor(crashedJdbc), crashedWorker, key, original.token(), pages))
                .isTrue();

        PostgresRecordAnnouncementStore recoveredWorker = announcementStore(NOW.plusSeconds(11));
        RecordAnnouncementClaim recovered = recoveredWorker.claimNext(
                request(NOW.plusSeconds(11), NOW.plusSeconds(21)), true).orElseThrow();
        assertThat(recoveredWorker.find(key).orElseThrow().messages()).containsExactlyElementsOf(pages);
        assertThat(recoveredWorker.markSynchronized(key, recovered.token(), NOW.plusSeconds(11))).isTrue();

        var synchronizedSnapshot = recoveredWorker.find(key).orElseThrow();
        assertThat(synchronizedSnapshot.registration().desiredProjection())
                .isEqualTo(RecordAnnouncementProjection.CREATE);
        assertThat(synchronizedSnapshot.messages()).containsExactlyElementsOf(pages);
    }

    private boolean replaceMessagesInTransaction(
            PostgresRecordAnnouncementStore store,
            RecordAnnouncementKey key,
            UUID token,
            List<RecordAnnouncementMessage> messages) {
        return replaceMessagesInTransaction(transactions, store, key, token, messages);
    }

    private static boolean replaceMessagesInTransaction(
            TransactionTemplate transactionTemplate,
            PostgresRecordAnnouncementStore store,
            RecordAnnouncementKey key,
            UUID token,
            List<RecordAnnouncementMessage> messages) {
        return Boolean.TRUE.equals(
                transactionTemplate.execute(status -> store.replaceMessages(key, token, messages)));
    }

    private PostgresRecordAnnouncementStore announcementStore(Instant instant) {
        return new PostgresRecordAnnouncementStore(isolatedJdbc(), fixedClock(instant));
    }

    private JdbcTemplate isolatedJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static TransactionTemplate transactionsFor(JdbcTemplate jdbc) {
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    private UUID appendEvent(String suffix) {
        RecordEventDraft draft = new RecordEventDraft(
                UUID.randomUUID(),
                "event:" + suffix,
                stateKey(),
                RecordEventType.RESULT_RECORD_BROKEN,
                Optional.empty(),
                new AttemptsDurationRecordValue(2, Duration.ofSeconds(60)),
                Optional.empty(),
                Optional.of(1L),
                Optional.empty(),
                new RecordSourceReference.GameResult(
                        1,
                        0,
                        1,
                        GameType.GRIDWORDS,
                        LocalDate.of(2026, 8, 4)),
                "result:" + suffix,
                RecordProcessingOrigin.LIVE_SUBMISSION,
                NOW);
        return events.append(draft).snapshot().draft().eventId();
    }

    private static RecordAnnouncementRegistration registration(
            RecordAnnouncementKey key, UUID eventId) {
        return new RecordAnnouncementRegistration(
                key,
                RecordAnnouncementSubject.player(1),
                RecordAnnouncementPhase.LIVE_EVALUATION,
                RecordAnnouncementProjection.CREATE,
                "records-renderer-v1",
                "a".repeat(64),
                List.of(eventId));
    }

    private static RecordStateKey stateKey() {
        return new RecordStateKey(
                1,
                new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
                RecordDefinitionVersion.RECORDS_V1,
                new RecordScope.Personal(1));
    }

    private static RecordLeaseClaimRequest request(Instant claimedAt, Instant leaseUntil) {
        return new RecordLeaseClaimRequest(claimedAt, leaseUntil);
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("lease race timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("lease race interrupted", exception);
        }
    }
}
