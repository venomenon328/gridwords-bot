package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.application.excuse.ExcuseResultLifecycle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityThresholds;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTemplate;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTopic;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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
class ExcuseResultLifecycleIntegrationIT {

    private static final Instant NOW = Instant.parse("2026-08-03T21:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private PostgresPersistenceAdapter adapter;
    private PostgresExcuseStateStore states;
    private TransactionTemplate transactions;
    private JdbcTemplate jdbc;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        jdbc = new JdbcTemplate(dataSource);
        states = new PostgresExcuseStateStore(jdbc, clock, BERLIN);
        ExcuseResultLifecycle lifecycle = ExcuseResultLifecycle.enabled(
                states,
                (playerId, gameType, gameDate, participants) -> List.of(),
                new ExcuseEligibilityPolicy(ExcuseEligibilityThresholds.defaults()),
                catalog(),
                clock,
                Duration.ofMinutes(15));
        adapter = new PostgresPersistenceAdapter(jdbc, clock, BERLIN, lifecycle);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE player RESTART IDENTITY CASCADE");
        adapter.upsert(new PlayerStore.PlayerUpsert(7001L, "Tobias", true, false));
    }

    @Test
    void storesTheFirstOfferAndRevalidatesTheSameStateAtomicallyWithCorrections() {
        register(901L);
        SubmissionStore.StoredSubmission first = transaction(() -> adapter.storeResult(
                new SubmissionStore.ResultStorage(901L, result(6, Duration.ofMinutes(5)))));
        long resultId = first.gameResultId().orElseThrow();
        assertThat(states.find(resultId).orElseThrow())
                .extracting(state -> state.status(), state -> state.offerContext().orElseThrow().originalReceivedAt())
                .containsExactly(ExcuseStatus.AVAILABLE, NOW);

        register(902L);
        assertThatThrownBy(() -> transaction(() -> {
            adapter.storeResult(new SubmissionStore.ResultStorage(902L, result(5, Duration.ofSeconds(90))));
            throw new IllegalStateException("rollback correction");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(adapter.findById(resultId).orElseThrow().parsedResult().duration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(states.find(resultId).orElseThrow().status()).isEqualTo(ExcuseStatus.AVAILABLE);

        transaction(() -> adapter.storeResult(new SubmissionStore.ResultStorage(902L, result(5, Duration.ofSeconds(90)))));
        assertThat(states.find(resultId).orElseThrow().status()).isEqualTo(ExcuseStatus.INVALIDATED);
    }

    private void register(long sourceMessageId) {
        adapter.register(new SubmissionStore.SubmissionRegistration(
                sourceMessageId, 12L, 13L, 7001L, "share", List.of(), NOW));
    }

    private static GameResultStore.GameResultUpsert result(int attempts, Duration duration) {
        return new GameResultStore.GameResultUpsert(
                7001L,
                new ParsedGameResult(
                        GameType.GRIDWORDS, LocalDate.of(2026, 8, 3), new ShareOutcome.Solved(attempts, 6), duration,
                        OptionalInt.empty(), Optional.of(new NormalizedBoard(
                                java.util.Collections.nCopies(attempts, "\u2B1C".repeat(5)))), Optional.empty()),
                "share", "gridwords-v1");
    }

    private static ExcuseCatalog catalog() {
        return new ExcuseCatalog("test-v1", List.of(
                template("general.technical", ExcuseStyle.TECHNICAL, ExcuseTopic.TECHNICAL_FAILURE),
                template("general.tactical", ExcuseStyle.TACTICAL, ExcuseTopic.LONG_TERM_PLAN),
                template("general.legal", ExcuseStyle.LEGAL, ExcuseTopic.RESPONSIBILITY)));
    }

    private static ExcuseTemplate template(String id, ExcuseStyle style, ExcuseTopic topic) {
        return new ExcuseTemplate(id, style, EnumSet.allOf(GameType.class), topic, 0, 1, java.util.Set.of(),
                java.util.Set.of(), "Testtext " + id, true);
    }

    private <T> T transaction(java.util.function.Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }
}
