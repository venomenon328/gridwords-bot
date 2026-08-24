package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.achievement.AchievementAnnouncementDeliveryCoordinator;
import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.application.achievement.AchievementReconciliationService;
import de.venomenon.gridwordsbot.application.achievement.AchievementResultLifecycle;
import de.venomenon.gridwordsbot.application.achievement.RenderedAchievementAnnouncement;
import de.venomenon.gridwordsbot.application.submission.ProcessSharedResultService;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvaluator;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsImageParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementMessageGateway;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
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

/** Full normal-result path from a real parsed share through PostgreSQL reconciliation to one delivered Achievement batch. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresAchievementShareToDeliveryE2EIT {
    private static final long GUILD_ID = 10L;
    private static final long CHANNEL_ID = 20L;
    private static final long PLAYER_ID = 30L;
    private static final long SOURCE_MESSAGE_ID = 5_000L;
    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Clock CLOCK = Clock.fixed(NOW, BERLIN);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;

    @BeforeAll
    void migrate() throws Exception {
        source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM achievement_announcement_item");
        jdbc.update("DELETE FROM achievement_announcement");
        jdbc.update("DELETE FROM achievement_event");
        jdbc.update("DELETE FROM achievement_bootstrap_state");
        jdbc.update("DELETE FROM achievement_award_state");
        jdbc.execute("TRUNCATE TABLE player CASCADE");
    }

    @Test
    void firstSolvedShareReconcilesBeforeCanonicalPublicationAndDeliversOneAggregatedBatch() {
        AchievementDefinitionCatalog catalog = AchievementDefinitionCatalog.achievementsV2();
        PostgresPersistenceAdapter persistence = new PostgresPersistenceAdapter(jdbc, CLOCK, BERLIN);
        PostgresAchievementAwardStateStore awards = new PostgresAchievementAwardStateStore(jdbc, CLOCK);
        PostgresAchievementEventStore events = new PostgresAchievementEventStore(jdbc, CLOCK);
        PostgresAchievementAnnouncementStore announcements = new PostgresAchievementAnnouncementStore(jdbc, CLOCK);
        PostgresAchievementBootstrapStore bootstraps = new PostgresAchievementBootstrapStore(jdbc, CLOCK);
        AchievementTransactionRunner transactions = transactionRunner();
        AchievementReconciliationService reconciliation = new AchievementReconciliationService(
                new PostgresAchievementHistoryQuery(jdbc),
                new AchievementEvaluator(catalog),
                catalog,
                awards,
                events,
                announcements,
                transactions,
                CLOCK,
                BERLIN);
        AchievementResultLifecycle lifecycle = new AchievementResultLifecycle(
                bootstraps, transactions, reconciliation, catalog, awards, events, announcements, persistence, CLOCK);
        markBootstrapSucceeded(bootstraps, catalog);

        AtomicBoolean canonicalObservedAchievementProjection = new AtomicBoolean();
        ProcessSharedResultService submissions = new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                attachment -> new byte[0],
                new QuadWordsImageParser(),
                CLOCK,
                BERLIN,
                persistence,
                persistence,
                ignored -> {
                    canonicalObservedAchievementProjection.set(
                            jdbc.queryForObject("SELECT count(*) FROM achievement_award_state", Integer.class) == 3
                                    && jdbc.queryForObject("SELECT count(*) FROM achievement_announcement", Integer.class) == 1);
                    return true;
                },
                ignored -> false,
                ignored -> { },
                lifecycle::reconcileNormal);

        String share = """
                GridWords (8. August 2026) 1/6 in 0:05
                🟩🟩🟩🟩🟩
                """;
        ProcessingResult result = submissions.process(new InboundSharedMessage(
                GUILD_ID, CHANNEL_ID, SOURCE_MESSAGE_ID, PLAYER_ID, "Achievement Player", share, List.of(), NOW));

        assertThat(result).isEqualTo(new ProcessingResult.Accepted(GameType.GRIDWORDS));
        assertThat(canonicalObservedAchievementProjection).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_award_state WHERE award_status='ACTIVE'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM achievement_event WHERE event_type='UNLOCKED'", Integer.class))
                .isEqualTo(3);
        AchievementAnnouncement.Snapshot pending = announcements.findPending(GUILD_ID, PLAYER_ID).getFirst();
        assertThat(pending.registration().type()).isEqualTo(AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH);
        assertThat(announcements.findItems(pending.registration().key())).hasSize(3);

        RecordingGateway gateway = new RecordingGateway();
        try (ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor()) {
            AchievementAnnouncementDeliveryCoordinator delivery = new AchievementAnnouncementDeliveryCoordinator(
                    announcements,
                    events,
                    awards,
                    persistence,
                    gateway,
                    catalog,
                    AchievementEmojiResolver.unicodeOnly(),
                    CLOCK,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(1),
                    Duration.ofMinutes(1),
                    heartbeat);
            assertThat(delivery.runNext()).isEqualTo(AchievementAnnouncementDeliveryCoordinator.RunResult.COMPLETED);
        }

        assertThat(gateway.created).singleElement().satisfies(rendered -> {
            String visible = rendered.embeds().stream()
                    .map(embed -> embed.title() + "\n" + embed.description())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(visible)
                    .contains("Achievement Player")
                    .contains("GW: Dabei!")
                    .contains("GW: Geschafft!")
                    .contains("GW: Volltreffer")
                    .contains("Dein erstes gültiges GridWords-Ergebnis.")
                    .contains("Dein erster erfolgreicher GridWords-Abschluss.");
        });
        assertThat(announcements.find(pending.registration().key()).orElseThrow()).satisfies(snapshot -> {
            assertThat(snapshot.deliveryState()).isEqualTo(AchievementAnnouncement.DeliveryState.SYNCHRONIZED);
            assertThat(snapshot.discordMessageId()).contains(9_000L);
        });
    }

    private AchievementTransactionRunner transactionRunner() {
        TransactionTemplate template = new TransactionTemplate(new DataSourceTransactionManager(source));
        return new AchievementTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return template.execute(status -> work.get());
            }

            @Override
            public <T> T inParticipantTransaction(long participantId, java.util.function.Supplier<T> work) {
                return template.execute(status -> {
                    Long locked = jdbc.queryForObject(
                            "SELECT discord_user_id FROM player WHERE discord_user_id=? FOR UPDATE",
                            Long.class,
                            participantId);
                    if (locked == null || locked.longValue() != participantId) {
                        throw new IllegalStateException("achievement participant lock could not be acquired");
                    }
                    return work.get();
                });
            }

            @Override
            public <T> T inBootstrapFenceTransaction(
                    AchievementWork.BootstrapKey key, java.util.function.Supplier<T> work) {
                return template.execute(status -> {
                    Integer locked = jdbc.queryForObject("""
                            SELECT 1 FROM achievement_bootstrap_state
                             WHERE guild_id=? AND definition_version=?
                             FOR UPDATE
                            """, Integer.class, key.guildId(), key.definitionVersion().value());
                    if (locked == null || locked != 1) {
                        throw new IllegalStateException("achievement bootstrap fence could not be acquired");
                    }
                    return work.get();
                });
            }
        };
    }

    private void markBootstrapSucceeded(
            PostgresAchievementBootstrapStore bootstraps, AchievementDefinitionCatalog catalog) {
        AchievementWork.BootstrapKey key = new AchievementWork.BootstrapKey(GUILD_ID, catalog.version());
        bootstraps.register(key);
        AchievementWork.LeaseClaim claim = bootstraps.claim(
                key, new AchievementWork.LeaseClaimRequest(NOW, NOW.plusSeconds(60))).orElseThrow();
        assertThat(bootstraps.markSucceeded(key, claim.token(), NOW)).isTrue();
    }

    private static final class RecordingGateway implements AchievementAnnouncementMessageGateway {
        private final List<RenderedAchievementAnnouncement> created = new ArrayList<>();

        @Override
        public long create(long channelId, RenderedAchievementAnnouncement announcement) {
            assertThat(channelId).isEqualTo(CHANNEL_ID);
            created.add(announcement);
            return 9_000L;
        }

        @Override
        public boolean exists(long channelId, long messageId) {
            return messageId == 9_000L;
        }

        @Override
        public List<Long> discoverCreatedMessages(
                long channelId, String publicationKey, RenderedAchievementAnnouncement expected) {
            return List.of();
        }

        @Override
        public void delete(long channelId, long messageId) {
            throw new AssertionError("normal first delivery must not delete messages");
        }
    }
}
