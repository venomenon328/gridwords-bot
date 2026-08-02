package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaim;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaimRequest;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryContent;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryExpiration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailure;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailureCategory;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryPageProgress;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryScope;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliverySnapshot;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
import de.venomenon.gridwordsbot.domain.reporting.ReportDueAt;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresPeriodicReportDeliveryExpirationIT {
    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");
    private static final String FINGERPRINT = "a".repeat(64);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresPeriodicReportDeliveryStore store;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        store = new PostgresPeriodicReportDeliveryStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM periodic_report_delivery_page");
        jdbc.update("DELETE FROM periodic_report_delivery");
    }

    @Test
    void findsNoAnchorForAnEmptyExactScope() {
        assertThat(store.findLatestPeriodStart(scope(1, 2, ReportType.WEEKLY))).isEmpty();
    }

    @Test
    void findsTheMaximumAcrossEveryDeliveryStateAndKeepsScopesSeparate() {
        PeriodicReportDeliveryRegistration open = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 6, 1), true);
        store.register(open);

        PeriodicReportDeliveryRegistration retryable = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 6, 8), true);
        store.register(retryable);
        PeriodicReportDeliveryClaim retryClaim = claim(retryable, retryable.metadata().dueAt().instant().plusSeconds(1));
        assertThat(store.markRetryableFailure(retryable.key(), retryClaim.token(), new PeriodicReportDeliveryFailure(
                PeriodicReportDeliveryFailureCategory.UNKNOWN, "temporary failure"), retryable.metadata().dueAt().instant().plusSeconds(2)))
                .isTrue();

        PeriodicReportDeliveryRegistration claimed = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 6, 15), true);
        store.register(claimed);
        claim(claimed, claimed.metadata().dueAt().instant().plusSeconds(1));

        PeriodicReportDeliveryRegistration succeeded = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 6, 22), true);
        store.register(succeeded);
        PeriodicReportDeliveryClaim successClaim = claim(succeeded, succeeded.metadata().dueAt().instant().plusSeconds(1));
        assertThat(store.recordPage(succeeded.key(), successClaim.token(), new PeriodicReportDeliveryPageProgress(0, 101))).isTrue();
        assertThat(store.markSucceeded(succeeded.key(), successClaim.token(), succeeded.metadata().dueAt().instant().plusSeconds(2))).isTrue();

        PeriodicReportDeliveryRegistration noOp = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 6, 29), false);
        store.register(noOp);
        PeriodicReportDeliveryClaim noOpClaim = claim(noOp, noOp.metadata().dueAt().instant().plusSeconds(1));
        assertThat(store.markNoOp(noOp.key(), noOpClaim.token(), noOp.metadata().dueAt().instant().plusSeconds(2))).isTrue();

        PeriodicReportDeliveryRegistration expired = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 7, 6), false);
        expire(expired);

        PeriodicReportDeliveryRegistration failed = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 7, 13), true);
        store.register(failed);
        PeriodicReportDeliveryClaim failedClaim = claim(failed, failed.metadata().dueAt().instant().plusSeconds(1));
        assertThat(store.markPermanentFailure(failed.key(), failedClaim.token(), new PeriodicReportDeliveryFailure(
                PeriodicReportDeliveryFailureCategory.PERMANENT, "permanent failure"), failed.metadata().dueAt().instant().plusSeconds(2)))
                .isTrue();

        PeriodicReportDeliveryRegistration otherChannel = registration(1, 3, ReportType.WEEKLY, LocalDate.of(2026, 8, 3), false);
        PeriodicReportDeliveryRegistration monthly = registration(1, 2, ReportType.MONTHLY, LocalDate.of(2026, 8, 1), false);
        store.register(otherChannel);
        store.register(monthly);

        assertThat(store.findLatestPeriodStart(scope(1, 2, ReportType.WEEKLY)))
                .contains(LocalDate.of(2026, 7, 13));
        assertThat(store.findLatestPeriodStart(scope(1, 3, ReportType.WEEKLY)))
                .contains(LocalDate.of(2026, 8, 3));
        assertThat(store.findLatestPeriodStart(scope(1, 2, ReportType.MONTHLY)))
                .contains(LocalDate.of(2026, 8, 1));
    }

    @Test
    void createsAnEmptyExpiredTombstoneExactlyAtTheCatchUpEndAndFindsItAsAnchor() {
        PeriodicReportDeliveryRegistration registration = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 7, 27), false);

        PeriodicReportDeliverySnapshot snapshot = expire(registration);

        assertThat(snapshot.registration().content()).isEmpty();
        assertThat(snapshot).extracting(PeriodicReportDeliverySnapshot::state,
                PeriodicReportDeliverySnapshot::attemptCount,
                PeriodicReportDeliverySnapshot::claim,
                PeriodicReportDeliverySnapshot::nextRetryAt,
                PeriodicReportDeliverySnapshot::failure,
                PeriodicReportDeliverySnapshot::pageProgress,
                PeriodicReportDeliverySnapshot::completedAt)
                .containsExactly(PeriodicReportDeliveryState.EXPIRED, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                        List.of(), Optional.of(registration.metadata().catchUpEndsAt()));
        assertThat(store.findLatestPeriodStart(scope(1, 2, ReportType.WEEKLY))).contains(registration.key().periodStart());
    }

    @Test
    void rejectsExpirationBeforeTheCatchUpEndWithoutPersistingAnything() {
        PeriodicReportDeliveryRegistration registration = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 7, 27), false);

        assertThatIllegalArgumentException().isThrownBy(() -> store.expire(expiration(registration),
                registration.metadata().catchUpEndsAt().minusNanos(1)));
        assertThat(store.find(registration.key())).isEmpty();
    }

    @Test
    void repeatedExpirationIsIdempotentAndDoesNotCreateAnotherRow() {
        PeriodicReportDeliveryRegistration registration = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 7, 27), false);

        PeriodicReportDeliverySnapshot first = expire(registration);
        PeriodicReportDeliverySnapshot replay = expire(registration);

        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM periodic_report_delivery", Integer.class)).isOne();
    }

    @Test
    void expiresOpenAndRetryableDeliveriesAndClearsRetryFailureFacts() {
        PeriodicReportDeliveryRegistration open = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 7, 20), true);
        store.register(open);
        assertThat(expire(open).state()).isEqualTo(PeriodicReportDeliveryState.EXPIRED);

        PeriodicReportDeliveryRegistration retryable = registration(1, 3, ReportType.WEEKLY, LocalDate.of(2026, 7, 20), true);
        store.register(retryable);
        PeriodicReportDeliveryClaim claim = claim(retryable, retryable.metadata().dueAt().instant().plusSeconds(1));
        assertThat(store.markRetryableFailure(retryable.key(), claim.token(), new PeriodicReportDeliveryFailure(
                PeriodicReportDeliveryFailureCategory.RETRYABLE, "retry later"), retryable.metadata().dueAt().instant().plusSeconds(2)))
                .isTrue();

        PeriodicReportDeliverySnapshot expired = expire(retryable);
        assertThat(expired).extracting(PeriodicReportDeliverySnapshot::state,
                PeriodicReportDeliverySnapshot::claim,
                PeriodicReportDeliverySnapshot::nextRetryAt,
                PeriodicReportDeliverySnapshot::failure)
                .containsExactly(PeriodicReportDeliveryState.EXPIRED, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void expiresClaimedWorkOnlyAfterItsLeaseEndsAndLeavesAnActiveClaimUntouched() {
        PeriodicReportDeliveryRegistration expiredLease = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 7, 20), true);
        store.register(expiredLease);
        claim(expiredLease, expiredLease.metadata().catchUpEndsAt().minusSeconds(2));
        assertThat(expire(expiredLease).state()).isEqualTo(PeriodicReportDeliveryState.EXPIRED);

        PeriodicReportDeliveryRegistration activeLease = registration(1, 3, ReportType.WEEKLY, LocalDate.of(2026, 8, 3), true);
        store.register(activeLease);
        PeriodicReportDeliveryClaim activeClaim = claim(activeLease, activeLease.metadata().catchUpEndsAt().plusSeconds(1));

        PeriodicReportDeliverySnapshot unchanged = expire(activeLease);
        assertThat(unchanged).extracting(PeriodicReportDeliverySnapshot::state, PeriodicReportDeliverySnapshot::claim)
                .containsExactly(PeriodicReportDeliveryState.CLAIMED, Optional.of(activeClaim));
    }

    @Test
    void leavesForeignTerminalStatesAndExistingExpiredStateUnchanged() {
        PeriodicReportDeliveryRegistration succeeded = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 8, 3), true);
        store.register(succeeded);
        PeriodicReportDeliveryClaim successClaim = claim(succeeded, succeeded.metadata().dueAt().instant().plusSeconds(1));
        assertThat(store.recordPage(succeeded.key(), successClaim.token(), new PeriodicReportDeliveryPageProgress(0, 201))).isTrue();
        assertThat(store.markSucceeded(succeeded.key(), successClaim.token(), succeeded.metadata().dueAt().instant().plusSeconds(2))).isTrue();

        PeriodicReportDeliveryRegistration noOp = registration(1, 3, ReportType.WEEKLY, LocalDate.of(2026, 8, 3), false);
        store.register(noOp);
        PeriodicReportDeliveryClaim noOpClaim = claim(noOp, noOp.metadata().dueAt().instant().plusSeconds(1));
        assertThat(store.markNoOp(noOp.key(), noOpClaim.token(), noOp.metadata().dueAt().instant().plusSeconds(2))).isTrue();

        PeriodicReportDeliveryRegistration failed = registration(1, 4, ReportType.WEEKLY, LocalDate.of(2026, 8, 3), true);
        store.register(failed);
        PeriodicReportDeliveryClaim failedClaim = claim(failed, failed.metadata().dueAt().instant().plusSeconds(1));
        assertThat(store.markPermanentFailure(failed.key(), failedClaim.token(), new PeriodicReportDeliveryFailure(
                PeriodicReportDeliveryFailureCategory.PERMANENT, "permanent failure"), failed.metadata().dueAt().instant().plusSeconds(2))).isTrue();

        PeriodicReportDeliveryRegistration expired = registration(1, 5, ReportType.WEEKLY, LocalDate.of(2026, 8, 3), false);
        PeriodicReportDeliverySnapshot expiredSnapshot = expire(expired);

        assertThat(expire(succeeded).state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
        assertThat(expire(noOp).state()).isEqualTo(PeriodicReportDeliveryState.NO_OP);
        assertThat(expire(failed).state()).isEqualTo(PeriodicReportDeliveryState.FAILED_PERMANENT);
        assertThat(expire(expired)).isEqualTo(expiredSnapshot);
    }

    @Test
    void rejectsConflictingImmutableFactsAndPreservesPersistedPagesDuringExpiration() {
        PeriodicReportDeliveryRegistration registration = registration(1, 2, ReportType.WEEKLY, LocalDate.of(2026, 7, 27), true);
        store.register(registration);
        PeriodicReportDeliveryClaim claim = claim(registration, registration.metadata().catchUpEndsAt().minusSeconds(1));
        assertThat(store.recordPage(registration.key(), claim.token(), new PeriodicReportDeliveryPageProgress(0, 301))).isTrue();

        PeriodicReportDeliveryMetadata conflictingMetadata = new PeriodicReportDeliveryMetadata(
                registration.metadata().period(), registration.metadata().dueAt(), registration.metadata().catchUpEndsAt().plusSeconds(1));
        assertThatIllegalStateException().isThrownBy(() -> store.expire(
                new PeriodicReportDeliveryExpiration(registration.key(), conflictingMetadata), conflictingMetadata.catchUpEndsAt()));

        assertThat(expire(registration).pageProgress()).containsExactly(new PeriodicReportDeliveryPageProgress(0, 301));
    }

    @Test
    void concurrentMissingExpirationCandidatesConvergeOnOneTombstone() throws Exception {
        PeriodicReportDeliveryRegistration registration = registration(1, 2, ReportType.MONTHLY, LocalDate.of(2026, 7, 1), false);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<PeriodicReportDeliverySnapshot> snapshots = executor.invokeAll(List.<Callable<PeriodicReportDeliverySnapshot>>of(
                            () -> expire(registration), () -> expire(registration)))
                    .stream().map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    }).toList();
            assertThat(snapshots).allMatch(snapshot -> snapshot.state() == PeriodicReportDeliveryState.EXPIRED);
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM periodic_report_delivery", Integer.class)).isOne();
        assertThat(store.findLatestPeriodStart(scope(1, 2, ReportType.MONTHLY))).contains(LocalDate.of(2026, 7, 1));
    }

    private PeriodicReportDeliverySnapshot expire(PeriodicReportDeliveryRegistration registration) {
        return store.expire(expiration(registration), registration.metadata().catchUpEndsAt());
    }

    private static PeriodicReportDeliveryExpiration expiration(PeriodicReportDeliveryRegistration registration) {
        return new PeriodicReportDeliveryExpiration(registration.key(), registration.metadata());
    }

    private PeriodicReportDeliveryClaim claim(PeriodicReportDeliveryRegistration registration, Instant leaseUntil) {
        Instant claimedAt = registration.metadata().dueAt().instant();
        return store.claim(registration.key(), new PeriodicReportDeliveryClaimRequest(claimedAt, leaseUntil)).orElseThrow();
    }

    private static PeriodicReportDeliveryScope scope(long guildId, long channelId, ReportType reportType) {
        return new PeriodicReportDeliveryScope(guildId, channelId, reportType);
    }

    private static PeriodicReportDeliveryRegistration registration(
            long guildId, long channelId, ReportType reportType, LocalDate periodStart, boolean withContent) {
        ReportPeriod period = reportType == ReportType.WEEKLY
                ? new ReportPeriod(periodStart, periodStart.plusDays(6))
                : new ReportPeriod(periodStart, periodStart.plusMonths(1).minusDays(1));
        ReportDueAt dueAt = new ReportDueAt(period.endDate().plusDays(1), LocalTime.of(8, 0), ZoneOffset.UTC);
        return new PeriodicReportDeliveryRegistration(
                new PeriodicReportDeliveryKey(guildId, channelId, reportType, periodStart),
                new PeriodicReportDeliveryMetadata(period, dueAt, dueAt.instant().plus(reportType.catchUpDuration())),
                withContent ? Optional.of(new PeriodicReportDeliveryContent(FINGERPRINT, 1)) : Optional.empty());
    }
}
