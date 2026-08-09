package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.canonical.JdaCanonicalMessageGateway;
import de.venomenon.gridwordsbot.adapter.discord.canonical.JdaSourceMessageDeletionGateway;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DailyResultDetailsInteractionListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.ExcuseOpenInteractionListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.ExcuseInteractionListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordParticipationCommandListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.JdaAttachmentContentLoader;
import de.venomenon.gridwordsbot.adapter.discord.status.PersonalStatusEmbedRenderer;
import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.application.canonical.GridWordsSourceDeletionService;
import de.venomenon.gridwordsbot.application.cleanup.DailyChannelCleanupService;
import de.venomenon.gridwordsbot.application.player.PersonalStatusService;
import de.venomenon.gridwordsbot.application.player.PlayerParticipationService;
import de.venomenon.gridwordsbot.application.excuse.ExcuseInteractionService;
import de.venomenon.gridwordsbot.application.excuse.ExcuseOpenService;
import de.venomenon.gridwordsbot.application.excuse.ExcuseExpirationService;
import de.venomenon.gridwordsbot.application.status.DailyResultDetailsService;
import de.venomenon.gridwordsbot.application.status.DailyStatusProjector;
import de.venomenon.gridwordsbot.application.status.DailyStatusRefreshService;
import de.venomenon.gridwordsbot.application.submission.ProcessSharedResultService;
import de.venomenon.gridwordsbot.application.achievement.AchievementBootstrapCoordinator;
import de.venomenon.gridwordsbot.application.achievement.AchievementResultLifecycle;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsImageParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import de.venomenon.gridwordsbot.port.in.ExcuseOpenUseCase;
import de.venomenon.gridwordsbot.port.in.ExcuseInteractionUseCase;
import de.venomenon.gridwordsbot.port.in.ExcuseExpirationUseCase;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.out.AttachmentContentLoader;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.CanonicalPublicationContextStore;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.CanonicalRefreshWakeUp;
import de.venomenon.gridwordsbot.port.out.ChannelMessageRetirementStore;
import de.venomenon.gridwordsbot.port.out.DailyResultDetailsQuery;
import de.venomenon.gridwordsbot.port.out.DailyStatusInteractionContextQuery;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.LatestValidSubmissionQuery;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceDeletionRecoveryStore;
import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@Profile("database")
class DatabaseInboundConfiguration {
    @Bean
    ProcessSharedResultUseCase processSharedResultUseCase(
            Clock clock,
            GridwordsBotProperties properties,
            PlayerStore players,
            SubmissionStore submissions,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider,
            ObjectProvider<AttachmentContentLoader> loaderProvider,
            ObjectProvider<DailyStatusRefreshService> statusProvider,
            ObjectProvider<AchievementResultLifecycle> achievementLifecycleProvider) {
        AttachmentContentLoader loader = loaderProvider.getIfAvailable(() -> attachment -> {
            throw new AttachmentContentLoader.RetryableAttachmentException(
                    "attachment loader is unavailable", null);
        });
        return new ProcessSharedResultService(
                new GridWordsShareParser(),
                new QuadWordsShareParser(),
                loader,
                new QuadWordsImageParser(),
                clock,
                properties.schedule().timeZone(),
                properties.schedule().dailyCleanup(),
                players,
                submissions,
                sourceMessageId -> {
                    CanonicalGridWordsPublicationService canonical = canonicalProvider.getIfAvailable();
                    return canonical != null && canonical.publish(sourceMessageId);
                },
                properties.discord().adminUserIds()::contains,
                gameDate -> {
                    DailyStatusRefreshService status = statusProvider.getIfAvailable();
                    if (status != null) {
                        status.refresh(gameDate);
                    }
                }, outcome -> {
                    AchievementResultLifecycle lifecycle = achievementLifecycleProvider.getIfAvailable();
                    if (lifecycle != null) {
                        lifecycle.reconcileNormal(outcome);
                    }
                });
    }

    @Bean
    PlayerParticipationUseCase playerParticipationUseCase(
            Clock clock,
            GridwordsBotProperties properties,
            PlayerStore players,
            ObjectProvider<DailyStatusRefreshService> statusProvider) {
        return new PlayerParticipationService(
                players,
                clock,
                properties.schedule().timeZone(),
                Set.copyOf(properties.discord().adminUserIds()),
                gameDate -> {
                    DailyStatusRefreshService status = statusProvider.getIfAvailable();
                    if (status != null) {
                        status.refreshExisting(gameDate);
                    }
                });
    }

    @Bean
    ZoneId businessZone(GridwordsBotProperties properties) {
        return properties.schedule().timeZone();
    }

