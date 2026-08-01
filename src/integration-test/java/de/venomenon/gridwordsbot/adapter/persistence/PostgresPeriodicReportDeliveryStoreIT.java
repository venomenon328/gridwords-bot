package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaim;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaimRequest;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryContent;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailure;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailureCategory;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryPageProgress;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
import de.venomenon.gridwordsbot.domain.reporting.ReportDueAt;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
class PostgresPeriodicReportDeliveryStoreIT {
    private static final Instant NOW = Instant.parse("2026-08-03T08:00:00Z");
    private static final Instant CATCH_UP_END = Instant.parse("2026-08-06T08:00:00Z");
    private static final String FINGERPRINT = "a".repeat(64);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

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
    void migrationCreatesDeliveryTablesConstraintsAndOnlyDeliveryMetadata() {
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'periodic_report_delivery'
                ORDER BY ordinal_position
                """, String.class)).contains(
                "period_end", "due_at", "catch_up_ends_at", "claim_token", "claim_until", "attempt_count",
                "next_retry_at", "failure_category", "safe_error", "content_fingerprint", "expected_page_count");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_name IN ('periodic_report_delivery', 'periodic_report_delivery_page')
                  AND constraint_name IN ('uq_periodic_report_delivery_business_key',
                    'pk_periodic_report_delivery_page', 'ck_periodic_report_delivery_claim')
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name = 'periodic_report_delivery' AND column_name IN ('player_statistics', 'series_snapshot')
                """, Integer.class)).isZero();
    }

    @Test
    void registrationReplaysIdenticalFactsAndRejectsConflictingMetadata() {
        PeriodicReportDeliveryRegistration registration = registration(2, Optional.of(content(2)));

        assertThat(store.register(registration).state()).isEqualTo(PeriodicReportDeliveryState.OPEN);
        assertThat(store.register(registration).registration()).isEqualTo(registration);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM periodic_report_delivery", Integer.class)).isOne();

        PeriodicReportDeliveryRegistration conflicting = new PeriodicReportDeliveryRegistration(
                registration.key(), new PeriodicReportDeliveryMetadata(period(), dueAt(), CATCH_UP_END.plusSeconds(1)),
                registration.content());
        assertThatIllegalStateException().isThrownBy(() -> store.register(conflicting));
    }

    @Test
    void exactlyOneConcurrentClaimerWinsAndExpiredLeaseCanBeTakenOver() throws Exception {
        PeriodicReportDeliveryRegistration registration = registration(3, Optional.of(content(1)));
        store.register(registration);
        PeriodicReportDeliveryClaimRequest request = request(NOW, NOW.plusSeconds(60));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Optional<PeriodicReportDeliveryClaim>> claims = executor.invokeAll(List.of(
                            claimAction(registration.key(), request), claimAction(registration.key(), request)))
                    .stream().map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    }).toList();
            assertThat(claims).filteredOn(Optional::isPresent).hasSize(1);
        }

        PeriodicReportDeliveryClaim first = store.find(registration.key()).orElseThrow().claim().orElseThrow();
        PeriodicReportDeliveryClaim replacement = store.claim(registration.key(), request(NOW.plusSeconds(60), NOW.plusSeconds(120)))
                .orElseThrow();
        assertThat(replacement.token()).isNotEqualTo(first.token());
        assertThat(store.find(registration.key()).orElseThrow().attemptCount()).isEqualTo(2);
    }

    @Test
    void tokenFencingAndPageWritesPersistOnlyContiguousVisibleOrder() {
        PeriodicReportDeliveryRegistration registration = registration(4, Optional.of(content(2)));
        store.register(registration);
        PeriodicReportDeliveryClaim stale = store.claim(registration.key(), request(NOW, NOW.plusSeconds(1))).orElseThrow();
        PeriodicReportDeliveryClaim owner = store.claim(registration.key(), request(NOW.plusSeconds(1), NOW.plusSeconds(60))).orElseThrow();

        assertThat(store.recordPage(registration.key(), stale.token(), new PeriodicReportDeliveryPageProgress(0, 100))).isFalse();
        assertThat(store.recordPage(registration.key(), owner.token(), new PeriodicReportDeliveryPageProgress(1, 101))).isFalse();
        assertThat(store.recordPage(registration.key(), owner.token(), new PeriodicReportDeliveryPageProgress(0, 100))).isTrue();
        assertThat(store.recordPage(registration.key(), owner.token(), new PeriodicReportDeliveryPageProgress(1, 101))).isTrue();
        assertThat(store.recordPage(registration.key(), owner.token(), new PeriodicReportDeliveryPageProgress(1, 102))).isFalse();
        assertThat(store.find(registration.key()).orElseThrow().pageProgress()).containsExactly(
                new PeriodicReportDeliveryPageProgress(0, 100), new PeriodicReportDeliveryPageProgress(1, 101));
        assertThat(store.markSucceeded(registration.key(), stale.token(), NOW.plusSeconds(2))).isFalse();
        assertThat(store.markSucceeded(registration.key(), owner.token(), NOW.plusSeconds(2))).isTrue();
    }

    @Test
    void retryKeepsFailureAndBackoffUntilTheNextClaim() {
        PeriodicReportDeliveryRegistration registration = registration(5, Optional.of(content(1)));
        store.register(registration);
        PeriodicReportDeliveryClaim claim = store.claim(registration.key(), request(NOW, NOW.plusSeconds(60))).orElseThrow();
        Instant retryAt = NOW.plusSeconds(30);

        assertThat(store.markRetryableFailure(registration.key(), claim.token(), new PeriodicReportDeliveryFailure(
                PeriodicReportDeliveryFailureCategory.UNKNOWN, "Discord request outcome is unknown"), retryAt)).isTrue();
        assertThat(store.find(registration.key()).orElseThrow())
                .extracting(snapshot -> snapshot.state(), snapshot -> snapshot.nextRetryAt(), snapshot -> snapshot.failure().orElseThrow().category())
                .containsExactly(PeriodicReportDeliveryState.RETRYABLE, Optional.of(retryAt), PeriodicReportDeliveryFailureCategory.UNKNOWN);
        assertThat(store.claim(registration.key(), request(NOW.plusSeconds(29), NOW.plusSeconds(90)))).isEmpty();
        assertThat(store.claim(registration.key(), request(retryAt, NOW.plusSeconds(90)))).isPresent();
        assertThat(store.find(registration.key()).orElseThrow().attemptCount()).isEqualTo(2);
    }

    @Test
    void noOpSuccessExpiryAndPermanentFailureAreTerminal() {
        PeriodicReportDeliveryRegistration noOp = registration(6, Optional.empty());
        store.register(noOp);
        PeriodicReportDeliveryClaim noOpClaim = store.claim(noOp.key(), request(NOW, NOW.plusSeconds(60))).orElseThrow();
        assertThat(store.markNoOp(noOp.key(), noOpClaim.token(), NOW)).isTrue();

        PeriodicReportDeliveryRegistration success = registration(7, Optional.of(content(1)));
        store.register(success);
        PeriodicReportDeliveryClaim successClaim = store.claim(success.key(), request(NOW, NOW.plusSeconds(60))).orElseThrow();
        assertThat(store.recordPage(success.key(), successClaim.token(), new PeriodicReportDeliveryPageProgress(0, 700))).isTrue();
        assertThat(store.markSucceeded(success.key(), successClaim.token(), NOW)).isTrue();

        PeriodicReportDeliveryRegistration expired = registration(8, Optional.empty());
        store.register(expired);
        assertThat(store.markExpired(expired.key(), CATCH_UP_END)).isTrue();

        PeriodicReportDeliveryRegistration permanent = registration(9, Optional.empty());
        store.register(permanent);
        PeriodicReportDeliveryClaim permanentClaim = store.claim(permanent.key(), request(NOW, NOW.plusSeconds(60))).orElseThrow();
        assertThat(store.markPermanentFailure(permanent.key(), permanentClaim.token(), new PeriodicReportDeliveryFailure(
                PeriodicReportDeliveryFailureCategory.PERMANENT, "Missing channel permission"), NOW)).isTrue();

        assertThat(List.of(noOp, success, expired, permanent)).allSatisfy(registration ->
                assertThat(store.claim(registration.key(), request(CATCH_UP_END, CATCH_UP_END.plusSeconds(60)))).isEmpty());
    }

    @Test
    void databaseConstraintsRejectDuplicatePageIndexesAndInvalidClaimCombinations() {
        PeriodicReportDeliveryRegistration registration = registration(10, Optional.of(content(2)));
        store.register(registration);
        long deliveryId = jdbc.queryForObject("SELECT id FROM periodic_report_delivery", Long.class);
        jdbc.update("""
                INSERT INTO periodic_report_delivery_page (delivery_id, page_index, discord_message_id, created_at)
                VALUES (?, 0, 1, ?)
                """, deliveryId, java.sql.Timestamp.from(NOW));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO periodic_report_delivery_page (delivery_id, page_index, discord_message_id, created_at)
                VALUES (?, 0, 2, ?)
                """, deliveryId, java.sql.Timestamp.from(NOW))).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE periodic_report_delivery SET claim_until = ? WHERE id = ?
                """, NOW, deliveryId)).isInstanceOf(Exception.class);
    }

    private Callable<Optional<PeriodicReportDeliveryClaim>> claimAction(
            PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaimRequest request) {
        return () -> store.claim(key, request);
    }

    private static PeriodicReportDeliveryRegistration registration(
            long channelId, Optional<PeriodicReportDeliveryContent> content) {
        return new PeriodicReportDeliveryRegistration(
                new PeriodicReportDeliveryKey(1, channelId, ReportType.WEEKLY, period().startDate()),
                new PeriodicReportDeliveryMetadata(period(), dueAt(), CATCH_UP_END), content);
    }

    private static PeriodicReportDeliveryContent content(int expectedPages) {
        return new PeriodicReportDeliveryContent(FINGERPRINT, expectedPages);
    }

    private static PeriodicReportDeliveryClaimRequest request(Instant claimedAt, Instant leaseUntil) {
        return new PeriodicReportDeliveryClaimRequest(claimedAt, leaseUntil);
    }

    private static ReportPeriod period() {
        return new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
    }

    private static ReportDueAt dueAt() {
        return new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.of(8, 0), BERLIN);
    }
}
