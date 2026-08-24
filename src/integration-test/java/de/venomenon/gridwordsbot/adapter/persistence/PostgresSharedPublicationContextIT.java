package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
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
class PostgresSharedPublicationContextIT {
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final LocalDate GAME_DATE = LocalDate.of(2026, 8, 24);
    private static final long PLAYER_ID = 71L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private PostgresPersistenceAdapter adapter;
    private TransactionTemplate transactions;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        adapter = new PostgresPersistenceAdapter(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void oneBothGamesParticipantPersistsSharedCompleteAndPerfectEstablishment() {
        adapter.upsert(new PlayerStore.PlayerUpsert(PLAYER_ID, "Solo", true, false));

        register(7101L, "grid");
        SubmissionStore.StoredSubmission grid = store(7101L, gridWordsSolved());
        assertThat(grid.publicationContext().sharedCompleteEstablished()).isFalse();
        assertThat(grid.publicationContext().sharedPerfectEstablished()).isFalse();

        register(7102L, "quad");
        SubmissionStore.StoredSubmission quad = store(7102L, quadWordsSolved());

        assertThat(quad.publicationContext().personalCompleteEstablished()).isTrue();
        assertThat(quad.publicationContext().personalPerfectEstablished()).isTrue();
        assertThat(quad.publicationContext().sharedCompleteEstablished()).isTrue();
        assertThat(quad.publicationContext().sharedPerfectEstablished()).isTrue();
    }

    private void register(long sourceMessageId, String rawContent) {
        adapter.register(new SubmissionStore.SubmissionRegistration(
                sourceMessageId, 1L, 2L, PLAYER_ID, rawContent, List.of(), NOW));
    }

    private SubmissionStore.StoredSubmission store(long sourceMessageId, GameResultStore.GameResultUpsert result) {
        return transactions.execute(status -> adapter.storeResult(new SubmissionStore.ResultStorage(sourceMessageId, result)));
    }

    private static GameResultStore.GameResultUpsert gridWordsSolved() {
        String white = "⬜".repeat(5);
        String yellow = "🟨".repeat(5);
        String green = "🟩".repeat(5);
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.GRIDWORDS,
                GAME_DATE,
                new ShareOutcome.Solved(3, 6),
                Duration.ofSeconds(45),
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(List.of(white, yellow, green))));
        return new GameResultStore.GameResultUpsert(PLAYER_ID, parsed, "grid", "test");
    }

    private static GameResultStore.GameResultUpsert quadWordsSolved() {
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.QUADWORDS,
                GAME_DATE,
                new ShareOutcome.Solved(4, 9),
                Duration.ofSeconds(90),
                OptionalInt.empty(),
                Optional.empty());
        return new GameResultStore.GameResultUpsert(PLAYER_ID, parsed, "quad", "quadwords-share-v2");
    }
}
