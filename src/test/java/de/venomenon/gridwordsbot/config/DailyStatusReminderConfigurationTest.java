package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.adapter.discord.status.JdaDailyStatusMessageGateway;
import de.venomenon.gridwordsbot.application.reminder.ReminderDeliveryService;
import de.venomenon.gridwordsbot.application.status.DailyStatusProjector;
import de.venomenon.gridwordsbot.application.status.DailyStatusRefreshService;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DailyStatusReminderConfigurationTest {
    @Test
    void databaseProfileWiresProjectorGatewaysServicesAndSchedulerBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("database");
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(JDA.class, () -> mock(JDA.class));
            context.registerBean(GameResultStore.class, () -> mock(GameResultStore.class));
            context.registerBean(PlayerStore.class, () -> mock(PlayerStore.class));
            context.registerBean(DailyStatusStore.class, () -> mock(DailyStatusStore.class));
            context.registerBean(GridwordsBotProperties.class, DailyStatusReminderConfigurationTest::properties);
            context.register(DailyStatusReminderConfiguration.class, SchedulingConfiguration.class,
                    DailyStatusReminderScheduler.class);
            context.refresh();

            assertThat(context.getBean(DailyStatusProjector.class)).isNotNull();
            assertThat(context.getBean(JdaDailyStatusMessageGateway.class)).isNotNull();
            assertThat(context.getBean(DailyStatusRefreshService.class)).isNotNull();
            assertThat(context.getBean(ReminderDeliveryService.class)).isNotNull();
            assertThat(context.getBean(DailyStatusReminderScheduler.class)).isNotNull();
        }
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(false, "unused", 11L, 12L, List.of()),
                new GridwordsBotProperties.Schedule(LocalTime.of(18, 0), LocalTime.of(23, 0),
                        LocalTime.of(8, 0), LocalTime.of(8, 15), ZoneId.of("Europe/Berlin")),
                new GridwordsBotProperties.Storage(24));
    }
}