package de.venomenon.gridwordsbot.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class DynamicPlayerPostgresPersistenceAdapterIT {
    private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");
    private static final LocalDate GAME_DATE = LocalDate.of(2026, 7, 29);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private DynamicPlayerPostgresPersistenceAdapter adapter;
    private JdbcTemplate jdbc;
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
        adapter = new DynamicPlayerPostgresPersistenceAdapter(
                jdbc, Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Berlin"));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void synchronizesDisplayNameAndAdministratorWithoutChangingReminderOrParticipation() {
        long playerId = 20_001L;
        adapter.setReminderOptIn(new PlayerStore.ProfileUpdate(playerId, "Old name", false), true);

        PlayerStore.StoredPlayer administrator = adapter.synchronizeProfile(
                new PlayerStore.ProfileUpdate(playerId, "Server name", true));

        assertEquals("Server name", administrator.displayName());
        assertTrue(administrator.administrator());
        assertTrue(administrator.reminderOptIn());
        assertFalse(administrator.active());
        assertTrue(adapter.findParticipationPeriods().stream().noneMatch(period -> period.playerId() == playerId));

        PlayerStore.StoredPlayer regular = adapter.synchronizeProfile(
                new PlayerStore.ProfileUpdate(playerId, "Renamed again", false));
        assertEquals("Renamed again", regular.displayName());
        assertFalse(regular.administrator());
        assertTrue(regular.reminderOptIn());
        assertFalse(regular.active());
    }

    @Test
    void rollsBackProfilePeriodAndResultTogetherWhenValidResultStorageFails() {
        long playerId = 20_002L;
        long sourceMessageId = 30_002L;
        register(sourceMessageId, playerId);

        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            adapter.storeResult(storage(sourceMessageId, playerId, "rollback"));
            throw new IllegalStateException("force rollback");
        }));

        assertTrue(adapter.findByDiscordUserId(playerId).isEmpty());
        assertTrue(adapter.findParticipationPeriods().stream().noneMatch(period -> period.playerId() == playerId));
        assertTrue(adapter.find(playerId, GameType.GRIDWORDS, GAME_DATE).isEmpty());
        assertEquals(SubmissionStore.SubmissionState.RECEIVED,
                adapter.findBySourceMessageId(sourceMessageId).orElseThrow().state());
    }

    @Test
    void serializesConcurrentFirstSubmissionsWithoutDeadlockOrOverlappingPeriods() throws Exception {
        long firstPlayer = 20_003L;
        long secondPlayer = 20_004L;
        long firstSource = 30_003L;
        long secondSource = 30_004L;
        register(firstSource, firstPlayer);
        register(secondSource, secondPlayer);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.execute(
                    status -> adapter.storeResult(storage(firstSource, firstPlayer, "first"))));
            var second = executor.submit(() -> transactions.execute(
                    status -> adapter.storeResult(storage(secondSource, secondPlayer, "second"))));

            assertEquals(SubmissionStore.SubmissionState.RESULT_STORED,
                    first.get(10, TimeUnit.SECONDS).state());
            assertEquals(SubmissionStore.SubmissionState.RESULT_STORED,
                    second.get(10, TimeUnit.SECONDS).state());
        }

        assertTrue(adapter.findByDiscordUserId(firstPlayer).orElseThrow().active());
        assertTrue(adapter.findByDiscordUserId(secondPlayer).orElseThrow().active());
        assertEquals(1, adapter.findParticipationPeriods().stream()
                .filter(period -> period.playerId() == firstPlayer).count());
        assertEquals(1, adapter.findParticipationPeriods().stream()
                .filter(period -> period.playerId() == secondPlayer).count());
        assertTrue(adapter.find(firstPlayer, GameType.GRIDWORDS, GAME_DATE).isPresent());
        assertTrue(adapter.find(secondPlayer, GameType.GRIDWORDS, GAME_DATE).isPresent());
    }

    private void register(long sourceMessageId, long playerId) {
        adapter.register(new SubmissionStore.SubmissionRegistration(
                sourceMessageId,
                200L,
                300L,
                playerId,
                "share " + sourceMessageId,
                List.of(),
                NOW));
    }

    private SubmissionStore.ResultStorage storage(long sourceMessageId, long playerId, String text) {
        return new SubmissionStore.ResultStorage(
                sourceMessageId,
                result(playerId, text),
                new PlayerStore.ParticipationChange(
                        new PlayerStore.ProfileUpdate(playerId, "Player " + playerId, playerId == 20_003L),
                        GAME_DATE));
    }

    private GameResultStore.GameResultUpsert result(long playerId, String text) {
        String white = new String(Character.toChars(0x2B1C)).repeat(5);
        String yellow = new String(Character.toChars(0x1F7E8)).repeat(5);
        String green = new String(Character.toChars(0x1F7E9)).repeat(5);
        ParsedGameResult parsed = new ParsedGameResult(
                GameType.GRIDWORDS,
                GAME_DATE,
                new ShareOutcome.Solved(3, 6),
                Duration.ofSeconds(42),
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(List.of(white, yellow, green))));
        return new GameResultStore.GameResultUpsert(playerId, parsed, text, "dynamic-player-it");
    }
}
