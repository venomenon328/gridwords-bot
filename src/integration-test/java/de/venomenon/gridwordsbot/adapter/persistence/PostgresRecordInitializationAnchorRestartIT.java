package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.record.RecordStateService;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapProjection;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
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
class PostgresRecordInitializationAnchorRestartIT {
    private static final Instant STARTED_AT = Instant.parse("2026-08-05T08:00:00Z");
    private static final String BOOTSTRAP_KEY = "1:records-v1";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresRecordStateStore states;
    private PostgresRecordEventStore events;
    private RecordTransactionRunner transactions;

    @BeforeAll
    void migrate() throws Exception {
        var source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();

        jdbc = new JdbcTemplate(source);
        Clock clock = Clock.fixed(STARTED_AT, ZoneOffset.UTC);
        states = new PostgresRecordStateStore(jdbc, clock);
        events = new PostgresRecordEventStore(jdbc, clock);
        TransactionTemplate template =
                new TransactionTemplate(new DataSourceTransactionManager(source));
        transactions = new RecordTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return template.execute(status -> work.get());
            }
        };
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM record_announcement_message");
        jdbc.update("DELETE FROM record_announcement_event");
        jdbc.update("DELETE FROM record_announcement");
        jdbc.update("DELETE FROM record_event");
        jdbc.update("DELETE FROM record_bootstrap");
        jdbc.update("DELETE FROM record_state");
    }

    @Test
    void canonicalReconciliationReplayKeepsTheOriginalPostgresAuditAnchor() {
        RecordStateKey key = new RecordStateKey(
                1,
                new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
                RecordDefinitionVersion.RECORDS_V1,
                new RecordScope.Personal(1));
        RecordBootstrapProjection.Candidate initialized =
                new RecordBootstrapProjection.Candidate(key, write(1, 99));
        RecordBootstrapProjection.Candidate canonical =
                new RecordBootstrapProjection.Candidate(key, write(3, 10));

        RecordStateService firstProcess = service();
        assertThat(firstProcess.initializeSilently(initialized, BOOTSTRAP_KEY, STARTED_AT))
                .isTrue();
        assertThat(firstProcess.reconcileCanonicalTarget(canonical, BOOTSTRAP_KEY, STARTED_AT))
                .isEqualTo(RecordStateService.RebuildResult.REPLACED);

        RecordStateService restartedProcess = service();
        assertThat(restartedProcess.reconcileCanonicalTarget(canonical, BOOTSTRAP_KEY, STARTED_AT))
                .isEqualTo(RecordStateService.RebuildResult.UNCHANGED);

        assertThat(((RecordSourceReference.GameResult)
                states.find(key).orElseThrow().source()).resultId()).isEqualTo(10);
        String stable = BOOTSTRAP_KEY + ":" + key.definitionKey().value() + ":" + key.scopeKey();
        var anchors = events.findByTriggerKey(1, stable);
        assertThat(anchors).hasSize(1);
        assertThat(((RecordSourceReference.GameResult)
                anchors.get(0).draft().newSource()).resultId()).isEqualTo(99);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_event", Integer.class))
                .isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_announcement", Integer.class))
                .isZero();
    }

    private RecordStateService service() {
        return new RecordStateService(
                states,
                events,
                transactions,
                RecordDefinitionCatalog.recordsV1());
    }

    private static RecordStateWrite write(int attempts, long resultId) {
        return new RecordStateWrite(
                Optional.of(1L),
                new AttemptsDurationRecordValue(attempts, Duration.ofSeconds(50)),
                new RecordSourceReference.GameResult(
                        resultId,
                        0,
                        1,
                        GameType.GRIDWORDS,
                        LocalDate.of(2026, 8, 4)),
                Optional.of(STARTED_AT.minusSeconds(60)),
                false);
    }
}
