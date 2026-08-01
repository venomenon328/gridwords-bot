package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.adapter.discord.reporting.JdaPeriodicReportMessageGateway;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportDeliveryService;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportRenderer;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import java.time.Clock;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class PeriodicReportDeliveryConfigurationTest {

    @Test
    void databaseProfileWiresDeliveryCoreWithAnInjectedTransportGatewayWithoutStartingJda() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("database");
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.registerBean(PeriodicReportMessageGateway.class, () -> mock(PeriodicReportMessageGateway.class));
            context.register(PeriodicReportDeliveryConfiguration.class);
            context.refresh();

            assertThat(context.getBean(PeriodicReportDeliveryStore.class)).isNotNull();
            assertThat(context.getBean(PeriodicReportRenderer.class)).isNotNull();
            assertThat(context.getBean(PeriodicReportDeliveryService.class)).isNotNull();
            assertThat(context.getBeansOfType(JDA.class)).isEmpty();
        }
    }

    @Test
    void databaseProfileWiresTheJdaGatewayAndDeliveryServiceWhenJdaIsProvided() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("database");
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.registerBean(JDA.class, () -> mock(JDA.class));
            context.register(PeriodicReportDeliveryConfiguration.class);
            context.refresh();

            assertThat(context.getBean(PeriodicReportMessageGateway.class)).isInstanceOf(JdaPeriodicReportMessageGateway.class);
            assertThat(context.getBean(PeriodicReportDeliveryService.class)).isNotNull();
        }
    }
}