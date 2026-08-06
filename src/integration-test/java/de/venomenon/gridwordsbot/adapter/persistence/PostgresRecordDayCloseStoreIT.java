package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.record.RecordDayCloseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class PostgresRecordDayCloseStoreIT {
    private static final Instant NOW = Instant.parse("2026-08-06T04:00:00Z");
    private static final RecordDayCloseKey YESTERDAY = new RecordDayCloseKey(42,
            RecordDefinitionVersion.RECORDS_V1, LocalDate.of(2026, 8, 5));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresRecordDayCloseStore store;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
        store = storeAt(NOW);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM record_day_close");
    }

    @Test
    void registersClaimsRetriesAndCompletesOneDateWithTokenFencing() {
        assertThat(store.register(YESTERDAY).state()).isEqualTo(RecordWorkState.OPEN);
        RecordDayCloseClaim first = store.claim(YESTERDAY, request(NOW, NOW.plusSeconds(10))).orElseThrow();
        assertThat(store.markRetryableFailure(YESTERDAY, first.token(),
                new RecordWorkFailure(RecordWorkFailureCategory.RETRYABLE, "transient"), NOW.plusSeconds(20))).isTrue();

        PostgresRecordDayCloseStore retry = storeAt(NOW.plusSeconds(20));
        RecordDayCloseClaim second = retry.claim(YESTERDAY, request(NOW.plusSeconds(20), NOW.plusSeconds(30))).orElseThrow();
        assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(retry.markSucceeded(YESTERDAY, first.token(), NOW.plusSeconds(21))).isFalse();
        assertThat(retry.markSucceeded(YESTERDAY, second.token(), NOW.plusSeconds(21))).isTrue();

        assertThat(retry.find(YESTERDAY).orElseThrow().state()).isEqualTo(RecordWorkState.SUCCEEDED);
        assertThat(retry.latestSucceededDate(42, RecordDefinitionVersion.RECORDS_V1.value()))
                .contains(LocalDate.of(2026, 8, 5));
    }

    @Test
    void expiredClaimMayBeTakenOverButTheStaleTokenCannotFinishIt() {
        store.register(YESTERDAY);
        RecordDayCloseClaim first = store.claim(YESTERDAY, request(NOW, NOW.plusSeconds(10))).orElseThrow();
        PostgresRecordDayCloseStore afterExpiry = storeAt(NOW.plusSeconds(10));

        RecordDayCloseClaim second = afterExpiry.claim(YESTERDAY,
                request(NOW.plusSeconds(10), NOW.plusSeconds(20))).orElseThrow();

        assertThat(afterExpiry.markSucceeded(YESTERDAY, first.token(), NOW.plusSeconds(11))).isFalse();
        assertThat(afterExpiry.markSucceeded(YESTERDAY, second.token(), NOW.plusSeconds(11))).isTrue();
        assertThat(afterExpiry.find(YESTERDAY).orElseThrow().attemptCount()).isEqualTo(2);
    }

    private PostgresRecordDayCloseStore storeAt(Instant now) {
        return new PostgresRecordDayCloseStore(jdbc, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static RecordLeaseClaimRequest request(Instant claimed, Instant leaseUntil) {
        return new RecordLeaseClaimRequest(claimed, leaseUntil);
    }
}
