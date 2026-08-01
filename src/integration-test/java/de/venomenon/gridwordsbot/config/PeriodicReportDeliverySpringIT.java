package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.GridwordsBotApplication;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportDeliveryService;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportNoOp;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportParticipantSection;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportSharedSection;
import de.venomenon.gridwordsbot.domain.reporting.ReportDueAt;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportPersonalDayCounts;
import de.venomenon.gridwordsbot.domain.reporting.ReportPersonalStreaks;
import de.venomenon.gridwordsbot.domain.reporting.ReportPlayerGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportSharedDayCounts;
import de.venomenon.gridwordsbot.domain.reporting.ReportSharedStreaks;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakSnapshot;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PeriodicReportDeliverySpringIT {
    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final ReportPeriod PERIOD = new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
    private static final MutableClock CLOCK = new MutableClock(NOW);
    private static final RecordingGateway GATEWAY = new RecordingGateway();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        CLOCK.set(NOW);
        GATEWAY.reset();
        jdbc = new JdbcTemplate(dataSource());
        try (ConfigurableApplicationContext ignored = start()) {
            jdbc.update("DELETE FROM periodic_report_delivery_page");
            jdbc.update("DELETE FROM periodic_report_delivery");
        }
    }

    @AfterEach
    void clean() {
        GATEWAY.reset();
    }

    @Test
    void springContextDeliversSingleAndMultiplePagesAndNoOpWithRealStoreOutsideTransactions() {
        try (ConfigurableApplicationContext context = start()) {
            PeriodicReportDeliveryService delivery = context.getBean(PeriodicReportDeliveryService.class);
            PeriodicReportDeliveryStore store = context.getBean(PeriodicReportDeliveryStore.class);

            PeriodicReportDeliveryKey oneKey = key(101, ReportType.WEEKLY);
            delivery.deliver(oneKey, metadata(), report(1, ReportType.WEEKLY));
            assertThat(store.find(oneKey).orElseThrow().state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            assertThat(store.find(oneKey).orElseThrow().pageProgress()).hasSize(1);

            PeriodicReportDeliveryKey manyKey = key(102, ReportType.MONTHLY);
            delivery.deliver(manyKey, metadata(), report(30, ReportType.MONTHLY));
            assertThat(store.find(manyKey).orElseThrow().state()).isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            List<Integer> pageIndices = store.find(manyKey).orElseThrow().pageProgress().stream()
                    .map(progress -> progress.pageIndex()).toList();
            assertThat(pageIndices).hasSizeGreaterThan(1);
            assertThat(pageIndices).containsExactlyElementsOf(java.util.stream.IntStream.range(0, pageIndices.size()).boxed().toList());

            PeriodicReportDeliveryKey noOpKey = key(103, ReportType.WEEKLY);
            int createsBeforeNoOp = GATEWAY.createCalls();
            delivery.deliver(noOpKey, metadata(), new PeriodicReportNoOp(ReportType.WEEKLY, PERIOD));
            assertThat(store.find(noOpKey).orElseThrow().state()).isEqualTo(PeriodicReportDeliveryState.NO_OP);
            assertThat(GATEWAY.createCalls()).isEqualTo(createsBeforeNoOp);
            assertThat(GATEWAY.ioInsideTransaction()).isFalse();
        }
    }

    @Test
    void recoveryAfterUnknownCreateSurvivesANewSpringContextWithoutDuplicateMessages() {
        PeriodicReportDeliveryKey key = key(201, ReportType.WEEKLY);
        GATEWAY.unknownAfterPublishingNextCreate();
        try (ConfigurableApplicationContext first = start()) {
            first.getBean(PeriodicReportDeliveryService.class).deliver(key, metadata(), report(1, ReportType.WEEKLY));
            assertThat(first.getBean(PeriodicReportDeliveryStore.class).find(key).orElseThrow().state())
                    .isEqualTo(PeriodicReportDeliveryState.RETRYABLE);
            assertThat(GATEWAY.createCalls()).isOne();
        }

        CLOCK.set(NOW.plusSeconds(30));
        try (ConfigurableApplicationContext restarted = start()) {
            restarted.getBean(PeriodicReportDeliveryService.class).deliver(key, metadata(), report(1, ReportType.WEEKLY));
            assertThat(restarted.getBean(PeriodicReportDeliveryStore.class).find(key).orElseThrow().state())
                    .isEqualTo(PeriodicReportDeliveryState.SUCCEEDED);
            assertThat(GATEWAY.createCalls()).isOne();
            assertThat(GATEWAY.ioInsideTransaction()).isFalse();
        }
    }

    @Test
    void realStorePersistsRetryPermanentFailureAndExpiryAcrossReplays() {
        try (ConfigurableApplicationContext context = start()) {
            PeriodicReportDeliveryService delivery = context.getBean(PeriodicReportDeliveryService.class);
            PeriodicReportDeliveryStore store = context.getBean(PeriodicReportDeliveryStore.class);

            PeriodicReportDeliveryKey retryKey = key(301, ReportType.WEEKLY);
            GATEWAY.failNextCreate(new PeriodicReportMessageGateway.RetryableMessageException("temporary", null));
            delivery.deliver(retryKey, metadata(), report(1, ReportType.WEEKLY));
            assertThat(store.find(retryKey).orElseThrow().state()).isEqualTo(PeriodicReportDeliveryState.RETRYABLE);

            PeriodicReportDeliveryKey permanentKey = key(302, ReportType.WEEKLY);
            GATEWAY.failNextCreate(new PeriodicReportMessageGateway.PermanentMessageException("denied", null));
            delivery.deliver(permanentKey, metadata(), report(1, ReportType.WEEKLY));
            assertThat(store.find(permanentKey).orElseThrow().state()).isEqualTo(PeriodicReportDeliveryState.FAILED_PERMANENT);

            PeriodicReportDeliveryKey expiredKey = key(303, ReportType.WEEKLY);
            store.register(new de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration(
                    expiredKey, metadata(), Optional.empty()));
            CLOCK.set(metadata().catchUpEndsAt());
            delivery.deliver(expiredKey, metadata(), new PeriodicReportNoOp(ReportType.WEEKLY, PERIOD));
            assertThat(store.find(expiredKey).orElseThrow().state()).isEqualTo(PeriodicReportDeliveryState.EXPIRED);
            assertThat(GATEWAY.ioInsideTransaction()).isFalse();
        }
    }

    private static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(GridwordsBotApplication.class, DeliveryTestConfiguration.class)
                .web(WebApplicationType.NONE)
                .profiles("database")
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "gridwords.discord.enabled=false",
                        "gridwords.discord.guild-id=11",
                        "gridwords.discord.channel-id=12",
                        "gridwords.discord.admin-user-ids=101")
                .run(
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--gridwords.discord.enabled=false",
                        "--gridwords.discord.guild-id=11",
                        "--gridwords.discord.channel-id=12",
                        "--gridwords.discord.admin-user-ids=101");
    }

    private static DataSource dataSource() {
        org.springframework.jdbc.datasource.DriverManagerDataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static PeriodicReportDeliveryKey key(long channelId, ReportType type) {
        return new PeriodicReportDeliveryKey(1, channelId, type, PERIOD.startDate());
    }

    private static PeriodicReportDeliveryMetadata metadata() {
        return new PeriodicReportDeliveryMetadata(PERIOD,
                new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.NOON, ZoneOffset.UTC), NOW.plus(Duration.ofHours(72)));
    }

    private static PeriodicReport report(int participants, ReportType type) {
        List<PeriodicReportParticipantSection> sections = new ArrayList<>();
        for (int index = 0; index < participants; index++) {
            ReportGameStatistics grid = game(GameType.GRIDWORDS);
            ReportGameStatistics quad = game(GameType.QUADWORDS);
            sections.add(new PeriodicReportParticipantSection(
                    new ReportParticipant(index + 1L, "P" + index, PERIOD.startDate(), List.of(PERIOD.startDate())),
                    new ReportPlayerGameStatistics(index + 1L, grid, quad),
                    new ReportPersonalDayCounts(1, 0, 0, 0),
                    new ReportPersonalStreaks(streak(), streak(), streak(), streak(), streak())));
        }
        return new PeriodicReport(type, PERIOD, sections, new PeriodicReportSharedSection(
                new ReportSharedDayCounts(0, 0, 0), new ReportSharedStreaks(streak(), streak())));
    }

    private static ReportGameStatistics game(GameType type) {
        return new ReportGameStatistics(type, 1, 0, 0, 0, 1, Optional.empty(), 0, 0,
                Duration.ZERO, 0, Optional.empty());
    }

    private static ReportStreakSnapshot streak() {
        return new ReportStreakSnapshot(0, 0);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeliveryTestConfiguration {
        @Bean
        @Primary
        Clock deliveryTestClock() {
            return CLOCK;
        }

        @Bean
        PeriodicReportDeliveryService testPeriodicReportDeliveryService(
                PeriodicReportDeliveryStore store,
                PeriodicReportMessageGateway messages,
                de.venomenon.gridwordsbot.application.reporting.PeriodicReportRenderer renderer,
                Clock clock) {
            return new PeriodicReportDeliveryService(store, messages, renderer, clock);
        }

        @Bean
        PeriodicReportMessageGateway testPeriodicReportMessageGateway() {
            return GATEWAY;
        }
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) { this.instant = instant; }
        void set(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    static final class RecordingGateway implements PeriodicReportMessageGateway {
        private final AtomicLong nextId = new AtomicLong(1000);
        private final Map<Long, ReportPage> messages = new LinkedHashMap<>();
        private RuntimeException nextCreateFailure;
        private boolean unknownAfterCreate;
        private boolean ioInsideTransaction;
        private int createCalls;

        synchronized void reset() {
            nextId.set(1000);
            messages.clear();
            nextCreateFailure = null;
            unknownAfterCreate = false;
            ioInsideTransaction = false;
            createCalls = 0;
        }
        synchronized void failNextCreate(RuntimeException failure) { nextCreateFailure = failure; }
        synchronized void unknownAfterPublishingNextCreate() { unknownAfterCreate = true; }
        synchronized int createCalls() { return createCalls; }
        synchronized boolean ioInsideTransaction() { return ioInsideTransaction; }

        @Override
        public synchronized long create(long channelId, ReportPage page) {
            recordIo();
            if (nextCreateFailure != null) {
                RuntimeException failure = nextCreateFailure;
                nextCreateFailure = null;
                throw failure;
            }
            long id = nextId.getAndIncrement();
            messages.put(id, page);
            createCalls++;
            if (unknownAfterCreate) {
                unknownAfterCreate = false;
                throw new UnknownMessageException("outcome unknown", null);
            }
            return id;
        }

        @Override
        public synchronized void edit(long channelId, long messageId, ReportPage page) {
            recordIo();
            if (!messages.containsKey(messageId)) throw new MissingMessageException("missing");
            messages.put(messageId, page);
        }

        @Override
        public synchronized PublishedReportPage load(long channelId, long messageId) {
            recordIo();
            ReportPage page = messages.get(messageId);
            if (page == null) throw new MissingMessageException("missing");
            return new PublishedReportPage(messageId, page);
        }

        @Override
        public synchronized List<PublishedReportPage> findExactMatches(long channelId, ReportPage page) {
            recordIo();
            return messages.entrySet().stream().filter(entry -> entry.getValue().equals(page))
                    .map(entry -> new PublishedReportPage(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparingLong(PublishedReportPage::messageId)).toList();
        }

        @Override
        public synchronized void delete(long channelId, long messageId) {
            recordIo();
            if (messages.remove(messageId) == null) throw new MissingMessageException("missing");
        }

        private void recordIo() {
            ioInsideTransaction |= TransactionSynchronizationManager.isActualTransactionActive();
        }
    }
}