    @Bean
    DailyResultDetailsUseCase dailyResultDetailsUseCase(
            DailyStatusInteractionContextQuery contexts,
            DailyResultDetailsQuery results,
            RecordDefinitionCatalog recordCatalog,
            AchievementDefinitionCatalog achievementCatalog,
            AchievementEmojiResolver achievementEmojis) {
        return new DailyResultDetailsService(contexts, results, recordCatalog, achievementCatalog, achievementEmojis);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "false",
            matchIfMissing = true)
    ExcuseOpenUseCase disabledExcuseOpenUseCase() {
        return request -> new ExcuseOpenUseCase.Rejected(ExcuseOpenUseCase.Reason.FEATURE_DISABLED);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "true")
    ExcuseOpenUseCase enabledExcuseOpenUseCase(
            GridwordsBotProperties properties,
            GameResultStore results,
            PlayerStore players,
            SubmissionStore submissions,
            ExcuseStateStore states,
            de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog catalog,
            de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector selector,
            Clock clock,
            ExcuseExpirationUseCase expirations) {
        return new ExcuseOpenService(
                properties.discord().guildId(), properties.discord().channelId(), results, players, submissions, states,
                new de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy(
                        de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityThresholds.defaults()),
                catalog, selector, clock, expirations);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "false",
            matchIfMissing = true)
    ExcuseInteractionUseCase disabledExcuseInteractionUseCase() {
        return new ExcuseInteractionUseCase() {
            private final Rejected disabled = new Rejected(Reason.FEATURE_DISABLED);
            @Override public Result openStyleMenu(ActionRequest request) { return disabled; }
            @Override public Result selectStyle(StyleRequest request) { return disabled; }
            @Override public Result pick(PickRequest request) { return disabled; }
            @Override public Result decline(ActionRequest request) { return disabled; }
        };
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "true")
    ExcuseInteractionUseCase enabledExcuseInteractionUseCase(
            GridwordsBotProperties properties,
            GameResultStore results,
            PlayerStore players,
            SubmissionStore submissions,
            ExcuseStateStore states,
            de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog catalog,
            de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector selector,
            Clock clock,
            CanonicalRefreshWakeUp refreshWakeUp,
            ExcuseExpirationUseCase expirations) {
        return new ExcuseInteractionService(
                properties.discord().guildId(), properties.discord().channelId(), results, players, submissions, states,
                new de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy(
                        de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityThresholds.defaults()),
                catalog, selector, clock, refreshWakeUp, expirations);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "false",
            matchIfMissing = true)
    ExcuseExpirationUseCase disabledExcuseExpirationUseCase() {
        return new ExcuseExpirationUseCase() {
            @Override public int reconcile() { return 0; }
            @Override public boolean expireIfDue(long gameResultId) { return false; }
        };
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "true")
    ExcuseExpirationUseCase enabledExcuseExpirationUseCase(
            ExcuseStateStore states,
            CanonicalRefreshWakeUp refreshWakeUp,
            Clock clock,
            GridwordsBotProperties properties) {
        return new ExcuseExpirationService(
                states, refreshWakeUp, clock,
                properties.excuses().expirationPageSize(), properties.excuses().expirationMaxPages());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "true")
    de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector excuseSelector() {
        return new de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector(
                new de.venomenon.gridwordsbot.domain.excuse.ExcuseTemplateRenderer(),
                new de.venomenon.gridwordsbot.domain.excuse.JavaExcuseRandom(new Random()));
    }

    @Bean
    CanonicalRefreshWakeUp canonicalRefreshWakeUp(ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider) {
        return gameResultId -> {
            CanonicalGridWordsPublicationService canonical = canonicalProvider.getIfAvailable();
            if (canonical != null) {
                canonical.wakeUpCanonicalRefresh(gameResultId);
            }
        };
    }

    @Bean
    PersonalStatusUseCase personalStatusUseCase(
            Clock clock,
            GridwordsBotProperties properties,
            PlayerStore players,
            LatestValidSubmissionQuery submissions,
            DailyStatusProjector dailyStatus) {
        return new PersonalStatusService(
                players,
                submissions,
                dailyStatus,
                clock,
                properties.schedule().timeZone());
    }

