package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.reporting.JdaPeriodicReportMessageGateway;
import de.venomenon.gridwordsbot.adapter.persistence.PostgresPeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportDeliveryService;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportRenderer;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import java.time.Clock;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires transport-neutral periodic report delivery only when its persistence profile is active. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
class PeriodicReportDeliveryConfiguration {

    @Bean
    PeriodicReportDeliveryStore periodicReportDeliveryStore(JdbcTemplate jdbc, Clock clock) {
        return new PostgresPeriodicReportDeliveryStore(jdbc, clock);
    }

    @Bean
    PeriodicReportRenderer periodicReportRenderer() {
        return new PeriodicReportRenderer();
    }

    @Bean
    @ConditionalOnBean(JDA.class)
    @ConditionalOnMissingBean(PeriodicReportMessageGateway.class)
    PeriodicReportMessageGateway periodicReportMessageGateway(JDA jda) {
        return new JdaPeriodicReportMessageGateway(jda);
    }

    @Bean
    @ConditionalOnBean(PeriodicReportMessageGateway.class)
    PeriodicReportDeliveryService periodicReportDeliveryService(
            PeriodicReportDeliveryStore store,
            PeriodicReportMessageGateway messages,
            PeriodicReportRenderer renderer,
            Clock clock) {
        return new PeriodicReportDeliveryService(store, messages, renderer, clock);
    }
}