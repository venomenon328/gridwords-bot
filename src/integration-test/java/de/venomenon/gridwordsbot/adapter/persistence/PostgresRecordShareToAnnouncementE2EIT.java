package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.application.canonical.GridWordsSourceDeletionService;
import de.venomenon.gridwordsbot.application.record.RecordAnnouncementDeliveryCoordinator;
import de.venomenon.gridwordsbot.application.record.RecordAnnouncementRenderer;
import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator;
import de.venomenon.gridwordsbot.application.record.RecordBootstrapReadService;
import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationCoordinator;
import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationProcessor;
import de.venomenon.gridwordsbot.application.record.RecordStateService;
import de.venomenon.gridwordsbot.application.record.RenderedRecordAnnouncementPage;
import de.venomenon.gridwordsbot.application.submission.ProcessSharedResultService;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementMessageGateway;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

/** Full production path from a valid share to a delivered record announcement, with only Discord faked. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresRecordShareToAnnouncementE2EIT {
    private static final long GUILD_ID = 10L;
    private static final long CHANNEL_ID = 20L;
    private static final long PLAYER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private Clock clock;
    private RecordDefinitionCatalog catalog;
    private PostgresPersistenceAdapter persistence;
    private PostgresRecordLiveEvaluationStore work;
    private PostgresRecordStateStore states;
    private PostgresRecordEventStore events;
    private PostgresRecordAnnouncementStore announcements;
    private PostgresRecordBootstrapStore bootstraps;
    private RecordTransactionRunner transactions;

    @BeforeAll
    void migrate() throws Exception {
        source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
        clock = Clock.fixed(NOW, BERLIN);
        catalog = RecordDefinitionCatalog.recordsV1();
        persistence = new PostgresPersistenceAdapter(jdbc, clock, BERLIN);
        work = new PostgresRecordLiveEvaluationStore(jdbc, clock);
        states = new PostgresRecordStateStore(jdbc, clock);
        events = new PostgresRecordEventStore(jdbc, clock);
        announcements = new PostgresRecordAnnouncementStore(jdbc, clock);
        bootstraps = new PostgresRecordBootstrapStore(jdbc, clock);
        transactions = new RecordTransactionRunner() {
            private final TransactionTemplate template =
                    new TransactionTemplate(new DataSourceTransactionManager(source));

            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> action) {
                return template.execute(status -> action.get());
            }
        };
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM record_announcement_message");
        jdbc.update("DELETE FROM record_announcement_event");
        jdbc.update("DELETE FROM record_announcement");
        jdbc.update("DELETE FROM record_event");
        jdbc.update("DELETE FROM record_state");
        jdbc.update("DELETE FROM record_bootstrap");
        jdbc.update("DELETE FROM record_live_evaluation");
        jdbc.update("DELETE FROM record_day_close");
        jdbc.update("DELETE FROM game_result_excuse_option");
        jdbc.update("DELETE FROM game_result_excuse_offer_context");
        jdbc.update("DELETE FROM game_result_excuse");
        jdbc.update("DELETE FROM submission_attachment");
        jdbc.update("DELETE FROM submission");
        jdbc.update("DELETE FROM game_result");
        jdbc.update("DELETE FROM player_participation_period");
        jdbc.update("DELETE FROM player");
    }

    @Test
    void validShareSurvivesCanonicalPublicationAndLeaseReclaimBeforeRecordAnnouncementDelivery() {
        seedPlayerAndFiveResultComparisonBasis();
        RecordStateService stateService = new RecordStateService(states, events, transactions, catalog);
        RecordBootstrapCoordinator bootstrap = new RecordBootstrapCoordinator(
                bootstraps, new PostgresRecordHistoryQuery(jdbc), stateService, catalog, clock);

        assertThat(bootstrap.run(GUILD_ID))
                .isEqualTo(RecordBootstrapCoordinator.BootstrapRunResult.SUCCEEDED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_announcement", Integer.class)).isZero();

        String share = """
                GridWords (7. August 2026) 1/6 in 0:05
                🟩🟩🟩🟩🟩
                """;
        ProcessSharedResultService submissions = new ProcessSharedResultService(
                new GridWordsShareParser(), new QuadWordsShareParser(), clock, BERLIN,
                persistence, persistence, ignored -> true);
        ProcessingResult submissionResult = submissions.process(new InboundSharedMessage(
                GUILD_ID, CHANNEL_ID, 5_000L, PLAYER_ID, "Player One", share, List.of(), NOW));

        assertThat(submissionResult).isEqualTo(new ProcessingResult.Accepted(GameType.GRIDWORDS));
        Long resultId = jdbc.queryForObject("""
                SELECT id FROM game_result
                WHERE player_id = ? AND game_type = 'GRIDWORDS' AND game_date = DATE '2026-08-07'
                """, Long.class, PLAYER_ID);
        assertThat(resultId).isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM record_live_evaluation
                WHERE guild_id = ? AND game_result_id = ? AND evaluation_state = 'OPEN'
                """, Integer.class, GUILD_ID, resultId)).isOne();

        CanonicalRecordingGateway canonicalGateway = new CanonicalRecordingGateway();
        CanonicalGridWordsPublicationService canonical = new CanonicalGridWordsPublicationService(
                persistence, persistence, persistence, canonicalGateway, clock, BERLIN,
                (at, action) -> { }, ignored -> { });
        assertThat(canonical.publish(5_000L)).isTrue();
        assertThat(jdbc.queryForObject("SELECT version FROM game_result WHERE id = ?", Long.class, resultId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT canonical_refresh_generation FROM game_result WHERE id = ?", Long.class, resultId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT processing_state FROM submission WHERE source_message_id = ?", String.class, 5_000L))
                .isEqualTo("CANONICAL_MESSAGE_PUBLISHED");
        GridWordsSourceDeletionService sourceDeletion = new GridWordsSourceDeletionService(
                persistence,
                (channelId, sourceMessageId) -> SourceMessageDeletionGateway.DeletionResult.DELETED,
                clock,
                (at, action) -> { });
        assertThat(sourceDeletion.deleteAfterCanonicalPublication(5_000L)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT processing_state FROM submission WHERE source_message_id = ?", String.class, 5_000L))
                .isEqualTo("COMPLETED");

        assertThat(work.claimNext(new RecordLeaseClaimRequest(NOW, NOW.plusSeconds(1))))
                .hasValueSatisfying(claim -> assertThat(claim.key().gameResultVersion()).isZero());

        Clock restartClock = Clock.fixed(NOW.plusSeconds(2), BERLIN);
        PostgresRecordLiveEvaluationStore restartedWork = new PostgresRecordLiveEvaluationStore(jdbc, restartClock);
        PostgresRecordStateStore restartedStates = new PostgresRecordStateStore(jdbc, restartClock);
        PostgresRecordEventStore restartedEvents = new PostgresRecordEventStore(jdbc, restartClock);
        PostgresRecordAnnouncementStore restartedAnnouncements = new PostgresRecordAnnouncementStore(jdbc, restartClock);
        RecordStateService restartedStateService = new RecordStateService(
                restartedStates, restartedEvents, transactions, catalog);
        RecordLiveEvaluationProcessor processor = new RecordLiveEvaluationProcessor(
                restartedWork,
                new PostgresRecordLiveHistoryQuery(jdbc),
                new RecordBootstrapReadService(bootstraps),
                restartedStateService,
                restartedEvents,
                restartedAnnouncements,
                transactions,
                catalog,
                restartClock,
                CHANNEL_ID);
        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            RecordLiveEvaluationCoordinator coordinator = new RecordLiveEvaluationCoordinator(
                    restartedWork, processor, restartClock, Duration.ofSeconds(30), Duration.ofSeconds(1),
                    Duration.ofSeconds(1), Duration.ofMinutes(1), heartbeat,
                    (result, duration) -> { });
            assertThat(coordinator.runNext())
                    .isEqualTo(RecordLiveEvaluationCoordinator.RunResult.COMPLETED);
        }
        assertThat(jdbc.queryForObject("""
                SELECT evaluation_state FROM record_live_evaluation
                WHERE guild_id = ? AND game_result_id = ? AND game_result_version = 0
                """, String.class, GUILD_ID, resultId)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("""
                SELECT attempt_count FROM record_live_evaluation
                WHERE guild_id = ? AND game_result_id = ? AND game_result_version = 0
                """, Integer.class, GUILD_ID, resultId)).isEqualTo(2);

        RecordAnnouncementKey key = new RecordAnnouncementKey(
                GUILD_ID, CHANNEL_ID,
                "live-result:" + resultId + ":player:" + PLAYER_ID + ":LIVE_EVALUATION");
        var desired = restartedAnnouncements.find(key).orElseThrow();
        assertThat(desired.registration().desiredProjection()).isEqualTo(RecordAnnouncementProjection.CREATE);
        assertThat(desired.registration().eventIds()).hasSize(2);
        assertThat(desired.registration().eventIds()).allSatisfy(eventId ->
                assertThat(restartedEvents.find(eventId).orElseThrow().draft().stateKey().scope())
                        .isEqualTo(new RecordScope.Personal(PLAYER_ID)));

        RecordingGateway gateway = new RecordingGateway();
        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            RecordAnnouncementDeliveryCoordinator delivery = new RecordAnnouncementDeliveryCoordinator(
                    restartedAnnouncements,
                    restartedEvents,
                    persistence,
                    gateway,
                    new RecordAnnouncementRenderer(),
                    restartClock,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1),
                    Duration.ofMinutes(1),
                    heartbeat,
                    true,
                    (result, duration) -> { });
            assertThat(delivery.runNext())
                    .isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
            assertThat(delivery.runNext())
                    .isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        }

        assertThat(gateway.created).singleElement().satisfies(page -> {
            assertThat(page.title()).isEqualTo("🏆 Neuer Rekord");
            assertThat(page.description()).contains(
                    "GridWords", "Wenigste Versuche", "Schnellste Lösung", "Player One");
        });
        assertThat(restartedAnnouncements.find(key).orElseThrow()).satisfies(snapshot -> {
            assertThat(snapshot.state()).isEqualTo(RecordWorkState.SYNCHRONIZED);
            assertThat(snapshot.messages()).hasSize(1);
            assertThat(snapshot.attemptCount()).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_announcement_message", Integer.class)).isOne();

        String correction = """
                GridWords (7. August 2026) 1/6 in 0:06
                🟩🟩🟩🟩🟩
                """;
        assertThat(submissions.process(new InboundSharedMessage(
                GUILD_ID, CHANNEL_ID, 5_001L, PLAYER_ID, "Player One", correction, List.of(), NOW.plusSeconds(3))))
                .isEqualTo(new ProcessingResult.Accepted(GameType.GRIDWORDS));
        assertThat(canonical.publish(5_001L)).isTrue();
        assertThat(sourceDeletion.deleteAfterCanonicalPublication(5_001L)).isTrue();
        assertThat(canonicalGateway.edits).isOne();
        assertThat(jdbc.queryForObject("SELECT version FROM game_result WHERE id = ?", Long.class, resultId)).isOne();

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            RecordLiveEvaluationCoordinator coordinator = new RecordLiveEvaluationCoordinator(
                    restartedWork, processor, restartClock, Duration.ofSeconds(30), Duration.ofSeconds(1),
                    Duration.ofSeconds(1), Duration.ofMinutes(1), heartbeat,
                    (result, duration) -> { });
            assertThat(coordinator.runNext())
                    .isEqualTo(RecordLiveEvaluationCoordinator.RunResult.COMPLETED);
        }
        assertThat(restartedAnnouncements.find(key).orElseThrow().registration().desiredProjection())
                .isEqualTo(RecordAnnouncementProjection.EDIT);

        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            RecordAnnouncementDeliveryCoordinator delivery = new RecordAnnouncementDeliveryCoordinator(
                    restartedAnnouncements, restartedEvents, persistence, gateway,
                    new RecordAnnouncementRenderer(), restartClock,
                    Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(1),
                    heartbeat, true, (result, duration) -> { });
            assertThat(delivery.runNext())
                    .isEqualTo(RecordAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        }
        assertThat(gateway.edited).singleElement().satisfies(page ->
                assertThat(page.description()).contains("1 Versuche", "0:06"));
        assertThat(gateway.discoveryCalls).isZero();
        assertThat(restartedAnnouncements.find(key).orElseThrow()).satisfies(snapshot -> {
            assertThat(snapshot.state()).isEqualTo(RecordWorkState.SYNCHRONIZED);
            assertThat(snapshot.attemptCount()).isEqualTo(2);
            assertThat(snapshot.failure()).isEmpty();
        });
    }

    private static final class CanonicalRecordingGateway implements CanonicalMessageGateway {
        private long messageId;
        private int edits;

        @Override
        public long create(long channelId, CanonicalResultMessage message) {
            assertThat(channelId).isEqualTo(CHANNEL_ID);
            assertThat(message.publicationKey()).startsWith("gridwords-result-");
            messageId = 8_000L;
            return messageId;
        }

        @Override
        public void edit(long channelId, long existingMessageId, CanonicalResultMessage message) {
            assertThat(channelId).isEqualTo(CHANNEL_ID);
            assertThat(existingMessageId).isEqualTo(messageId);
            edits++;
        }

        @Override
        public OptionalLong findByPublicationKey(long channelId, String publicationKey) {
            return messageId == 0 ? OptionalLong.empty() : OptionalLong.of(messageId);
        }

        @Override
        public void delete(long channelId, long existingMessageId) {
            throw new AssertionError("initial canonical publication must not delete a message");
        }
    }

    private void seedPlayerAndFiveResultComparisonBasis() {
        jdbc.update("""
                INSERT INTO player (
                    discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, 'Player One', TRUE, FALSE, FALSE, ?, ?)
                """, PLAYER_ID, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO player_participation_period (
                    player_id, game_type, active_from, inactive_from, created_at, updated_at)
                VALUES (?, 'GRIDWORDS', DATE '2026-08-01', NULL, ?, ?)
                """, PLAYER_ID, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));

        int[] attempts = {5, 5, 4, 4, 3};
        int[] durations = {120, 110, 100, 90, 80};
        for (int index = 0; index < attempts.length; index++) {
            LocalDate date = LocalDate.of(2026, 8, 2 + index);
            String normalizedBoard = "⬜⬜⬜⬜⬜\n".repeat(attempts[index] - 1) + "🟩🟩🟩🟩🟩";
            Long resultId = jdbc.queryForObject("""
                    INSERT INTO game_result (
                        player_id, game_type, game_date, solved, attempts_used, max_attempts,
                        duration_seconds, normalized_board, raw_share_text, parser_version, created_at, updated_at)
                    VALUES (?, 'GRIDWORDS', ?, TRUE, ?, 6, ?, ?, 'historical share',
                        'gridwords-share-v1', ?, ?)
                    RETURNING id
                    """, Long.class, PLAYER_ID, date, attempts[index], durations[index], normalizedBoard,
                    java.sql.Timestamp.from(NOW.minusSeconds(600L - index)),
                    java.sql.Timestamp.from(NOW.minusSeconds(600L - index)));
            java.sql.Timestamp completedAt = java.sql.Timestamp.from(NOW.minusSeconds(600L - index));
            jdbc.update("""
                    INSERT INTO submission (
                        source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                        processing_state, game_result_id, received_at, updated_at, original_deleted_at)
                    VALUES (?, ?, ?, ?, 'historical share', 'COMPLETED', ?, ?, ?, ?)
                    """, 1_000L + index, GUILD_ID, CHANNEL_ID, PLAYER_ID, resultId,
                    completedAt, completedAt, completedAt);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM record_live_evaluation", Integer.class)).isZero();
    }

    private static final class RecordingGateway implements RecordAnnouncementMessageGateway {
        private final List<RenderedRecordAnnouncementPage> created = new ArrayList<>();
        private final List<RenderedRecordAnnouncementPage> edited = new ArrayList<>();
        private final List<PublishedPage> published = new ArrayList<>();
        private int discoveryCalls;

        @Override
        public long create(long channelId, RenderedRecordAnnouncementPage page) {
            assertThat(channelId).isEqualTo(CHANNEL_ID);
            created.add(page);
            long messageId = 9_000L + page.position();
            published.add(new PublishedPage(messageId, page.position()));
            return messageId;
        }

        @Override
        public void edit(long channelId, long messageId, RenderedRecordAnnouncementPage page) {
            assertThat(channelId).isEqualTo(CHANNEL_ID);
            assertThat(published).anyMatch(publishedPage -> publishedPage.messageId() == messageId);
            edited.add(page);
        }

        @Override
        public void delete(long channelId, long messageId) {
            throw new AssertionError("initial record delivery must not delete Discord messages");
        }

        @Override
        public boolean exists(long channelId, long messageId) {
            return published.stream().anyMatch(page -> page.messageId() == messageId);
        }

        @Override
        public List<PublishedPage> discoverCreatedPages(
                long channelId, String publicationKey, List<RenderedRecordAnnouncementPage> expectedPages) {
            assertThat(channelId).isEqualTo(CHANNEL_ID);
            discoveryCalls++;
            return List.copyOf(published);
        }
    }
}
