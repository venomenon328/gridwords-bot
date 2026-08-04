package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.domain.excuse.DailyComparisonSnapshot;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOffer;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferMetadata;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOptionSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRevalidation;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTopic;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Stream;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies durable terminal-excuse refresh recovery against the production PostgreSQL adapters. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresCanonicalExcuseRecoveryIT {

    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final long PLAYER_ID = 74001L;
    private static final long CHANNEL_ID = 74002L;
    private static final long CANONICAL_MESSAGE_ID = 74003L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

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
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE player RESTART IDENTITY CASCADE");
    }

    @ParameterizedTest
    @MethodSource("terminalCases")
    void restartRecoversEachDurablyCommittedTerminalExcuseOntoTheExistingCanonicalMessage(
            ExcuseStatus terminalStatus, SubmissionStore.SubmissionState sourceState) {
        Seed seed = seedPublishedResult(terminalStatus.ordinal() + 1, sourceState);
        long resultId = seed.resultId();
        PostgresExcuseStateStore states = stateStore();
        Instant expiresAt = terminalStatus == ExcuseStatus.EXPIRED ? NOW : NOW.plusSeconds(600);
        transaction(() -> states.initializeAvailable(offer(resultId, seed.sourceMessageId(), expiresAt), offerContext(resultId)));
        persistTerminalTransition(states, resultId, terminalStatus);

        assertThat(states.find(resultId).orElseThrow().status()).isEqualTo(terminalStatus);
        assertThat(refreshRequired(resultId)).isTrue();
        assertThat(refreshGeneration(resultId)).isEqualTo(1L);

        PostgresPersistenceAdapter restarted = persistence();
        assertThat(restarted.findCanonicalRefreshCandidates())
                .extracting(candidate -> candidate.submission().sourceMessageId())
                .contains(seed.sourceMessageId());

        CanonicalMessageGateway discord = mock(CanonicalMessageGateway.class);
        CanonicalGridWordsPublicationService recovered = service(restarted, discord);
        transaction(() -> {
            recovered.resumeOpenPublications();
            return null;
        });

        verify(discord).edit(eq(CHANNEL_ID), eq(CANONICAL_MESSAGE_ID), any());
        verify(discord, never()).create(anyLong(), any());
        assertThat(restarted.findById(resultId).orElseThrow().canonicalMessageId())
                .isEqualTo(OptionalLong.of(CANONICAL_MESSAGE_ID));
        assertThat(refreshRequired(resultId)).isFalse();
    }

    @Test
    void supersededAndRetiredResultsNeverReenterRestartRefreshRecovery() {
        long superseded = seedPublishedResult(10, SubmissionStore.SubmissionState.SUPERSEDED).resultId();
        long retired = seedPublishedResult(11, SubmissionStore.SubmissionState.COMPLETED).resultId();
        jdbc.update("UPDATE game_result SET canonical_refresh_required = TRUE, canonical_refresh_generation = 1 "
                + "WHERE id IN (?, ?)", superseded, retired);
        jdbc.update("""
                INSERT INTO canonical_result_retirement (
                    game_result_id, retirement_state, retired_at, created_at, updated_at)
                VALUES (?, 'RETIRED', ?, ?, ?)
                """, retired, Timestamp.from(NOW), Timestamp.from(NOW), Timestamp.from(NOW));

        PostgresPersistenceAdapter restarted = persistence();
        assertThat(restarted.findCanonicalRefreshCandidates())
                .extracting(candidate -> candidate.submission().gameResultId().orElseThrow())
                .doesNotContain(superseded, retired);

        CanonicalMessageGateway discord = mock(CanonicalMessageGateway.class);
        transaction(() -> {
            service(restarted, discord).withRetirementFence(
                    new PostgresChannelMessageRetirementStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC)))
                    .resumeOpenPublications();
            return null;
        });
        verifyNoInteractions(discord);
        assertThat(refreshRequired(retired)).isTrue();
    }

    private static Stream<Arguments> terminalCases() {
        return Stream.of(
                Arguments.of(ExcuseStatus.SELECTED, SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED),
                Arguments.of(ExcuseStatus.DECLINED, SubmissionStore.SubmissionState.COMPLETED),
                Arguments.of(ExcuseStatus.EXPIRED, SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED),
                Arguments.of(ExcuseStatus.INVALIDATED, SubmissionStore.SubmissionState.COMPLETED));
    }

    private Seed seedPublishedResult(int offset, SubmissionStore.SubmissionState state) {
        long playerId = PLAYER_ID + offset;
        long sourceId = 75_000L + offset;
        jdbc.update("""
                INSERT INTO player (discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, 'Recovery player', TRUE, FALSE, TRUE, ?, ?)
                """, playerId, Timestamp.from(NOW), Timestamp.from(NOW));
        long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    normalized_board, raw_share_text, parser_version, canonical_message_id,
                    canonical_refresh_required, canonical_refresh_generation, created_at, updated_at)
                VALUES (?, 'GRIDWORDS', ?, TRUE, 6, 6, 300, ?, 'redacted-fixture', 'gridwords-v1', ?, FALSE, 0, ?, ?)
                RETURNING id
                """, Long.class, playerId, LocalDate.of(2026, 8, 4), "\u2B1C\u2B1C\u2B1C\u2B1C\u2B1C\n".repeat(6).strip(),
                CANONICAL_MESSAGE_ID, Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id, guild_id, channel_id, author_player_id, raw_message_content, processing_state,
                    game_result_id, original_deleted_at, received_at, updated_at)
                VALUES (?, 1, ?, ?, 'redacted-fixture', ?, ?, ?, ?, ?)
                """, sourceId, CHANNEL_ID, playerId, state.name(), resultId,
                state == SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED
                        || state == SubmissionStore.SubmissionState.COMPLETED ? Timestamp.from(NOW) : null,
                Timestamp.from(NOW), Timestamp.from(NOW));
        return new Seed(resultId, sourceId);
    }

    private void persistTerminalTransition(PostgresExcuseStateStore states, long resultId, ExcuseStatus terminalStatus) {
        switch (terminalStatus) {
            case SELECTED -> {
                transaction(() -> states.storeInitialOptions(resultId, 1, options()));
                transaction(() -> states.selectAndRequestCanonicalRefresh(
                        new ExcuseOptionSelection(resultId, 1, ExcuseRound.INITIAL, 1, NOW))).orElseThrow();
            }
            case DECLINED -> transaction(() -> states.declineAndRequestCanonicalRefresh(resultId, 1, NOW)).orElseThrow();
            case EXPIRED -> transaction(() -> states.expireAndRequestCanonicalRefresh(resultId, NOW)).orElseThrow();
            case INVALIDATED -> transaction(() -> states.revalidateAndRequestCanonicalRefresh(new ExcuseRevalidation(
                    resultId, ExcuseRevalidation.Outcome.INVALIDATE, offerContext(resultId)))).orElseThrow();
            default -> throw new IllegalArgumentException("not a terminal excuse status: " + terminalStatus);
        }
    }

    private CanonicalGridWordsPublicationService service(
            PostgresPersistenceAdapter persistence, CanonicalMessageGateway discord) {
        return new CanonicalGridWordsPublicationService(
                persistence, persistence, persistence, discord, Clock.fixed(NOW, ZoneOffset.UTC), BERLIN,
                (at, action) -> { }, ignored -> { }, stateStore());
    }

    private PostgresPersistenceAdapter persistence() {
        return new PostgresPersistenceAdapter(jdbc, Clock.fixed(NOW, ZoneOffset.UTC), BERLIN);
    }

    private PostgresExcuseStateStore stateStore() {
        return new PostgresExcuseStateStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC), BERLIN);
    }

    private ExcuseOffer offer(long resultId, long sourceMessageId, Instant expiresAt) {
        return new ExcuseOffer(resultId, playerIdFor(resultId), GameType.GRIDWORDS,
                new ExcuseOfferMetadata(sourceMessageId, "catalog", "context", 1, NOW.minusSeconds(60), expiresAt));
    }

    private ExcuseOfferContext offerContext(long resultId) {
        return new ExcuseOfferContext(NOW.minusSeconds(60),
                new DailyComparisonSnapshot(GameType.GRIDWORDS, 0, false, java.util.OptionalInt.empty(), Duration.ZERO),
                "a".repeat(64));
    }

    private long playerIdFor(long resultId) {
        return jdbc.queryForObject("SELECT player_id FROM game_result WHERE id = ?", Long.class, resultId);
    }

    private boolean refreshRequired(long resultId) {
        return jdbc.queryForObject("SELECT canonical_refresh_required FROM game_result WHERE id = ?", Boolean.class, resultId);
    }

    private long refreshGeneration(long resultId) {
        return jdbc.queryForObject("SELECT canonical_refresh_generation FROM game_result WHERE id = ?", Long.class, resultId);
    }

    private static List<ExcuseOption> options() {
        return List.of(
                new ExcuseOption(ExcuseRound.INITIAL, 1, "recovery.one", ExcuseStyle.TECHNICAL,
                        ExcuseTopic.GENERAL, "Text eins"),
                new ExcuseOption(ExcuseRound.INITIAL, 2, "recovery.two", ExcuseStyle.TACTICAL,
                        ExcuseTopic.LONG_TERM_PLAN, "Text zwei"),
                new ExcuseOption(ExcuseRound.INITIAL, 3, "recovery.three", ExcuseStyle.LEGAL,
                        ExcuseTopic.RESPONSIBILITY, "Text drei"));
    }

    private <T> T transaction(java.util.function.Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    private record Seed(long resultId, long sourceMessageId) {
    }
}
