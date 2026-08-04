package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.application.excuse.ExcuseResultLifecycle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityThresholds;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOptionSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTemplate;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTopic;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
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
    private static final Instant BEFORE_LATE_SUBMISSION = Instant.parse("2026-08-03T20:00:00Z");
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

    @Test
    void persistsANegativeInitialDecisionForANonQualifyingNewResult() {
        register(903L, BEFORE_LATE_SUBMISSION);

        long resultId = transaction(() -> adapter.storeResult(
                new SubmissionStore.ResultStorage(903L, result(5, Duration.ofSeconds(90)))))
                .gameResultId().orElseThrow();

        assertThat(states.find(resultId).orElseThrow().status()).isEqualTo(ExcuseStatus.NOT_OFFERED);
    }

    @Test
    void replayDoesNotUpgradeANegativeDecisionAfterTheFeatureIsEnabled() {
        PostgresPersistenceAdapter disabledAdapter = new PostgresPersistenceAdapter(
                jdbc, Clock.fixed(NOW, ZoneOffset.UTC), BERLIN, ExcuseResultLifecycle.disabled(
                        states, Clock.fixed(NOW, ZoneOffset.UTC)));
        disabledAdapter.register(new SubmissionStore.SubmissionRegistration(
                904L, 12L, 13L, 7001L, "share", List.of(), NOW));
        SubmissionStore.ResultStorage storage = new SubmissionStore.ResultStorage(904L, result(6, Duration.ofMinutes(5)));
        long resultId = transaction(() -> disabledAdapter.storeResult(storage)).gameResultId().orElseThrow();

        transaction(() -> adapter.storeResult(storage));

        assertThat(states.find(resultId).orElseThrow().status()).isEqualTo(ExcuseStatus.NOT_OFFERED);
    }

    @Test
    void keepsASelectedGeneralExcuseWhenTheCorrectedResultRemainsEligible() {
        long resultId = storeAvailableGridWordsResult(905L, 6, Duration.ofMinutes(5));
        select(resultId, options(
                "general.technical", "Testtext general.technical",
                "general.tactical", "Testtext general.tactical",
                "general.legal", "Testtext general.legal"));

        register(906L);
        transaction(() -> adapter.storeResult(new SubmissionStore.ResultStorage(906L, result(6, Duration.ofMinutes(6)))));

        assertThat(states.find(resultId).orElseThrow().status()).isEqualTo(ExcuseStatus.SELECTED);
    }

    @Test
    void invalidatesASelectedTemplateThatNoLongerMatchesTheCorrectedResult() {
        long resultId = storeAvailableGridWordsResult(907L, 6, Duration.ofMinutes(5));
        select(resultId, options(
                "last-attempt.technical", "Letzter Versuch",
                "general.tactical", "Testtext general.tactical",
                "general.legal", "Testtext general.legal"));

        register(908L);
        transaction(() -> adapter.storeResult(new SubmissionStore.ResultStorage(908L, result(5, Duration.ofMinutes(5)))));

        assertThat(states.find(resultId).orElseThrow().status()).isEqualTo(ExcuseStatus.INVALIDATED);
    }

    @Test
    void boardEnrichmentReplacesTheAvailableContextWithoutCreatingAnotherOffer() {
        register(909L);
        long resultId = transaction(() -> adapter.storeResult(new SubmissionStore.ResultStorage(
                909L, quadWordsResult(Optional.empty())))).gameResultId().orElseThrow();
        transaction(() -> states.storeInitialOptions(resultId, 1, options(
                "general.technical", "Testtext general.technical",
                "general.tactical", "Testtext general.tactical",
                "general.legal", "Testtext general.legal")));

        register(910L);
        transaction(() -> adapter.storeResult(new SubmissionStore.ResultStorage(
                910L, quadWordsResult(Optional.of(quadWordsBoards())))));

        assertThat(states.find(resultId).orElseThrow())
                .extracting(state -> state.status(), state -> state.offer().orElseThrow().contextGeneration())
                .containsExactly(ExcuseStatus.AVAILABLE, 2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_result_excuse_option WHERE game_result_id = ?", Integer.class, resultId))
                .isZero();
    }

    @Test
    void invalidatesASelectedExcuseWhenItsRenderedPlaceholderTextWouldChange() {
        long resultId = storeAvailableGridWordsResult(911L, 6, Duration.ofMinutes(5));
        select(resultId, options(
                "duration.technical", "Dauer 05:00",
                "general.tactical", "Testtext general.tactical",
                "general.legal", "Testtext general.legal"));

        register(912L);
        transaction(() -> adapter.storeResult(new SubmissionStore.ResultStorage(912L, result(6, Duration.ofMinutes(6)))));

        assertThat(states.find(resultId).orElseThrow().status()).isEqualTo(ExcuseStatus.INVALIDATED);
    }

    @Test
    void disabledFeaturePersistsTheNegativeInitialDecision() {
        PostgresPersistenceAdapter disabledAdapter = new PostgresPersistenceAdapter(
                jdbc, Clock.fixed(NOW, ZoneOffset.UTC), BERLIN, ExcuseResultLifecycle.disabled(
                        states, Clock.fixed(NOW, ZoneOffset.UTC)));
        disabledAdapter.register(new SubmissionStore.SubmissionRegistration(
                913L, 12L, 13L, 7001L, "share", List.of(), NOW));

        long resultId = transaction(() -> disabledAdapter.storeResult(
                new SubmissionStore.ResultStorage(913L, result(6, Duration.ofMinutes(5)))))
                .gameResultId().orElseThrow();

        assertThat(states.find(resultId).orElseThrow().status()).isEqualTo(ExcuseStatus.NOT_OFFERED);
    }

    private void register(long sourceMessageId) {
        register(sourceMessageId, NOW);
    }

    private void register(long sourceMessageId, Instant receivedAt) {
        adapter.register(new SubmissionStore.SubmissionRegistration(
                sourceMessageId, 12L, 13L, 7001L, "share", List.of(), receivedAt));
    }

    private long storeAvailableGridWordsResult(long sourceMessageId, int attempts, Duration duration) {
        register(sourceMessageId);
        return transaction(() -> adapter.storeResult(new SubmissionStore.ResultStorage(
                sourceMessageId, result(attempts, duration)))).gameResultId().orElseThrow();
    }

    private void select(long resultId, List<ExcuseOption> options) {
        transaction(() -> states.storeInitialOptions(resultId, 1, options));
        transaction(() -> states.select(new ExcuseOptionSelection(resultId, 1, 1, NOW.plusSeconds(1))));
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

    private static GameResultStore.GameResultUpsert quadWordsResult(Optional<QuadWordsBoards> boards) {
        return new GameResultStore.GameResultUpsert(
                7001L,
                new ParsedGameResult(
                        GameType.QUADWORDS, LocalDate.of(2026, 8, 3), new ShareOutcome.Solved(9, 9),
                        Duration.ofMinutes(8), OptionalInt.empty(), Optional.empty(), boards),
                "share", "quadwords-v2");
    }

    private static QuadWordsBoards quadWordsBoards() {
        QuadWordsBoard board = new QuadWordsBoard(java.util.Collections.nCopies(9, "\u2B1C".repeat(5)));
        return new QuadWordsBoards(board, board, board, board);
    }

    private static List<ExcuseOption> options(
            String firstId, String firstText, String secondId, String secondText, String thirdId, String thirdText) {
        return List.of(
                new ExcuseOption(ExcuseRound.INITIAL, 1, firstId, ExcuseStyle.TECHNICAL,
                        ExcuseTopic.TECHNICAL_FAILURE, firstText),
                new ExcuseOption(ExcuseRound.INITIAL, 2, secondId, ExcuseStyle.TACTICAL,
                        ExcuseTopic.LONG_TERM_PLAN, secondText),
                new ExcuseOption(ExcuseRound.INITIAL, 3, thirdId, ExcuseStyle.LEGAL,
                        ExcuseTopic.RESPONSIBILITY, thirdText));
    }

    private static ExcuseCatalog catalog() {
        return new ExcuseCatalog("test-v1", List.of(
                template("general.technical", ExcuseStyle.TECHNICAL, ExcuseTopic.TECHNICAL_FAILURE),
                template("general.tactical", ExcuseStyle.TACTICAL, ExcuseTopic.LONG_TERM_PLAN),
                template("general.legal", ExcuseStyle.LEGAL, ExcuseTopic.RESPONSIBILITY),
                new ExcuseTemplate("last-attempt.technical", ExcuseStyle.TECHNICAL,
                        EnumSet.of(GameType.GRIDWORDS), ExcuseTopic.LAST_ATTEMPT, 10, 1,
                        java.util.Set.of(de.venomenon.gridwordsbot.domain.excuse.ExcuseReason.GRIDWORDS_LAST_ATTEMPT),
                        java.util.Set.of(), "Letzter Versuch", true),
                new ExcuseTemplate("duration.technical", ExcuseStyle.TECHNICAL,
                        EnumSet.allOf(GameType.class), ExcuseTopic.SLOW_RESULT, 0, 1,
                        java.util.Set.of(), java.util.Set.of(), "Dauer {duration}", true)));
    }

    private static ExcuseTemplate template(String id, ExcuseStyle style, ExcuseTopic topic) {
        return new ExcuseTemplate(id, style, EnumSet.allOf(GameType.class), topic, 0, 1, java.util.Set.of(),
                java.util.Set.of(), "Testtext " + id, true);
    }

    private <T> T transaction(java.util.function.Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }
}
