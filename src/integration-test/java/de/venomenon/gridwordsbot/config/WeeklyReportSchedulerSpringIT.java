package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.GridwordsBotApplication;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportDeliveryService;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportUseCase;
import de.venomenon.gridwordsbot.application.reporting.ReportDayAndStreakProjector;
import de.venomenon.gridwordsbot.application.reporting.ReportGameStatisticsProjector;
import de.venomenon.gridwordsbot.application.reporting.ReportParticipantProjector;
import de.venomenon.gridwordsbot.application.reporting.WeeklyReportReconciliationService;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaimRequest;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
import de.venomenon.gridwordsbot.domain.reporting.ReportDueAt;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlanner;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WeeklyReportSchedulerSpringIT {
    private static final long GUILD_ID = 11L;
    private static final long CHANNEL_ID = 12L;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Instant OPEN_NOW = Instant.parse("2026-08-03T06:00:00Z");
    private static final Instant CLOSED_NOW = Instant.parse("2026-08-06T06:00:00Z");
    private static final ReportPeriod PERIOD = new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
    private static final MutableClock CLOCK = new MutableClock(OPEN_NOW);
    private static final ThreadSafeGateway GATEWAY = new ThreadSafeGateway();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        try (ConfigurableApplicationContext ignored = start()) {
            // Liquibase creates the isolated test schema once for this class.
        }
        GATEWAY.reset();
    }

    @BeforeEach
    void reset() {
        CLOCK.set(OPEN_NOW);
        GATEWAY.reset();
        jdbc = new JdbcTemplate(dataSource());
        jdbc.update("DELETE FROM periodic_report_delivery_page");
        jdbc.update("DELETE FROM periodic_report_delivery");
        jdbc.update("DELETE FROM game_result");
        jdbc.update("DELETE FROM player_participation_period");
        jdbc.update("DELETE FROM player");
    }

    @AfterEach
    void clearGateway() {
        GATEWAY.reset();
    }

    @Test
    void databaseProfileWiresTheCompleteWeeklyPathAndStartupPublishesOnce() {
        insertParticipant(1L, "Original");

        try (ConfigurableApplicationContext context = start()) {
            PeriodicReportDeliveryStore store = context.getBean(PeriodicReportDeliveryStore.class);
            assertThat(context.getBeansOfType(ReportParticipantProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(ReportGameStatisticsProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(ReportDayAndStreakProjector.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportUseCase.class)).hasSize(1);
            assertThat(context.getBeansOfType(PeriodicReportReconciliationPlanner.class)).hasSize(1);
            assertThat(context.getBeansOfType(WeeklyReportReconciliationService.class)).hasSize(1);
            assertThat(context.getBeansOfType(WeeklyReportScheduler.class)).hasSize(1);
            assertThat(store.find(key()).orElseThrow().state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            int pageCount = store.find(key()).orElseThrow().pageProgress().size();
            assertThat(GATEWAY.messageCount()).isEqualTo(pageCount);

            context.getBean(WeeklyReportScheduler.class).reconcile();
            assertThat(GATEWAY.messageCount()).isEqualTo(pageCount);
        }
    }

    @Test
    void noOpAndExactCatchUpExpiryArePersistedWithoutMessages() {
        try (ConfigurableApplicationContext context = start()) {
            assertThat(context.getBean(PeriodicReportDeliveryStore.class).find(key()).orElseThrow().state())
                    .isEqualTo(PeriodicReportDeliveryState.NO_OP);
            assertThat(GATEWAY.messageCount()).isZero();
        }

        reset();
        CLOCK.set(CLOSED_NOW);
        try (ConfigurableApplicationContext context = start()) {
            assertThat(context.getBean(PeriodicReportDeliveryStore.class).find(key()).orElseThrow().state())
                    .isEqualTo(PeriodicReportDeliveryState.EXPIRED);
            assertThat(GATEWAY.messageCount()).isZero();
        }
    }

    @Test
    void tickRepairsAnExternallyDeletedPageOnlyInsideTheCatchUpWindowAndNeverEditsLaterSourceChanges() {
        insertParticipant(1L, "Original");
        try (ConfigurableApplicationContext context = start()) {
            WeeklyReportScheduler scheduler = context.getBean(WeeklyReportScheduler.class);
            long originalMessageId = GATEWAY.onlyMessageId();
            GATEWAY.deleteExternally(originalMessageId);

            scheduler.reconcile();
            assertThat(GATEWAY.messageCount()).isOne();
            assertThat(GATEWAY.createCalls()).isEqualTo(2);

            jdbc.update("UPDATE player SET display_name = 'Changed' WHERE discord_user_id = 1");
            scheduler.reconcile();
            assertThat(GATEWAY.editCalls()).isZero();

            GATEWAY.deleteExternally(GATEWAY.onlyMessageId());
            CLOCK.set(CLOSED_NOW);
            scheduler.reconcile();
            assertThat(GATEWAY.messageCount()).isZero();
            assertThat(context.getBean(PeriodicReportDeliveryStore.class).find(key()).orElseThrow().state())
                    .isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
        }
    }

    @Test
    void anActiveClaimIsNotStolenAndAnExpiredLeaseIsRecoveredByTheWeeklyPath() {
        CLOCK.set(OPEN_NOW.minus(Duration.ofHours(2)));
        try (ConfigurableApplicationContext context = start()) {
            PeriodicReportDeliveryStore store = context.getBean(PeriodicReportDeliveryStore.class);
            CLOCK.set(OPEN_NOW);
            store.register(new PeriodicReportDeliveryRegistration(key(), metadata(), Optional.empty()));
            var active = store.claim(key(), new PeriodicReportDeliveryClaimRequest(
                    OPEN_NOW, OPEN_NOW.plus(Duration.ofMinutes(1)))).orElseThrow();

            context.getBean(WeeklyReportScheduler.class).reconcile();
            assertThat(store.find(key()).orElseThrow().state()).isEqualTo(PeriodicReportDeliveryState.CLAIMED);
            assertThat(store.find(key()).orElseThrow().claim().orElseThrow().token()).isEqualTo(active.token());
            assertThat(GATEWAY.messageCount()).isZero();

            CLOCK.set(OPEN_NOW.plus(Duration.ofMinutes(2)));
            context.getBean(WeeklyReportScheduler.class).reconcile();
            assertThat(store.find(key()).orElseThrow().state()).isEqualTo(PeriodicReportDeliveryState.NO_OP);
            assertThat(GATEWAY.messageCount()).isZero();
        }
    }

    @Test
    void restartRecoversAnUnknownCreateWithoutASecondVisiblePage() {
        insertParticipant(1L, "Recoverable");
        GATEWAY.unknownAfterNextCreate();
        try (ConfigurableApplicationContext first = start()) {
            assertThat(first.getBean(PeriodicReportDeliveryStore.class).find(key()).orElseThrow().state())
                    .isEqualTo(PeriodicReportDeliveryState.RETRYABLE);
            assertThat(GATEWAY.messageCount()).isOne();
        }

        CLOCK.set(OPEN_NOW.plusSeconds(30));
        try (ConfigurableApplicationContext restarted = start()) {
            assertThat(restarted.getBean(PeriodicReportDeliveryStore.class).find(key()).orElseThrow().state())
                    .isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            assertThat(GATEWAY.messageCount()).isOne();
        }
    }

    @Test
    void parallelWeeklyPathsConvergeOnOneDeliveryOnePageAndOneTombstonePerOlderPeriod() throws Exception {
        CLOCK.set(OPEN_NOW.minus(Duration.ofHours(2)));
        try (ConfigurableApplicationContext first = start(); ConfigurableApplicationContext second = start()) {
            PeriodicReportDeliveryStore store = first.getBean(PeriodicReportDeliveryStore.class);
            store.expire(new de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryExpiration(
                    new PeriodicReportDeliveryKey(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, LocalDate.of(2026, 7, 6)),
                    new PeriodicReportDeliveryMetadata(
                            new ReportPeriod(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12)),
                            new ReportDueAt(LocalDate.of(2026, 7, 13), LocalTime.of(8, 0), BERLIN),
                            Instant.parse("2026-07-16T06:00:00Z"))),
                    Instant.parse("2026-07-16T06:00:00Z"));
            insertParticipant(1L, "Concurrent");
            CLOCK.set(OPEN_NOW);

            Thread one = new Thread(() -> first.getBean(WeeklyReportReconciliationService.class).reconcile(GUILD_ID, CHANNEL_ID));
            Thread two = new Thread(() -> second.getBean(WeeklyReportReconciliationService.class).reconcile(GUILD_ID, CHANNEL_ID));
            one.start();
            two.start();
            one.join();
            two.join();

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM periodic_report_delivery", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM periodic_report_delivery WHERE delivery_state = 'EXPIRED'", Integer.class))
                    .isEqualTo(2);
            assertThat(store.find(key()).orElseThrow().state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            assertThat(GATEWAY.messageCount()).isOne();
        }
    }

    @Test
    void configuredBerlinWeeklyTimeRetainsThePlannerDstSemantics() {
        CLOCK.set(Instant.parse("2026-03-30T06:00:00Z"));
        try (ConfigurableApplicationContext context = start()) {
            var snapshot = context.getBean(PeriodicReportDeliveryStore.class).find(new PeriodicReportDeliveryKey(
                    GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, LocalDate.of(2026, 3, 23))).orElseThrow();
            assertThat(snapshot.registration().metadata().dueAt())
                    .isEqualTo(new ReportDueAt(LocalDate.of(2026, 3, 30), LocalTime.of(8, 0), BERLIN));
            assertThat(snapshot.registration().metadata().dueAt().instant())
                    .isEqualTo(Instant.parse("2026-03-30T06:00:00Z"));
        }
    }

    private static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(WeeklyReportTestConfiguration.class, GridwordsBotApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("database")
                .run(properties());
    }

    private static String[] properties() {
        return new String[] {
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--gridwords.discord.enabled=false",
                "--gridwords.discord.guild-id=" + GUILD_ID,
                "--gridwords.discord.channel-id=" + CHANNEL_ID,
                "--gridwords.discord.admin-user-ids=1",
                "--gridwords.schedule.weekly-report=08:00",
                "--gridwords.schedule.time-zone=Europe/Berlin"};
    }

    private static DataSource dataSource() {
        org.springframework.jdbc.datasource.DriverManagerDataSource dataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private void insertParticipant(long playerId, String name) {
        OffsetDateTime now = OffsetDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO player (discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, ?, FALSE, FALSE, FALSE, ?, ?)
                """, playerId, name, now, now);
        jdbc.update("""
                INSERT INTO player_participation_period (player_id, active_from, inactive_from, created_at, updated_at)
                VALUES (?, ?, NULL, ?, ?)
                """, playerId, PERIOD.startDate(), now, now);
    }

    private static PeriodicReportDeliveryKey key() {
        return new PeriodicReportDeliveryKey(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, PERIOD.startDate());
    }

    private static PeriodicReportDeliveryMetadata metadata() {
        return new PeriodicReportDeliveryMetadata(
                PERIOD,
                new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.of(8, 0), BERLIN),
                CLOSED_NOW);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class WeeklyReportTestConfiguration {
        @Bean
        @Primary
        Clock weeklyReportClock() {
            return CLOCK;
        }

        @Bean
        WeeklyReportReconciliationService testWeeklyReportReconciliationService(
                PeriodicReportDeliveryStore store,
                PeriodicReportReconciliationPlanner planner,
                PeriodicReportUseCase reports,
                PeriodicReportDeliveryService delivery,
                Clock clock,
                GridwordsBotProperties properties) {
            return new WeeklyReportReconciliationService(
                    store, planner, reports, delivery, clock,
                    properties.schedule().weeklyReport(), properties.schedule().timeZone());
        }

        @Bean
        PeriodicReportMessageGateway testPeriodicReportMessageGateway() {
            return GATEWAY;
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant now) { this.now = new AtomicReference<>(now); }
        void set(Instant instant) { now.set(instant); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    static final class ThreadSafeGateway implements PeriodicReportMessageGateway {
        private final AtomicLong nextId = new AtomicLong(1_000L);
        private final Map<Long, ReportPage> messages = new ConcurrentHashMap<>();
        private final AtomicLong createCalls = new AtomicLong();
        private final AtomicLong editCalls = new AtomicLong();
        private final AtomicReference<Boolean> unknownAfterCreate = new AtomicReference<>(false);

        void reset() {
            nextId.set(1_000L);
            messages.clear();
            createCalls.set(0);
            editCalls.set(0);
            unknownAfterCreate.set(false);
        }

        void unknownAfterNextCreate() { unknownAfterCreate.set(true); }
        int messageCount() { return messages.size(); }
        long createCalls() { return createCalls.get(); }
        long editCalls() { return editCalls.get(); }
        long onlyMessageId() { return messages.keySet().stream().findFirst().orElseThrow(); }
        void deleteExternally(long messageId) { messages.remove(messageId); }

        @Override
        public long create(long channelId, ReportPage page) {
            long id = nextId.getAndIncrement();
            messages.put(id, page);
            createCalls.incrementAndGet();
            if (unknownAfterCreate.compareAndSet(true, false)) {
                throw new UnknownMessageException("outcome unknown", null);
            }
            return id;
        }

        @Override
        public void edit(long channelId, long messageId, ReportPage page) {
            if (messages.replace(messageId, page) == null) throw new MissingMessageException("missing");
            editCalls.incrementAndGet();
        }

        @Override
        public PublishedReportPage load(long channelId, long messageId) {
            ReportPage page = messages.get(messageId);
            if (page == null) throw new MissingMessageException("missing");
            return new PublishedReportPage(messageId, page);
        }

        @Override
        public List<PublishedReportPage> findExactMatches(long channelId, ReportPage page) {
            return messages.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(page))
                    .map(entry -> new PublishedReportPage(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparingLong(PublishedReportPage::messageId))
                    .toList();
        }

        @Override
        public void delete(long channelId, long messageId) {
            if (messages.remove(messageId) == null) throw new MissingMessageException("missing");
        }
    }
}
