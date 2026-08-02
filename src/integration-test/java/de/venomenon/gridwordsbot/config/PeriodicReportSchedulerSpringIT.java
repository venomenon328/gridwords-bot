package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.GridwordsBotApplication;
import de.venomenon.gridwordsbot.application.reporting.MonthlyReportReconciliationService;
import de.venomenon.gridwordsbot.application.reporting.WeeklyReportReconciliationService;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryScope;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class PeriodicReportSchedulerSpringIT {
    private static final long GUILD_ID = 11L;
    private static final long CHANNEL_ID = 12L;
    private static final Instant BEFORE_DUE = Instant.parse("2026-06-29T05:59:00Z");
    private static final Instant BOTH_DUE = Instant.parse("2026-07-01T06:16:00Z");
    private static final Instant MONTHLY_CATCH_UP_END = Instant.parse("2026-07-08T06:15:00Z");
    private static final MutableClock CLOCK = new MutableClock(BEFORE_DUE);
    private static final ThreadSafeGateway GATEWAY = new ThreadSafeGateway();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        CLOCK.set(BEFORE_DUE);
        try (ConfigurableApplicationContext ignored = start(CombinedReportTestConfiguration.class)) {
            // Liquibase creates the isolated schema once for this integration-test class.
        }
        GATEWAY.reset();
    }

    @BeforeEach
    void reset() {
        CLOCK.set(BEFORE_DUE);
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
    void productionSpringSchedulersKeepConcurrentWeeklyAndMonthlyDeliveryAndRecoveryIndependent() throws Exception {
        for (long playerId = 1; playerId <= 26; playerId++) {
            insertParticipant(playerId, "Player " + playerId, LocalDate.of(2026, 6, 1));
        }

        Set<Long> recoveredMonthlyIds;
        Set<Long> weeklyIds;
        int weeklyPageCount;
        int visiblePagesAfterRecovery;
        long createCallsAfterRecovery;
        try (ConfigurableApplicationContext first = start(CombinedReportTestConfiguration.class);
                ConfigurableApplicationContext second = start(CombinedReportTestConfiguration.class)) {
            assertThat(first.getBeansOfType(WeeklyReportReconciliationService.class)).hasSize(1);
            assertThat(first.getBeansOfType(MonthlyReportReconciliationService.class)).hasSize(1);
            assertThat(first.getBeansOfType(WeeklyReportScheduler.class)).hasSize(1);
            assertThat(first.getBeansOfType(MonthlyReportScheduler.class)).hasSize(1);

            CLOCK.set(BOTH_DUE);
            runConcurrently(
                    () -> first.getBean(WeeklyReportScheduler.class).reconcile(),
                    () -> second.getBean(WeeklyReportScheduler.class).reconcile(),
                    () -> first.getBean(MonthlyReportScheduler.class).reconcile(),
                    () -> second.getBean(MonthlyReportScheduler.class).reconcile());

            PeriodicReportDeliveryStore store = first.getBean(PeriodicReportDeliveryStore.class);
            PeriodicReportDeliveryScope weeklyScope =
                    new PeriodicReportDeliveryScope(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY);
            PeriodicReportDeliveryScope monthlyScope =
                    new PeriodicReportDeliveryScope(GUILD_ID, CHANNEL_ID, ReportType.MONTHLY);
            PeriodicReportDeliveryKey weeklyKey = new PeriodicReportDeliveryKey(
                    GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, LocalDate.of(2026, 6, 22));
            PeriodicReportDeliveryKey monthlyKey = new PeriodicReportDeliveryKey(
                    GUILD_ID, CHANNEL_ID, ReportType.MONTHLY, LocalDate.of(2026, 6, 1));

            var weeklyBefore = store.find(weeklyKey).orElseThrow();
            var monthlyBefore = store.find(monthlyKey).orElseThrow();
            assertThat(weeklyBefore.state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            assertThat(monthlyBefore.state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            assertThat(monthlyBefore.pageProgress()).hasSizeGreaterThan(1);
            assertThat(store.findLatestPeriodStart(weeklyScope)).contains(weeklyKey.periodStart());
            assertThat(store.findLatestPeriodStart(monthlyScope)).contains(monthlyKey.periodStart());

            weeklyPageCount = weeklyBefore.pageProgress().size();
            weeklyIds = weeklyBefore.pageProgress().stream()
                    .map(page -> page.messageId())
                    .collect(java.util.stream.Collectors.toSet());
            Set<Long> monthlyIds = monthlyBefore.pageProgress().stream()
                    .map(page -> page.messageId())
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(weeklyIds).doesNotContainAnyElementsOf(monthlyIds);
            assertThat(GATEWAY.createCalls())
                    .isEqualTo(weeklyBefore.pageProgress().size() + monthlyBefore.pageProgress().size());

            long deletedMonthlyPage = monthlyBefore.pageProgress().getFirst().messageId();
            GATEWAY.deleteExternally(deletedMonthlyPage);
            long createCallsBeforeRecovery = GATEWAY.createCalls();
            first.getBean(MonthlyReportScheduler.class).reconcile();

            var monthlyRecovered = store.find(monthlyKey).orElseThrow();
            recoveredMonthlyIds = monthlyRecovered.pageProgress().stream()
                    .map(page -> page.messageId())
                    .collect(java.util.stream.Collectors.toSet());
            Set<Long> survivingMonthlyIds = new HashSet<>(monthlyIds);
            survivingMonthlyIds.remove(deletedMonthlyPage);
            assertThat(recoveredMonthlyIds)
                    .containsAll(survivingMonthlyIds)
                    .doesNotContain(deletedMonthlyPage);
            assertThat(monthlyRecovered.pageProgress()).hasSize(monthlyBefore.pageProgress().size());
            assertThat(GATEWAY.createCalls()).isEqualTo(createCallsBeforeRecovery + 1);

            var weeklyAfterRecovery = store.find(weeklyKey).orElseThrow();
            assertThat(weeklyAfterRecovery.state()).isEqualTo(weeklyBefore.state());
            assertThat(weeklyAfterRecovery.pageProgress()).hasSize(weeklyPageCount);
            assertThat(weeklyAfterRecovery.pageProgress().stream()
                            .map(page -> page.messageId())
                            .collect(java.util.stream.Collectors.toSet()))
                    .isEqualTo(weeklyIds);

            visiblePagesAfterRecovery = GATEWAY.messageCount();
            createCallsAfterRecovery = GATEWAY.createCalls();
        }

        try (ConfigurableApplicationContext restarted = start(CombinedReportTestConfiguration.class)) {
            PeriodicReportDeliveryStore store = restarted.getBean(PeriodicReportDeliveryStore.class);
            PeriodicReportDeliveryKey weeklyKey = new PeriodicReportDeliveryKey(
                    GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, LocalDate.of(2026, 6, 22));
            PeriodicReportDeliveryKey monthlyKey = new PeriodicReportDeliveryKey(
                    GUILD_ID, CHANNEL_ID, ReportType.MONTHLY, LocalDate.of(2026, 6, 1));

            var restartedWeekly = store.find(weeklyKey).orElseThrow();
            var restartedMonthly = store.find(monthlyKey).orElseThrow();
            assertThat(restartedWeekly.state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            assertThat(restartedWeekly.pageProgress()).hasSize(weeklyPageCount);
            assertThat(restartedWeekly.pageProgress().stream()
                            .map(page -> page.messageId())
                            .collect(java.util.stream.Collectors.toSet()))
                    .isEqualTo(weeklyIds);
            assertThat(restartedMonthly.state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            assertThat(restartedMonthly.pageProgress().stream()
                            .map(page -> page.messageId())
                            .collect(java.util.stream.Collectors.toSet()))
                    .isEqualTo(recoveredMonthlyIds);
            assertThat(GATEWAY.messageCount()).isEqualTo(visiblePagesAfterRecovery);
            assertThat(GATEWAY.createCalls()).isEqualTo(createCallsAfterRecovery);
        }
    }

    @Test
    void monthlySchedulerDoesNotRepairExternalDeletionAtOrAfterTheExactCatchUpEnd() {
        CLOCK.set(BOTH_DUE);
        for (long playerId = 1; playerId <= 26; playerId++) {
            insertParticipant(playerId, "Player " + playerId, LocalDate.of(2026, 6, 1));
        }

        try (ConfigurableApplicationContext context = start(MonthlyReportTestConfiguration.class)) {
            assertThat(context.getBeansOfType(MonthlyReportReconciliationService.class)).hasSize(1);
            PeriodicReportDeliveryStore store = context.getBean(PeriodicReportDeliveryStore.class);
            PeriodicReportDeliveryKey monthlyKey = new PeriodicReportDeliveryKey(
                    GUILD_ID, CHANNEL_ID, ReportType.MONTHLY, LocalDate.of(2026, 6, 1));
            var beforeDeletion = store.find(monthlyKey).orElseThrow();
            assertThat(beforeDeletion.state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            assertThat(beforeDeletion.pageProgress()).hasSizeGreaterThan(1);

            long deletedPage = beforeDeletion.pageProgress().getFirst().messageId();
            GATEWAY.deleteExternally(deletedPage);
            int visiblePagesAfterDeletion = GATEWAY.messageCount();
            long createCallsBeforeClosedReconciliation = GATEWAY.createCalls();

            MonthlyReportScheduler scheduler = context.getBean(MonthlyReportScheduler.class);
            CLOCK.set(MONTHLY_CATCH_UP_END);
            scheduler.reconcile();
            CLOCK.set(MONTHLY_CATCH_UP_END.plusSeconds(1));
            scheduler.reconcile();

            var afterClosedReconciliation = store.find(monthlyKey).orElseThrow();
            assertThat(afterClosedReconciliation.state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            assertThat(afterClosedReconciliation.pageProgress()).isEqualTo(beforeDeletion.pageProgress());
            assertThat(GATEWAY.messageCount()).isEqualTo(visiblePagesAfterDeletion);
            assertThat(GATEWAY.createCalls()).isEqualTo(createCallsBeforeClosedReconciliation);
        }
    }

    private static ConfigurableApplicationContext start(Class<?> testConfiguration) {
        return new SpringApplicationBuilder(testConfiguration, GridwordsBotApplication.class)
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
                "--gridwords.schedule.monthly-report=08:15",
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

    private void insertParticipant(long playerId, String name, LocalDate activeFrom) {
        OffsetDateTime now = OffsetDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO player (discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, ?, FALSE, FALSE, FALSE, ?, ?)
                """, playerId, name, now, now);
        jdbc.update("""
                INSERT INTO player_participation_period (player_id, active_from, inactive_from, created_at, updated_at)
                VALUES (?, ?, NULL, ?, ?)
                """, playerId, activeFrom, now, now);
    }

    private static void runConcurrently(Runnable... tasks) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> threads = Arrays.stream(tasks)
                .map(task -> new Thread(() -> {
                    try {
                        task.run();
                    } catch (Throwable thrown) {
                        failure.compareAndSet(null, thrown);
                    }
                }))
                .toList();
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        if (failure.get() != null) {
            throw new AssertionError("concurrent report reconciliation failed", failure.get());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CombinedReportTestConfiguration {
        @Bean
        @Primary
        Clock reportClock() {
            return CLOCK;
        }

        @Bean
        PeriodicReportMessageGateway testPeriodicReportMessageGateway() {
            return GATEWAY;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MonthlyReportTestConfiguration {
        @Bean
        @Primary
        Clock reportClock() {
            return CLOCK;
        }

        @Bean
        @Primary
        WeeklyReportReconciliationService testWeeklyReportReconciliationService() {
            return mock(WeeklyReportReconciliationService.class);
        }

        @Bean
        PeriodicReportMessageGateway testPeriodicReportMessageGateway() {
            return GATEWAY;
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant now) {
            this.now = new AtomicReference<>(now);
        }

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    static final class ThreadSafeGateway implements PeriodicReportMessageGateway {
        private final AtomicLong nextId = new AtomicLong(1_000L);
        private final Map<Long, ReportPage> messages = new ConcurrentHashMap<>();
        private final AtomicLong createCalls = new AtomicLong();

        void reset() {
            nextId.set(1_000L);
            messages.clear();
            createCalls.set(0);
        }

        int messageCount() {
            return messages.size();
        }

        long createCalls() {
            return createCalls.get();
        }

        void deleteExternally(long messageId) {
            messages.remove(messageId);
        }

        @Override
        public long create(long channelId, ReportPage page) {
            long id = nextId.getAndIncrement();
            messages.put(id, page);
            createCalls.incrementAndGet();
            return id;
        }

        @Override
        public void edit(long channelId, long messageId, ReportPage page) {
            if (messages.replace(messageId, page) == null) {
                throw new MissingMessageException("missing");
            }
        }

        @Override
        public PublishedReportPage load(long channelId, long messageId) {
            ReportPage page = messages.get(messageId);
            if (page == null) {
                throw new MissingMessageException("missing");
            }
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
            if (messages.remove(messageId) == null) {
                throw new MissingMessageException("missing");
            }
        }
    }
}
