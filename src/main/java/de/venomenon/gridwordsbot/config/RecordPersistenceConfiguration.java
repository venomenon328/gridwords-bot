package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordAnnouncementStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordBootstrapStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordEventStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordStateStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordHistoryQuery;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordHistoryQuery;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.application.record.RecordBootstrapCoordinator;
import de.venomenon.gridwordsbot.application.record.RecordStateService;
import de.venomenon.gridwordsbot.application.record.RecordStateReadService;
import de.venomenon.gridwordsbot.application.record.RecordBootstrapReadService;
import de.venomenon.gridwordsbot.adapter.observability.MicrometerRecordBootstrapMetrics;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapMetrics;
import de.venomenon.gridwordsbot.port.out.RecordTransactionRunner;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.ApplicationRunner;
import io.micrometer.core.instrument.MeterRegistry;

/** Wires persistence contracts only; evaluators, bootstrap scans, delivery workers, and Discord stay in later packages. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
class RecordPersistenceConfiguration {
    @Bean RecordStateStore recordStateStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordStateStore(jdbc, clock); }
    @Bean RecordEventStore recordEventStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordEventStore(jdbc, clock); }
    @Bean RecordBootstrapStore recordBootstrapStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordBootstrapStore(jdbc, clock); }
    @Bean RecordAnnouncementStore recordAnnouncementStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordAnnouncementStore(jdbc, clock); }
    @Bean RecordHistoryQuery recordHistoryQuery(JdbcTemplate jdbc) { return new PostgresRecordHistoryQuery(jdbc); }
    @Bean RecordDefinitionCatalog recordDefinitionCatalog() { return RecordDefinitionCatalog.recordsV1(); }
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
    @Bean RecordBootstrapCoordinator recordBootstrapCoordinator(RecordBootstrapStore bootstraps, RecordHistoryQuery history,
            RecordStateService states, RecordDefinitionCatalog catalog, Clock clock, GridwordsBotProperties properties,
            RecordBootstrapMetrics metrics) {
        return new RecordBootstrapCoordinator(bootstraps, history, states, catalog, clock,
                properties.records().bootstrapLeaseDuration(), properties.records().bootstrapRetryBackoff(), metrics);
    }
    @Bean RecordStateReadService recordStateReadService(RecordStateStore states) { return new RecordStateReadService(states); }
    @Bean RecordBootstrapReadService recordBootstrapReadService(RecordBootstrapStore bootstraps) { return new RecordBootstrapReadService(bootstraps); }
    @Bean ApplicationRunner recordBootstrapStartupRunner(RecordBootstrapCoordinator coordinator, GridwordsBotProperties properties) {
        return arguments -> coordinator.run(properties.discord().guildId());
    }
}
