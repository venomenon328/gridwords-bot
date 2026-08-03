package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.adapter.discord.reporting.JdaPeriodicReportMessageGateway;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportDeliveryService;
import de.venomenon.gridwordsbot.application.reporting.PeriodicReportRenderer;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import de.venomenon.gridwordsbot.port.out.ReportGameResultQuery;
import de.venomenon.gridwordsbot.port.out.ReportParticipantQuery;
import de.venomenon.gridwordsbot.port.out.ReportStreakHistoryQuery;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

class PeriodicReportDeliveryConfigurationTest {

    @Test
    void databaseProfileWiresDeliveryCoreWithAnInjectedTransportGatewayWhileDiscordIsDisabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            configureDiscord(context, false);
            registerProperties(context, false);
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.registerBean(PeriodicReportMessageGateway.class,
                    () -> mock(PeriodicReportMessageGateway.class));
            registerReportQueries(context);
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
            configureDiscord(context, true);
            registerProperties(context, true);
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.registerBean(JDA.class, () -> mock(JDA.class));
            registerReportQueries(context);
            context.register(PeriodicReportDeliveryConfiguration.class);
            context.refresh();

            assertThat(context.getBean(PeriodicReportMessageGateway.class))
                    .isInstanceOf(JdaPeriodicReportMessageGateway.class);
            assertThat(context.getBean(PeriodicReportDeliveryService.class)).isNotNull();
        }
    }

    private static void configureDiscord(
            AnnotationConfigApplicationContext context,
            boolean enabled) {
        context.getEnvironment().setActiveProfiles("database");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test-discord",
                Map.of("gridwords.discord.enabled", Boolean.toString(enabled))));
    }

    private static void registerProperties(
            AnnotationConfigApplicationContext context,
            boolean discordEnabled) {
        context.registerBean(
                GridwordsBotProperties.class,
                () -> new GridwordsBotProperties(
                        new GridwordsBotProperties.Discord(discordEnabled, "", 1L, 2L, List.of()),
                        new GridwordsBotProperties.Schedule(
                                LocalTime.of(18, 0),
                                LocalTime.of(23, 0),
                                LocalTime.of(8, 0),
                                LocalTime.of(8, 15),
                                ZoneId.of("Europe/Berlin")),
                        new GridwordsBotProperties.Storage(0)));
    }

    private static void registerReportQueries(AnnotationConfigApplicationContext context) {
        context.registerBean(ReportParticipantQuery.class,
                () -> mock(ReportParticipantQuery.class));
        context.registerBean(ReportGameResultQuery.class,
                () -> mock(ReportGameResultQuery.class));
        context.registerBean(ReportStreakHistoryQuery.class,
                () -> mock(ReportStreakHistoryQuery.class));
    }
}
