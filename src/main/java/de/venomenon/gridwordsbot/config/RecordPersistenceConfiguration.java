package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordAnnouncementStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordBootstrapStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordEventStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordLiveEvaluationStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordDayCloseStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordStateStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordHistoryQuery;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordLiveHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordLiveEvaluationStore;
import de.venomenon.gridwordsbot.port.out.RecordDayCloseStore;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordLiveHistoryQuery;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator;
import de.venomenon.gridwordsbot.application.record.RecordStateService;
import de.venomenon.gridwordsbot.application.record.RecordStateReadService;
import de.venomenon.gridwordsbot.application.record.RecordBootstrapReadService;
import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationProcessor;
import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationCoordinator;
import de.venomenon.gridwordsbot.application.record.RecordDayCloseService;
import de.venomenon.gridwordsbot.port.in.RecordDayCloseUseCase;
import de.venomenon.gridwordsbot.application.record.RecordLiveEvaluationMetrics;
import de.venomenon.gridwordsbot.adapter.observability.MicrometerRecordBootstrapMetrics;
import de.venomenon.gridwordsbot.adapter.observability.MicrometerRecordLiveEvaluationMetrics;
import de.venomenon.gridwordsbot.adapter.observability.MicrometerRecordAnnouncementDeliveryMetrics;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapMetrics;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementMessageGateway;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.application.record.RecordAnnouncementDeliveryCoordinator;
import de.venomenon.gridwordsbot.application.record.RecordAnnouncementDeliveryMetrics;
import de.venomenon.gridwordsbot.application.record.RecordAnnouncementRenderer;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.ApplicationRunner;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/** Wires record persistence, bootstrap and durable live-evaluation runtime coordination. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
class RecordPersistenceConfiguration {
    @Bean RecordStateStore recordStateStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordStateStore(jdbc, clock); }
    @Bean RecordEventStore recordEventStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordEventStore(jdbc, clock); }
    @Bean RecordBootstrapStore recordBootstrapStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordBootstrapStore(jdbc, clock); }
    @Bean RecordLiveEvaluationStore recordLiveEvaluationStore(JdbcTemplate jdbc, Clock clock) {
        return new PostgresRecordLiveEvaluationStore(jdbc, clock);
    }
    @Bean RecordDayCloseStore recordDayCloseStore(JdbcTemplate jdbc, Clock clock) {
        return new PostgresRecordDayCloseStore(jdbc, clock);
    }
    @Bean RecordAnnouncementStore recordAnnouncementStore(JdbcTemplate jdbc, Clock clock, GridwordsBotProperties properties) {
        return new PostgresRecordAnnouncementStore(jdbc, clock, properties.records().publicAnnouncementsEnabled());
    }
    @Bean RecordHistoryQuery recordHistoryQuery(JdbcTemplate jdbc) { return new PostgresRecordHistoryQuery(jdbc); }
    @Bean RecordLiveHistoryQuery recordLiveHistoryQuery(JdbcTemplate jdbc) { return new PostgresRecordLiveHistoryQuery(jdbc); }
    @Bean RecordDefinitionCatalog recordDefinitionCatalog() { return RecordDefinitionCatalog.recordsV2(); }
    @Bean RecordTransactionRunner recordTransactionRunner(TransactionTemplate transactions) {
        return new RecordTransactionRunner() { @Override public <T> T inTransaction(java.util.function.Supplier<T> work) {
            return transactions.execute(status -> work.get());
        }};
    }
    @Bean RecordStateService recordStateService(RecordStateStore states, RecordEventStore events, RecordTransactionRunner transactions) {
        return new RecordStateService(states, events, transactions, recordDefinitionCatalog());
    }
    @Bean RecordBootstrapMetrics recordBootstrapMetrics(MeterRegistry registry) { return new MicrometerRecordBootstrapMetrics(registry); }
    @Bean(name = "recordBootstrapPollDelayMillis")
    long recordBootstrapPollDelayMillis(GridwordsBotProperties properties) {
        return properties.records().bootstrapPollDelay().toMillis();
    }
    @Bean(name = "recordLiveEvaluationPollDelayMillis")
    long recordLiveEvaluationPollDelayMillis(GridwordsBotProperties properties) {
        return properties.records().liveEvaluationPollDelay().toMillis();
    }
    @Bean(name = "recordAnnouncementPollDelayMillis")
    long recordAnnouncementPollDelayMillis(GridwordsBotProperties properties) {
        return properties.records().announcementPollDelay().toMillis();
    }
    @Bean RecordBootstrapCoordinator recordBootstrapCoordinator(RecordBootstrapStore bootstraps, RecordHistoryQuery history,
            RecordStateService states, RecordDefinitionCatalog catalog, Clock clock, GridwordsBotProperties properties,
            RecordBootstrapMetrics metrics) {
        return new RecordBootstrapCoordinator(bootstraps, history, states, catalog, clock,
                properties.records().bootstrapLeaseDuration(), properties.records().bootstrapRetryBackoff(), metrics);
    }
    @Bean RecordStateReadService recordStateReadService(RecordStateStore states) { return new RecordStateReadService(states); }
    @Bean RecordBootstrapReadService recordBootstrapReadService(RecordBootstrapStore bootstraps) { return new RecordBootstrapReadService(bootstraps); }
    @Bean RecordLiveEvaluationProcessor recordLiveEvaluationProcessor(
            RecordLiveEvaluationStore work, RecordLiveHistoryQuery history, RecordBootstrapReadService bootstrap,
            RecordStateService states, RecordEventStore events, RecordAnnouncementStore announcements,
            RecordTransactionRunner transactions, RecordDefinitionCatalog catalog, Clock clock,
            GridwordsBotProperties properties) {
        return new RecordLiveEvaluationProcessor(work, history, bootstrap, states, events, announcements,
                transactions, catalog, clock, properties.discord().channelId());
    }
    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService recordLiveEvaluationHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "record-live-evaluation-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
    @Bean RecordLiveEvaluationMetrics recordLiveEvaluationMetrics(MeterRegistry registry) {
        return new MicrometerRecordLiveEvaluationMetrics(registry);
    }
    @Bean RecordAnnouncementDeliveryMetrics recordAnnouncementDeliveryMetrics(MeterRegistry registry) {
        return new MicrometerRecordAnnouncementDeliveryMetrics(registry);
    }
    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService recordAnnouncementHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "record-announcement-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
    @Bean
    @ConditionalOnBean(RecordAnnouncementMessageGateway.class)
    RecordAnnouncementDeliveryCoordinator recordAnnouncementDeliveryCoordinator(
            RecordAnnouncementStore announcements, RecordEventStore events, PlayerStore players,
            RecordAnnouncementMessageGateway messages, Clock clock, RecordAnnouncementDeliveryMetrics metrics,
            ScheduledExecutorService recordAnnouncementHeartbeatExecutor, GridwordsBotProperties properties) {
        GridwordsBotProperties.Records records = properties.records();
        return new RecordAnnouncementDeliveryCoordinator(announcements, events, players, messages,
                new RecordAnnouncementRenderer(), clock, records.announcementLeaseDuration(),
                records.announcementHeartbeatInterval(), records.announcementInitialRetryBackoff(),
                records.announcementMaxRetryBackoff(), recordAnnouncementHeartbeatExecutor,
                records.publicAnnouncementsEnabled(), metrics);
    }
    @Bean RecordLiveEvaluationCoordinator recordLiveEvaluationCoordinator(
            RecordLiveEvaluationStore work, RecordLiveEvaluationProcessor processor, Clock clock,
            ScheduledExecutorService recordLiveEvaluationHeartbeatExecutor, RecordLiveEvaluationMetrics metrics,
            GridwordsBotProperties properties) {
        GridwordsBotProperties.Records records = properties.records();
        return new RecordLiveEvaluationCoordinator(work, processor, clock,
                records.liveEvaluationLeaseDuration(), records.liveEvaluationHeartbeatInterval(),
                records.liveEvaluationInitialRetryBackoff(), records.liveEvaluationMaxRetryBackoff(),
                recordLiveEvaluationHeartbeatExecutor, metrics);
    }
    @Bean RecordDayCloseUseCase recordDayCloseUseCase(
            RecordDayCloseStore work,
            RecordHistoryQuery history,
            RecordBootstrapReadService bootstrap,
            RecordStateService states,
            RecordEventStore events,
            RecordAnnouncementStore announcements,
            RecordTransactionRunner transactions,
            RecordDefinitionCatalog catalog,
            Clock clock,
            GridwordsBotProperties properties) {
        return new RecordDayCloseService(work, history, bootstrap, states, events, announcements, transactions,
                catalog, clock, properties.discord().channelId());
    }
    @Bean ApplicationRunner recordBootstrapStartupRunner(RecordBootstrapCoordinator coordinator, GridwordsBotProperties properties) {
        return arguments -> coordinator.run(properties.discord().guildId());
    }
    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.records", name = "live-evaluation-enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner recordLiveEvaluationStartupRunner(RecordLiveEvaluationCoordinator coordinator) {
        return arguments -> coordinator.runNext();
    }
    @Bean
    @ConditionalOnBean(RecordAnnouncementDeliveryCoordinator.class)
    ApplicationRunner recordAnnouncementDeliveryStartupRunner(RecordAnnouncementDeliveryCoordinator coordinator) {
        return arguments -> coordinator.runNext();
    }
}