    @Bean
    PersonalStatusEmbedRenderer personalStatusEmbedRenderer(GridwordsBotProperties properties) {
        return new PersonalStatusEmbedRenderer(properties.schedule().timeZone());
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    DiscordParticipationCommandListener discordParticipationCommandListener(
            GridwordsBotProperties properties,
            PlayerParticipationUseCase commands,
            PersonalStatusUseCase personalStatus,
            PersonalStatusEmbedRenderer personalStatusRenderer) {
        return new DiscordParticipationCommandListener(
                properties, commands, personalStatus, personalStatusRenderer);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    DailyResultDetailsInteractionListener dailyResultDetailsInteractionListener(
            GridwordsBotProperties properties,
            ExecutorService discordInboundExecutor,
            DailyResultDetailsUseCase details) {
        return new DailyResultDetailsInteractionListener(properties, discordInboundExecutor, details);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    ExcuseOpenInteractionListener excuseOpenInteractionListener(
            GridwordsBotProperties properties,
            ExecutorService discordInboundExecutor,
            ExcuseOpenUseCase open) {
        return new ExcuseOpenInteractionListener(properties, discordInboundExecutor, open);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    ExcuseInteractionListener excuseInteractionListener(
            GridwordsBotProperties properties,
            ExecutorService discordInboundExecutor,
            ExcuseInteractionUseCase interactions) {
        return new ExcuseInteractionListener(properties, discordInboundExecutor, interactions);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    AttachmentContentLoader attachmentContentLoader(JDA jda) {
        return new JdaAttachmentContentLoader(jda);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    CanonicalMessageGateway canonicalMessageGateway(
            JDA jda,
            ObjectProvider<CanonicalPublicationContextStore> contexts) {
        return new JdaCanonicalMessageGateway(
                jda,
                contexts.getIfAvailable(CanonicalPublicationContextStore::none));
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    SourceMessageDeletionGateway sourceMessageDeletionGateway(JDA jda) {
        return new JdaSourceMessageDeletionGateway(jda);
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskScheduler canonicalPublicationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("gridwords-canonical-retry-");
        return scheduler;
    }

    @Bean
    PublicationRetryScheduler publicationRetryScheduler(ThreadPoolTaskScheduler scheduler) {
        return (at, action) -> scheduler.schedule(action, at);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    CanonicalGridWordsPublicationService canonicalGridWordsPublicationService(
            GameResultStore results,
            PlayerStore players,
            SubmissionStore submissions,
            CanonicalMessageGateway discord,
            Clock clock,
            GridwordsBotProperties properties,
            PublicationRetryScheduler retries,
            ObjectProvider<GridWordsSourceDeletionService> deletions,
            ChannelMessageRetirementStore retirement,
            ObjectProvider<ExcuseStateStore> excuses) {
        return new CanonicalGridWordsPublicationService(
                results,
                players,
                submissions,
                discord,
                clock,
                properties.schedule().timeZone(),
                retries,
                sourceMessageId -> {
                    GridWordsSourceDeletionService deletion = deletions.getIfAvailable();
                    if (deletion != null) {
                        deletion.reconcileAfterCanonicalPublication(sourceMessageId);
                    }
                }, excuses.getIfAvailable(CanonicalGridWordsPublicationService::noExcuses),
                properties.schedule().dailyCleanup())
                .withRetirementFence(retirement);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    GridWordsSourceDeletionService gridWordsSourceDeletionService(
            SubmissionStore submissions,
            SourceMessageDeletionGateway deletionGateway,
            Clock clock,
            PublicationRetryScheduler retries,
            ObjectProvider<SourceDeletionRecoveryStore> recovery,
            GameResultStore results,
            GridwordsBotProperties properties) {
        return new GridWordsSourceDeletionService(
                submissions,
                deletionGateway,
                clock,
                retries,
                recovery.getIfAvailable(() -> ignored -> 0),
                results,
                properties.schedule().timeZone(),
                properties.schedule().dailyCleanup());
    }

    @Bean
    @DependsOn("liquibase")
    ApplicationRunner databaseInboundStartup(
            ObjectProvider<JDA> jda,
            ObjectProvider<DiscordInboundListener> listener,
            ObjectProvider<DiscordParticipationCommandListener> commands,
            ObjectProvider<DailyResultDetailsInteractionListener> resultDetails,
            ObjectProvider<ExcuseOpenInteractionListener> excuseOpen,
            ObjectProvider<ExcuseInteractionListener> excuseInteractions,
            ObjectProvider<ExcuseExpirationUseCase> excuseExpirations,
            ObjectProvider<CanonicalGridWordsPublicationService> canonical,
            ObjectProvider<GridWordsSourceDeletionService> deletion,
            ObjectProvider<DailyChannelCleanupService> cleanup,
            ObjectProvider<AchievementBootstrapCoordinator> achievementBootstrap,
            ObjectProvider<AchievementResultLifecycle> achievementLifecycle,
            GridwordsBotProperties properties) {
        return new DatabaseInboundStartup(
                jda, listener, commands, resultDetails, excuseOpen, excuseInteractions, excuseExpirations, canonical, deletion, cleanup,
                achievementBootstrap, achievementLifecycle, properties);
    }
}
