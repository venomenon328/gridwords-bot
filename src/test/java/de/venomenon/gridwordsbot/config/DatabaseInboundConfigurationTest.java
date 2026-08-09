package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.application.canonical.GridWordsSourceDeletionService;
import de.venomenon.gridwordsbot.adapter.discord.inbound.ExcuseOpenInteractionListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.ExcuseInteractionListener;
import de.venomenon.gridwordsbot.application.excuse.ExcuseInteractionService;
import de.venomenon.gridwordsbot.application.excuse.ExcuseOpenService;
import de.venomenon.gridwordsbot.application.status.DailyStatusProjector;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.port.in.ExcuseInteractionUseCase;
import de.venomenon.gridwordsbot.port.in.ExcuseOpenUseCase;
import de.venomenon.gridwordsbot.port.in.ExcuseExpirationUseCase;
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
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
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
            detailPresentationBeans(context);
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
            assertThat(context.getBean(ExcuseOpenInteractionListener.class)).isNotNull();
            assertThat(context.getBean(ExcuseOpenUseCase.class).open(
                    new ExcuseOpenUseCase.Request(11L, 12L, 13L, 14L, 15L)))
                    .isEqualTo(new ExcuseOpenUseCase.Rejected(ExcuseOpenUseCase.Reason.FEATURE_DISABLED));
        }
    }

    @Test
    void enablesTheCompletedEphemeralFlowOnlyForAnExplicitTestFeatureFlag() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("database");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test-excuses",
                    Map.of(
                            "gridwords.discord.enabled", "true",
                            "gridwords.excuse-generator.contextual-enabled", "true")));
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
            detailPresentationBeans(context);
            context.registerBean(PlayerStore.class, () -> mock(PlayerStore.class));
            context.registerBean(SubmissionStore.class, () -> mock(SubmissionStore.class));
            context.registerBean(ExcuseStateStore.class, () -> mock(ExcuseStateStore.class));
            context.registerBean(ExcuseCatalog.class, () -> mock(ExcuseCatalog.class));
            context.registerBean(ChannelMessageRetirementStore.class,
                    () -> mock(ChannelMessageRetirementStore.class));
            context.registerBean(GridwordsBotProperties.class,
                    DatabaseInboundConfigurationTest::properties);
            context.register(DatabaseInboundConfiguration.class);
            context.refresh();

            assertThat(context.getBean(ExcuseOpenUseCase.class)).isInstanceOf(ExcuseOpenService.class);
            assertThat(context.getBean(ExcuseInteractionUseCase.class)).isInstanceOf(ExcuseInteractionService.class);
            assertThat(context.getBean(ExcuseExpirationUseCase.class)).isNotNull();
            assertThat(context.getBean(ExcuseInteractionListener.class)).isNotNull();
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

    private static void detailPresentationBeans(AnnotationConfigApplicationContext context) {
        context.registerBean(RecordDefinitionCatalog.class, RecordDefinitionCatalog::recordsV1);
        context.registerBean(AchievementDefinitionCatalog.class, AchievementDefinitionCatalog::achievementsV1);
        context.registerBean(AchievementEmojiResolver.class, AchievementEmojiResolver::unicodeOnly);
        context.registerBean(DailyStatusProjector.class, () -> mock(DailyStatusProjector.class));
    }
}
