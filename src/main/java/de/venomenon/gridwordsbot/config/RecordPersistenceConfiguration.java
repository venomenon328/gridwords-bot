package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordAnnouncementStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordBootstrapStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordEventStore;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresRecordStateStore;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires persistence contracts only; evaluators, bootstrap scans, delivery workers, and Discord stay in later packages. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
class RecordPersistenceConfiguration {
    @Bean RecordStateStore recordStateStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordStateStore(jdbc, clock); }
    @Bean RecordEventStore recordEventStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordEventStore(jdbc, clock); }
    @Bean RecordBootstrapStore recordBootstrapStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordBootstrapStore(jdbc, clock); }
    @Bean RecordAnnouncementStore recordAnnouncementStore(JdbcTemplate jdbc, Clock clock) { return new PostgresRecordAnnouncementStore(jdbc, clock); }
}
