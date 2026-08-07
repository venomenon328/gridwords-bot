package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseOffer;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferMetadata;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOptionSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRevalidation;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTopic;
import de.venomenon.gridwordsbot.domain.excuse.DailyComparisonSnapshot;
import de.venomenon.gridwordsbot.domain.model.GameType;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresExcuseStateStoreIT {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");
    private static final long PLAYER_ID = 42001L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresExcuseStateStore store;
    private TransactionTemplate transactions;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        store = new PostgresExcuseStateStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC), BERLIN);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE player RESTART IDENTITY CASCADE");
        insertPlayer(PLAYER_ID);
    }

    @Test
    void databaseConstrainsOneStateStatusesOfferTimestampsAndSelectionSnapshots() {
        long notOffered = result(PLAYER_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 1));
        transaction(() -> store.initializeNotOffered(notOffered));
        assertThat(store.find(notOffered).orElseThrow().status()).isEqualTo(ExcuseStatus.NOT_OFFERED);

        long available = availableResult(GameType.QUADWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        assertThat(store.find(available).orElseThrow().status()).isEqualTo(ExcuseStatus.AVAILABLE);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO game_result_excuse (
                    game_result_id, status, context_generation, reroll_used, created_at, updated_at)
                VALUES (?, 'AVAILABLE', 1, FALSE, ?, ?)
                """, notOffered, Timestamp.from(NOW), Timestamp.from(NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE game_result_excuse SET status = 'SELECTED' WHERE game_result_id = ?
                """, available)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE game_result_excuse SET expires_at = offered_at WHERE game_result_id = ?
                """, available)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE game_result_excuse SET status = 'UNKNOWN' WHERE game_result_id = ?
                """, available)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void selectUsesOnlyPersistedCurrentOptionsAndCopiesTheirSnapshots() {
        long resultId = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        assertThat(transaction(() -> store.select(new ExcuseOptionSelection(
                resultId, 1, ExcuseRound.INITIAL, 1, NOW.plusSeconds(1)))))
                .isEmpty();

        transaction(() -> store.storeInitialOptions(resultId, 1, options(ExcuseRound.INITIAL, "initial")));
        ExcuseState selected = transaction(() -> store.select(
                new ExcuseOptionSelection(resultId, 1, ExcuseRound.INITIAL, 2, NOW.plusSeconds(1)))).orElseThrow();

        assertThat(selected.status()).isEqualTo(ExcuseStatus.SELECTED);
        assertThat(selected.selection().orElseThrow())
                .extracting(snapshot -> snapshot.round(), snapshot -> snapshot.position(), snapshot -> snapshot.templateId(),
                        snapshot -> snapshot.style(), snapshot -> snapshot.topic(), snapshot -> snapshot.renderedText())
                .containsExactly(ExcuseRound.INITIAL, 2, "initial.2", ExcuseStyle.TACTICAL,
                        ExcuseTopic.LONG_TERM_PLAN, "initial text 2");
        assertThat(transaction(() -> store.decline(resultId, 1, NOW.plusSeconds(2)))).isEmpty();
    }

    @Test
    void selectionAndDeclinePersistTheCanonicalRefreshRequestInTheSameTransaction() {
        long selectedResult = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        transaction(() -> store.storeInitialOptions(selectedResult, 1, options(ExcuseRound.INITIAL, "selected")));

        assertThat(transaction(() -> store.selectAndRequestCanonicalRefresh(
                new ExcuseOptionSelection(
                        selectedResult, 1, ExcuseRound.INITIAL, 1, NOW.plusSeconds(1))))).isPresent();
        assertThat(refreshRequired(selectedResult)).isTrue();
        assertThat(refreshGeneration(selectedResult)).isEqualTo(1L);
        assertThat(gameResultVersion(selectedResult)).isZero();

        insertPlayer(PLAYER_ID + 9);
        long declinedResult = availableResult(PLAYER_ID + 9, GameType.QUADWORDS, LocalDate.of(2026, 8, 2), NOW, NOW.plusSeconds(600));
        assertThat(transaction(() -> store.declineAndRequestCanonicalRefresh(
                declinedResult, 1, NOW.plusSeconds(1)))).isPresent();
        assertThat(refreshRequired(declinedResult)).isTrue();
        assertThat(refreshGeneration(declinedResult)).isEqualTo(1L);
        assertThat(gameResultVersion(declinedResult)).isZero();
    }

    @Test
    void expirationIsAtomicAtTheBoundaryAndConcurrentWorkersCreateOneRefreshGeneration() throws Exception {
        long resultId = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW.minusSeconds(60), NOW);

        assertThat(transaction(() -> store.expireAndRequestCanonicalRefresh(resultId, NOW.minusSeconds(1)))).isEmpty();
        assertThat(store.find(resultId).orElseThrow().status()).isEqualTo(ExcuseStatus.AVAILABLE);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Optional<ExcuseState>> outcomes = executor.invokeAll(List.<Callable<Optional<ExcuseState>>>of(
                    () -> transaction(() -> store.expireAndRequestCanonicalRefresh(resultId, NOW)),
                    () -> transaction(() -> store.expireAndRequestCanonicalRefresh(resultId, NOW))))
                    .stream().map(this::result).toList();
            assertThat(outcomes).filteredOn(Optional::isPresent).hasSize(1);
        }

        assertThat(store.find(resultId).orElseThrow().status()).isEqualTo(ExcuseStatus.EXPIRED);
        assertThat(refreshRequired(resultId)).isTrue();
        assertThat(refreshGeneration(resultId)).isEqualTo(1L);
    }

    @Test
    void roundTripsFrozenOfferFactsAndReplacesOnlyTheCurrentAvailableContext() {
        long resultId = result(PLAYER_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 1));
        source(resultId, PLAYER_ID);
        ExcuseOfferContext initial = new ExcuseOfferContext(
                NOW.minusSeconds(90),
                new DailyComparisonSnapshot(GameType.GRIDWORDS, 2, true, java.util.OptionalInt.of(4), Duration.ofMinutes(3)),
                "0".repeat(64));
        transaction(() -> store.initializeAvailable(
                offer(resultId, PLAYER_ID, GameType.GRIDWORDS, NOW, NOW.plusSeconds(600)), initial));
        transaction(() -> store.storeInitialOptions(resultId, 1, options(ExcuseRound.INITIAL, "initial")));

        ExcuseState revalidated = transaction(() -> store.revalidateAndRequestCanonicalRefresh(new ExcuseRevalidation(
                resultId,
                ExcuseRevalidation.Outcome.REPLACE_AVAILABLE_CONTEXT,
                new ExcuseOfferContext(
                        NOW.minusSeconds(90),
                        new DailyComparisonSnapshot(
                                GameType.GRIDWORDS, 2, true, java.util.OptionalInt.of(4), Duration.ofMinutes(3)),
                        "a".repeat(64))))).orElseThrow();

        assertThat(revalidated.offerContext()).contains(new ExcuseOfferContext(
                NOW.minusSeconds(90),
                new DailyComparisonSnapshot(GameType.GRIDWORDS, 2, true, java.util.OptionalInt.of(4), Duration.ofMinutes(3)),
                "a".repeat(64)));
        assertThat(revalidated.offer().orElseThrow().contextGeneration()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_result_excuse_option WHERE game_result_id = ?", Integer.class,
                resultId)).isZero();
        assertThat(refreshRequired(resultId)).isTrue();
        assertThat(refreshGeneration(resultId)).isEqualTo(1L);
    }

    @Test
    void optionConstraintsPreventInvalidPositionsAndDuplicateTemplatesAcrossTheWholeWorkflow() {
        long resultId = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        transaction(() -> store.storeInitialOptions(resultId, 1, options(ExcuseRound.INITIAL, "initial")));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO game_result_excuse_option (
                    game_result_id, context_generation, round, position, template_id, style, topic, rendered_text, created_at)
                VALUES (?, 1, 'STYLE_REROLL', 0, 'invalid.position', 'LEGAL', 'GENERAL', 'text', ?)
                """, resultId, Timestamp.from(NOW))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO game_result_excuse_option (
                    game_result_id, context_generation, round, position, template_id, style, topic, rendered_text, created_at)
                VALUES (?, 1, 'STYLE_REROLL', 1, 'initial.1', 'LEGAL', 'GENERAL', 'text', ?)
                """, resultId, Timestamp.from(NOW))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO game_result_excuse_option (
                    game_result_id, context_generation, round, position, template_id, style, topic, rendered_text, created_at)
                VALUES (?, 1, 'STYLE_REROLL', 1, 'unknown.topic', 'LEGAL', 'OTHER', 'text', ?)
                """, resultId, Timestamp.from(NOW))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storingThreeOptionsIsAtomicWhenTheDatabaseRejectsOneOfThem() {
        long resultId = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        jdbc.update("""
                INSERT INTO game_result_excuse_option (
                    game_result_id, context_generation, round, position, template_id, style, topic, rendered_text, created_at)
                VALUES (?, 1, 'STYLE_REROLL', 1, 'initial.3', 'LEGAL', 'GENERAL', 'previous', ?)
                """, resultId, Timestamp.from(NOW));

        assertThatThrownBy(() -> transaction(() -> store.storeInitialOptions(
                resultId, 1, options(ExcuseRound.INITIAL, "initial"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM game_result_excuse_option
                WHERE game_result_id = ? AND round = 'INITIAL'
                """, Integer.class, resultId)).isZero();
    }

    @Test
    void concurrentInitialOpensCreateExactlyOnePersistedRoundAndThenReuseIt() throws Exception {
        long resultId = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        ExcuseSelection created = new ExcuseSelection(ExcuseRound.INITIAL, options(ExcuseRound.INITIAL, "created"));
        AtomicInteger factoryCalls = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Optional<ExcuseSelection>> outcomes = executor.invokeAll(List.<Callable<Optional<ExcuseSelection>>>of(
                    () -> transaction(() -> store.loadOrCreateInitialOptions(resultId, 1, () -> {
                        factoryCalls.incrementAndGet();
                        return created;
                    })),
                    () -> transaction(() -> store.loadOrCreateInitialOptions(resultId, 1, () -> {
                        factoryCalls.incrementAndGet();
                        return created;
                    }))))
                    .stream().map(this::result).toList();

            assertThat(outcomes).containsOnly(Optional.of(created));
        }

        assertThat(factoryCalls).hasValue(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM game_result_excuse_option
                WHERE game_result_id = ? AND context_generation = 1 AND round = 'INITIAL'
                """, Integer.class, resultId)).isEqualTo(3);
        PostgresExcuseStateStore restarted = new PostgresExcuseStateStore(
                jdbc, Clock.fixed(NOW, ZoneOffset.UTC), BERLIN);
        assertThat(transaction(() -> restarted.loadOrCreateInitialOptions(resultId, 1,
                () -> { throw new AssertionError("existing initial options must be reused"); }))).contains(created);
    }

    @Test
    void rerollIsConsumedOnlyWithItsCompleteOptionRoundAndCannotBeUsedTwice() {
        long resultId = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        transaction(() -> store.storeInitialOptions(resultId, 1, options(ExcuseRound.INITIAL, "initial")));

        ExcuseState rerolled = transaction(() -> store.storeStyleRerollOptions(
                resultId, 1, options(ExcuseRound.STYLE_REROLL, "reroll"))).orElseThrow();
        assertThat(rerolled.rerollUsed()).isTrue();
        assertThat(transaction(() -> store.storeStyleRerollOptions(
                resultId, 1, options(ExcuseRound.STYLE_REROLL, "second")))).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM game_result_excuse_option
                WHERE game_result_id = ? AND round = 'STYLE_REROLL'
                """, Integer.class, resultId)).isEqualTo(3);

        ExcuseState selected = transaction(() -> store.select(
                new ExcuseOptionSelection(
                        resultId, 1, ExcuseRound.STYLE_REROLL, 1, NOW.plusSeconds(1)))).orElseThrow();
        assertThat(selected.selection().orElseThrow().templateId()).isEqualTo("reroll.1");
    }

    @Test
    void reopenAfterRestartUsesOnlyThePersistedStyleRerollRound() {
        long resultId = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        transaction(() -> store.storeInitialOptions(resultId, 1, options(ExcuseRound.INITIAL, "initial")));
        transaction(() -> store.storeStyleRerollOptions(resultId, 1, options(ExcuseRound.STYLE_REROLL, "reroll")));

        PostgresExcuseStateStore restarted = new PostgresExcuseStateStore(
                jdbc, Clock.fixed(NOW, ZoneOffset.UTC), BERLIN);

        assertThat(transaction(() -> restarted.findActiveOptions(resultId, 1, ExcuseRound.STYLE_REROLL)))
                .extracting(ExcuseOption::round, ExcuseOption::templateId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ExcuseRound.STYLE_REROLL, "reroll.1"),
                        org.assertj.core.groups.Tuple.tuple(ExcuseRound.STYLE_REROLL, "reroll.2"),
                        org.assertj.core.groups.Tuple.tuple(ExcuseRound.STYLE_REROLL, "reroll.3"));
        assertThat(transaction(() -> restarted.findActiveOptions(resultId, 1, ExcuseRound.INITIAL))).hasSize(3);
    }

    @Test
    void staleInitialPickLosesToAConcurrentStyleRerollUnderTheDatabaseRowLock() throws Exception {
        long resultId = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        transaction(() -> store.storeInitialOptions(resultId, 1, options(ExcuseRound.INITIAL, "initial")));
        CountDownLatch rerollLocked = new CountDownLatch(1);
        CountDownLatch releaseReroll = new CountDownLatch(1);
        ExcuseSelection reroll = new ExcuseSelection(ExcuseRound.STYLE_REROLL, options(ExcuseRound.STYLE_REROLL, "reroll"));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<Optional<ExcuseSelection>> rerollOutcome = executor.submit(() -> transaction(() ->
                    store.loadOrCreateStyleRerollOptions(resultId, 1, () -> {
                        rerollLocked.countDown();
                        await(releaseReroll);
                        return reroll;
                    })));
            assertThat(rerollLocked.await(5, TimeUnit.SECONDS)).isTrue();
            java.util.concurrent.Future<Optional<ExcuseState>> staleInitialPick = executor.submit(() -> transaction(() ->
                    store.selectAndRequestCanonicalRefresh(new ExcuseOptionSelection(
                            resultId, 1, ExcuseRound.INITIAL, 1, NOW.plusSeconds(1)))));

            releaseReroll.countDown();

            assertThat(result(rerollOutcome)).contains(reroll);
            assertThat(result(staleInitialPick)).isEmpty();
        }

        assertThat(store.find(resultId).orElseThrow()).satisfies(state -> {
            assertThat(state.status()).isEqualTo(ExcuseStatus.AVAILABLE);
            assertThat(state.rerollUsed()).isTrue();
        });
        assertThat(refreshRequired(resultId)).isFalse();
        assertThat(refreshGeneration(resultId)).isZero();
    }

    @Test
    void concurrentSelectionAndDeclineYieldExactlyOneTerminalDecision() throws Exception {
        long resultId = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        transaction(() -> store.storeInitialOptions(resultId, 1, options(ExcuseRound.INITIAL, "initial")));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Optional<ExcuseState>> outcomes = executor.invokeAll(List.<Callable<Optional<ExcuseState>>>of(
                    () -> transaction(() -> store.select(new ExcuseOptionSelection(
                            resultId, 1, ExcuseRound.INITIAL, 1, NOW.plusSeconds(1)))),
                    () -> transaction(() -> store.decline(resultId, 1, NOW.plusSeconds(1)))))
                    .stream().map(this::result).toList();
            assertThat(outcomes).filteredOn(Optional::isPresent).hasSize(1);
        }
        assertThat(store.find(resultId).orElseThrow().status())
                .isIn(ExcuseStatus.SELECTED, ExcuseStatus.DECLINED);
    }

    @Test
    void concurrentTerminalActionsCreateAtMostOneCanonicalRefreshGeneration() throws Exception {
        long resultId = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW, NOW.plusSeconds(600));
        transaction(() -> store.storeInitialOptions(resultId, 1, options(ExcuseRound.INITIAL, "initial")));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Optional<ExcuseState>> outcomes = executor.invokeAll(List.<Callable<Optional<ExcuseState>>>of(
                    () -> transaction(() -> store.selectAndRequestCanonicalRefresh(
                            new ExcuseOptionSelection(
                                    resultId, 1, ExcuseRound.INITIAL, 1, NOW.plusSeconds(1)))),
                    () -> transaction(() -> store.declineAndRequestCanonicalRefresh(resultId, 1, NOW.plusSeconds(1)))))
                    .stream().map(this::result).toList();
            assertThat(outcomes).filteredOn(Optional::isPresent).hasSize(1);
        }
        assertThat(refreshGeneration(resultId)).isEqualTo(1L);
        assertThat(refreshRequired(resultId)).isTrue();
    }

    @Test
    void threeDayCooldownUsesBerlinCalendarDaysAndSeparatesGames() {
        Instant monday = Instant.parse("2026-08-03T10:00:00Z");
        long gridMonday = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), monday, monday.plusSeconds(600));
        long gridWednesday = result(PLAYER_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 2));
        source(gridWednesday, PLAYER_ID);
        long gridThursday = result(PLAYER_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 3));
        source(gridThursday, PLAYER_ID);
        long quadMonday = result(PLAYER_ID, GameType.QUADWORDS, LocalDate.of(2026, 8, 1));
        source(quadMonday, PLAYER_ID);

        assertThat(gridMonday).isPositive();
        assertThat(transaction(() -> store.initializeAvailable(offer(
                gridWednesday, PLAYER_ID, GameType.GRIDWORDS, monday.plusSeconds(2 * 86_400), monday.plusSeconds(2 * 86_400 + 600)))))
                .isEmpty();
        assertThat(transaction(() -> store.initializeAvailable(offer(
                gridThursday, PLAYER_ID, GameType.GRIDWORDS, monday.plusSeconds(3 * 86_400), monday.plusSeconds(3 * 86_400 + 600)))))
                .isPresent();
        assertThat(transaction(() -> store.initializeAvailable(offer(
                quadMonday, PLAYER_ID, GameType.QUADWORDS, monday.plusSeconds(60), monday.plusSeconds(660)))))
                .isPresent();
    }

    @Test
    void concurrentOffersForTheSamePlayerAndGameLeaveOnlyOneCooldownConsumer() throws Exception {
        long first = result(PLAYER_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 1));
        long second = result(PLAYER_ID, GameType.GRIDWORDS, LocalDate.of(2026, 8, 2));
        source(first, PLAYER_ID);
        source(second, PLAYER_ID);
        ExcuseOffer firstOffer = offer(first, PLAYER_ID, GameType.GRIDWORDS, NOW, NOW.plusSeconds(600));
        ExcuseOffer secondOffer = offer(second, PLAYER_ID, GameType.GRIDWORDS, NOW, NOW.plusSeconds(600));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Optional<ExcuseState>> outcomes = executor.invokeAll(List.<Callable<Optional<ExcuseState>>>of(
                    () -> transaction(() -> store.initializeAvailable(firstOffer)),
                    () -> transaction(() -> store.initializeAvailable(secondOffer))))
                    .stream().map(this::result).toList();
            assertThat(outcomes).filteredOn(Optional::isPresent).hasSize(1);
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM game_result_excuse excuse
                JOIN game_result result ON result.id = excuse.game_result_id
                WHERE result.player_id = ? AND result.game_type = 'GRIDWORDS' AND excuse.offered_at IS NOT NULL
                """, Integer.class, PLAYER_ID)).isEqualTo(1);
    }

    @Test
    void selectedHistorySpansGamesButExcludesInvalidatedSelections() {
        long grid = selectedResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), "grid");
        long quad = selectedResult(GameType.QUADWORDS, LocalDate.of(2026, 8, 2), "quad");

        assertThat(store.findRecentSelections(PLAYER_ID, 10))
                .extracting(entry -> entry.templateId()).containsExactly("quad.1", "grid.1");
        jdbc.update("UPDATE game_result_excuse SET status = 'INVALIDATED' WHERE game_result_id = ?", grid);
        assertThat(store.findRecentSelections(PLAYER_ID, 10))
                .extracting(entry -> entry.templateId()).containsExactly("quad.1");
    }

    @Test
    void selectedHistoryIsCappedAtTenEntries() {
        for (int index = 0; index < 11; index++) {
            Instant offeredAt = NOW.plusSeconds(index * 3L * 86_400L);
            selectedResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1).plusDays(index), "history" + index, offeredAt);
        }

        assertThat(store.findRecentSelections(PLAYER_ID, 11)).hasSize(10);
    }

    @Test
    void dueExpiryQueryIsOrderedAndBoundedWithoutChangingState() {
        long first = availableResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 1), NOW.minusSeconds(120), NOW.minusSeconds(60));
        insertPlayer(PLAYER_ID + 1);
        insertPlayer(PLAYER_ID + 2);
        long second = availableResult(PLAYER_ID + 1, GameType.QUADWORDS, LocalDate.of(2026, 8, 2),
                NOW.minusSeconds(60), NOW.minusSeconds(1));
        long future = availableResult(PLAYER_ID + 2, GameType.GRIDWORDS, LocalDate.of(2026, 8, 3),
                NOW, NOW.plusSeconds(60));

        assertThat(store.findDueExpirations(NOW, 1)).extracting(ExcuseState::gameResultId).containsExactly(first);
        assertThat(store.findDueExpirations(NOW, 10)).extracting(ExcuseState::gameResultId).containsExactly(first, second);
        assertThat(store.find(future).orElseThrow().status()).isEqualTo(ExcuseStatus.AVAILABLE);
    }

    private long selectedResult(GameType gameType, LocalDate date, String prefix) {
        return selectedResult(gameType, date, prefix, NOW);
    }

    private long selectedResult(GameType gameType, LocalDate date, String prefix, Instant offeredAt) {
        long resultId = availableResult(gameType, date, offeredAt, offeredAt.plusSeconds(600));
        transaction(() -> store.storeInitialOptions(resultId, 1, options(ExcuseRound.INITIAL, prefix)));
        return transaction(() -> store.select(new ExcuseOptionSelection(
                resultId, 1, ExcuseRound.INITIAL, 1, offeredAt.plusSeconds(1))))
                .orElseThrow().gameResultId();
    }

    private long availableResult(GameType gameType, LocalDate date, Instant offeredAt, Instant expiresAt) {
        return availableResult(PLAYER_ID, gameType, date, offeredAt, expiresAt);
    }

    private long availableResult(long playerId, GameType gameType, LocalDate date, Instant offeredAt, Instant expiresAt) {
        long resultId = result(playerId, gameType, date);
        source(resultId, playerId);
        return transaction(() -> store.initializeAvailable(offer(resultId, playerId, gameType, offeredAt, expiresAt)))
                .orElseThrow().gameResultId();
    }

    private ExcuseOffer offer(long resultId, long playerId, GameType gameType, Instant offeredAt, Instant expiresAt) {
        return new ExcuseOffer(resultId, playerId, gameType,
                new ExcuseOfferMetadata(resultId, "catalog-v1", "context-v1", 1, offeredAt, expiresAt));
    }

    private long result(long playerId, GameType gameType, LocalDate date) {
        return jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    normalized_board, raw_share_text, parser_version, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, 4, ?, 90, ?, 'share', 'v1', ?, ?)
                RETURNING id
                """, Long.class, playerId, gameType.name(), date,
                gameType == GameType.GRIDWORDS ? 6 : 9,
                gameType == GameType.GRIDWORDS ? "board" : null,
                Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private void source(long resultId, long playerId) {
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                    processing_state, received_at, updated_at)
                VALUES (?, 1, 1, ?, 'share', 'RESULT_STORED', ?, ?)
                """, resultId, playerId, Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private void insertPlayer(long playerId) {
        jdbc.update("""
                INSERT INTO player (discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, 'Player', TRUE, FALSE, TRUE, ?, ?)
                """, playerId, Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private boolean refreshRequired(long gameResultId) {
        return jdbc.queryForObject("SELECT canonical_refresh_required FROM game_result WHERE id = ?", Boolean.class,
                gameResultId);
    }

    private long refreshGeneration(long gameResultId) {
        return jdbc.queryForObject("SELECT canonical_refresh_generation FROM game_result WHERE id = ?", Long.class,
                gameResultId);
    }

    private long gameResultVersion(long gameResultId) {
        return jdbc.queryForObject("SELECT version FROM game_result WHERE id = ?", Long.class, gameResultId);
    }

    private static List<ExcuseOption> options(ExcuseRound round, String prefix) {
        return List.of(
                new ExcuseOption(round, 1, prefix + ".1", ExcuseStyle.TECHNICAL, ExcuseTopic.GENERAL, prefix + " text 1"),
                new ExcuseOption(round, 2, prefix + ".2", ExcuseStyle.TACTICAL, ExcuseTopic.LONG_TERM_PLAN, prefix + " text 2"),
                new ExcuseOption(round, 3, prefix + ".3", ExcuseStyle.LEGAL, ExcuseTopic.RESPONSIBILITY, prefix + " text 3"));
    }

    private <T> T transaction(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    private <T> T result(java.util.concurrent.Future<T> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
