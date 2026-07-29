package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceMessageReactionGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DatabaseInboundConfigurationTest {

    @Test
    void wiresCanonicalRecoveryWithJdaReactionAndSchedulerAdapters() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("database");
            context.registerBean("liquibase", Object.class, Object::new);
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(JDA.class, () -> mock(JDA.class));
            context.registerBean(GameResultStore.class, () -> mock(GameResultStore.class));
            context.registerBean(PlayerStore.class, () -> mock(PlayerStore.class));
            context.registerBean(SubmissionStore.class, () -> mock(SubmissionStore.class));
            context.registerBean(GridwordsBotProperties.class, DatabaseInboundConfigurationTest::properties);
            context.register(DatabaseInboundConfiguration.class);
            context.refresh();

            assertThat(context.getBean(CanonicalMessageGateway.class)).isNotNull();
            assertThat(context.getBean(SourceMessageReactionGateway.class)).isNotNull();
            assertThat(context.getBean(PublicationRetryScheduler.class)).isNotNull();
            assertThat(context.getBean(CanonicalGridWordsPublicationService.class)).isNotNull();
        }
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(false, "unused", 11L, 12L, List.of()),
                new GridwordsBotProperties.Players(
                        new GridwordsBotProperties.Player(1L, "Tobias"),
                        new GridwordsBotProperties.Player(2L, "Georgia")),
                new GridwordsBotProperties.Schedule(
                        LocalTime.of(8, 0),
                        LocalTime.of(18, 0),
                        LocalTime.of(8, 0),
                        LocalTime.of(8, 0),
                        ZoneId.of("Europe/Berlin")),
                new GridwordsBotProperties.Storage(24));
    }
}