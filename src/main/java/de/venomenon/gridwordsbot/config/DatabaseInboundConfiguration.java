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
import de.venomenon.gridwordsbot.application.status.DailyStatusRefreshService;
import de.venomenon.gridwordsbot.application.submission.ProcessSharedResultService;
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
            ObjectProvider<DailyStatusRefreshService> statusProvider) {
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
                properties.zoneId(),
                players,
                submissions,
                canonicalProvider::getIfAvailable,
                statusProvider::getIfAvailable);
    }

    @Bean
    PersonalStatusUseCase personalStatusUseCase(
            Clock clock,
            GridwordsBotProperties properties,
            PlayerStore players,
            LatestValidSubmissionQuery latestSubmissions) {
        return new PersonalStatusService(clock, properties.zoneId(), players, latestSubmissions);
    }

    @Bean
    PlayerParticipationUseCase playerParticipationUseCase(
            Clock clock,
            GridwordsBotProperties properties,
            PlayerStore players,
            ObjectProvider<DailyStatusRefreshService> statusProvider) {
        return new PlayerParticipationService(
                clock,
                properties.zoneId(),
                players,
                statusProvider::getIfAvailable);
    }

    @Bean
    DailyResultDetailsUseCase dailyResultDetailsUseCase(DailyResultDetailsQuery query) {
        return new DailyResultDetailsService(query);
    }

    @Bean
    DailyStatusInteractionContextQuery dailyStatusInteractionContextQuery(
            CanonicalPublicationContextStore contexts) {
        return contexts::findStatusInteractionContext;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "false",
            matchIfMissing = true)
    ExcuseOpenUseCase disabledExcuseOpenUseCase() {
        return request -> ExcuseOpenUseCase.OpenResult.notOffered();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "true")
    ExcuseOpenUseCase enabledExcuseOpenUseCase(
            ExcuseStateStore states,
            GameResultStore gameResults,
            DailyResultDetailsQuery resultDetails,
            DailyStatusInteractionContextQuery statusContexts,
            de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog catalog,
            de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector selector,
            Clock clock,
            GridwordsBotProperties properties) {
        return new ExcuseOpenService(
                states,
                gameResults,
                resultDetails,
                statusContexts,
                catalog,
                selector,
                clock,
                properties.excuses().offerLifetime());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "false",
            matchIfMissing = true)
    ExcuseInteractionUseCase disabledExcuseInteractionUseCase() {
        return new ExcuseInteractionUseCase() {
            @Override public InteractionResult pick(PickRequest request) {
                return InteractionResult.rejected("NOT_OFFERED");
            }
            @Override public InteractionResult reroll(RerollRequest request) {
                return InteractionResult.rejected("NOT_OFFERED");
            }
            @Override public InteractionResult decline(DeclineRequest request) {
                return InteractionResult.rejected("NOT_OFFERED");
            }
        };
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "true")
    ExcuseInteractionUseCase enabledExcuseInteractionUseCase(
            ExcuseStateStore states,
            GameResultStore gameResults,
            de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog catalog,
            de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector selector,
            CanonicalRefreshWakeUp refreshWakeUp,
            Clock clock) {
        return new ExcuseInteractionService(states, gameResults, catalog, selector, refreshWakeUp, clock);
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
            LatestValidSubmissionQuery latestSubmissions) {
        return new PersonalStatusService(clock, properties.zoneId(), players, latestSubmissions);
    }

    @Bean
    PlayerParticipationUseCase playerParticipationUseCase(
            PlayerStore players,
            Clock clock,
            GridwordsBotProperties properties,
            ObjectProvider<DailyStatusRefreshService> statusProvider) {
        return new PlayerParticipationService(players, clock, properties.zoneId(), statusProvider::getIfAvailable);
    }

    @Bean
    DailyResultDetailsUseCase dailyResultDetailsUseCase(DailyResultDetailsQuery query) {
        return new DailyResultDetailsService(query);
    }

    @Bean
    DailyStatusInteractionContextQuery dailyStatusInteractionContextQuery(CanonicalPublicationContextStore store) {
        return store::findStatusInteractionContext;
    }

    @Bean
    @ConditionalOnMissingBean
    AttachmentContentLoader attachmentContentLoader() {
        return attachment -> {
            throw new UnsupportedOperationException("Attachment loading is not available");
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    JdaAttachmentContentLoader jdaAttachmentContentLoader(JDA jda) {
        return new JdaAttachmentContentLoader(jda);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    CanonicalMessageGateway canonicalMessageGateway(JDA jda) {
        return new JdaCanonicalMessageGateway(jda);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    SourceMessageDeletionGateway sourceMessageDeletionGateway(JDA jda) {
        return new JdaSourceMessageDeletionGateway(jda);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    PersonalStatusEmbedRenderer personalStatusEmbedRenderer() {
        return new PersonalStatusEmbedRenderer();
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    DiscordInboundListener discordInboundListener(
            JDA jda,
            ProcessSharedResultUseCase processSharedResultUseCase,
            ExecutorService inboundExecutor,
            GridwordsBotProperties properties) {
        return new DiscordInboundListener(jda, processSharedResultUseCase, inboundExecutor, properties.discord().channelId());
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    DiscordParticipationCommandListener discordParticipationCommandListener(
            JDA jda,
            PlayerParticipationUseCase participationUseCase,
            GridwordsBotProperties properties) {
        return new DiscordParticipationCommandListener(jda, participationUseCase, properties.discord().guildId());
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    DailyResultDetailsInteractionListener dailyResultDetailsInteractionListener(
            JDA jda,
            DailyResultDetailsUseCase useCase,
            GridwordsBotProperties properties) {
        return new DailyResultDetailsInteractionListener(jda, useCase, properties.discord().guildId());
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    ExcuseOpenInteractionListener excuseOpenInteractionListener(
            JDA jda,
            ExcuseOpenUseCase useCase,
            GridwordsBotProperties properties) {
        return new ExcuseOpenInteractionListener(jda, useCase, properties.discord().guildId());
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    ExcuseInteractionListener excuseInteractionListener(
            JDA jda,
            ExcuseInteractionUseCase useCase,
            GridwordsBotProperties properties) {
        return new ExcuseInteractionListener(jda, useCase, properties.discord().guildId());
    }

    @Bean
    @DependsOn("databaseInboundStartup")
    ApplicationRunner databaseInboundStartupRunner(ApplicationRunner databaseInboundStartup) {
        return databaseInboundStartup;
    }

    @Bean
    @ConditionalOnMissingBean(PublicationRetryScheduler.class)
    PublicationRetryScheduler publicationRetryScheduler(ThreadPoolTaskScheduler scheduler) {
        return scheduler::schedule;
    }

    @Bean
    DailyChannelCleanupService dailyChannelCleanupService(
            ChannelMessageRetirementStore retirementStore,
            CanonicalMessageGateway canonicalGateway,
            SourceMessageDeletionGateway sourceDeletionGateway,
            Clock clock,
            GridwordsBotProperties properties) {
        return new DailyChannelCleanupService(
                retirementStore,
                canonicalGateway,
                sourceDeletionGateway,
                clock,
                properties.zoneId());
    }

    @Bean
    GridWordsSourceDeletionService gridWordsSourceDeletionService(
            SourceDeletionRecoveryStore recoveryStore,
            SourceMessageDeletionGateway deletionGateway,
            CanonicalGridWordsPublicationService canonicalPublicationService) {
        return new GridWordsSourceDeletionService(recoveryStore, deletionGateway, canonicalPublicationService);
    }
}
