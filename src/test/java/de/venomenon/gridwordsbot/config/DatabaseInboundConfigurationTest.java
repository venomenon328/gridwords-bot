package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.application.canonical.GridWordsSourceDeletionService;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.ChannelMessageRetirementStore;
import de.venomenon.gridwordsbot.port.out.DailyResultDetailsQuery;
import de.venomenon.gridwordsbot.port.out.DailyStatusInteractionContextQuery;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.LatestValidSubmissionQuery;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class DatabaseInboundConfigurationTest {

    @Test
    void wiresCanonicalPublicationDeletionAndDiscordAdaptersWhenEnabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("database");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test-discord",
                    Map.of("gridwords.discord.enabled", "true")));
            context.registerBean("liquibase", Object.class, Object::new);
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(JDA.class, () -> mock(JDA.class));
            context.registerBean(ExecutorService.class, () -> mock(ExecutorService.class));
            context.registerBean(GameResultStore.class, () -> mock(GameResultStore.class));
            context.registerBean(LatestValidSubmissionQuery.class,
                    () -> mock(LatestValidSubmissionQuery.class));
            context.registerBean(DailyStatusInteractionContextQuery.class,
                    () -> mock(DailyStatusInteractionContextQuery.class));
            context.registerBean(DailyResultDetailsQuery.class,
                    () -> mock(DailyResultDetailsQuery.class));
            context.registerBean(PlayerStore.class, () -> mock(PlayerStore.class));
            context.registerBean(SubmissionStore.class, () -> mock(SubmissionStore.class));
            context.registerBean(ChannelMessageRetirementStore.class,
                    () -> mock(ChannelMessageRetirementStore.class));
            context.registerBean(GridwordsBotProperties.class,
                    DatabaseInboundConfigurationTest::properties);
            context.register(DatabaseInboundConfiguration.class);
            context.refresh();

            assertThat(context.getBean(CanonicalMessageGateway.class)).isNotNull();
            assertThat(context.getBean(SourceMessageDeletionGateway.class)).isNotNull();
            assertThat(context.getBean(GridWordsSourceDeletionService.class)).isNotNull();
            assertThat(context.getBean(PublicationRetryScheduler.class)).isNotNull();
            assertThat(context.getBean(CanonicalGridWordsPublicationService.class)).isNotNull();
        }
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "unused", 11L, 12L, List.of()),
                new GridwordsBotProperties.Schedule(
                        LocalTime.of(8, 0),
                        LocalTime.of(18, 0),
                        LocalTime.of(8, 0),
                        LocalTime.of(8, 15),
                        ZoneId.of("Europe/Berlin")),
                new GridwordsBotProperties.Storage(24));
    }
}
