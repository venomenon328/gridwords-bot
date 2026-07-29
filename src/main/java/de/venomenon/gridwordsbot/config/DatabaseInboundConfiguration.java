package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.adapter.discord.canonical.JdaCanonicalMessageGateway;
import de.venomenon.gridwordsbot.adapter.discord.canonical.JdaSourceMessageReactionGateway;
import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceMessageReactionGateway;
import de.venomenon.gridwordsbot.application.submission.ConfiguredPlayer;
import de.venomenon.gridwordsbot.application.submission.ConfiguredPlayerSynchronizer;
import de.venomenon.gridwordsbot.application.submission.ProcessSharedResultService;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Wires database-backed inbound processing while leaving the offline gateway profile independent. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
class DatabaseInboundConfiguration {

    @Bean
    ProcessSharedResultUseCase processSharedResultUseCase(
            Clock clock,
            GridwordsBotProperties properties,
            PlayerStore playerStore,
            SubmissionStore submissionStore,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider) {
        return new ProcessSharedResultService(
                new GridWordsShareParser(), new QuadWordsShareParser(), clock, properties.schedule().timeZone(), playerStore,
                submissionStore, sourceMessageId -> { CanonicalGridWordsPublicationService canonical = canonicalProvider.getIfAvailable(); return canonical == null || canonical.publish(sourceMessageId); });
    }

    @Bean
    @ConditionalOnBean(JDA.class)
    CanonicalMessageGateway canonicalMessageGateway(JDA jda) {
        return new JdaCanonicalMessageGateway(jda);
    }

    @Bean
    @ConditionalOnBean(JDA.class)
    SourceMessageReactionGateway sourceMessageReactionGateway(JDA jda) {
        return new JdaSourceMessageReactionGateway(jda);
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskScheduler canonicalPublicationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("gridwords-canonical-retry-");
        return scheduler;
    }

    @Bean
    PublicationRetryScheduler publicationRetryScheduler(ThreadPoolTaskScheduler canonicalPublicationTaskScheduler) {
        return (at, action) -> canonicalPublicationTaskScheduler.schedule(action, at);
    }

    @Bean
    @ConditionalOnBean(CanonicalMessageGateway.class)
    CanonicalGridWordsPublicationService canonicalGridWordsPublicationService(
            GameResultStore results, PlayerStore players, SubmissionStore submissions, CanonicalMessageGateway discord,
            Clock clock, GridwordsBotProperties properties, PublicationRetryScheduler retryScheduler,
            SourceMessageReactionGateway reactionGateway) {
        return new CanonicalGridWordsPublicationService(results, players, submissions, discord, clock,
                properties.schedule().timeZone(), List.of(properties.players().first().userId(), properties.players().second().userId()),
                retryScheduler, reactionGateway);
    }

    @Bean
    ConfiguredPlayerSynchronizer configuredPlayerSynchronizer(
            GridwordsBotProperties properties, PlayerStore playerStore) {
        List<Long> administrators = properties.discord().adminUserIds();
        return new ConfiguredPlayerSynchronizer(List.of(
                configuredPlayer(properties.players().first(), administrators),
                configuredPlayer(properties.players().second(), administrators)), playerStore);
    }

    @Bean
    @DependsOn("liquibase")
    ApplicationRunner databaseInboundStartup(
            ConfiguredPlayerSynchronizer synchronizer,
            ObjectProvider<JDA> jdaProvider,
            ObjectProvider<DiscordInboundListener> listenerProvider,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider) {
        return new DatabaseInboundStartup(synchronizer, jdaProvider, listenerProvider, canonicalProvider);
    }

    private ConfiguredPlayer configuredPlayer(GridwordsBotProperties.Player player, List<Long> administrators) {
        return new ConfiguredPlayer(player.userId(), player.displayName(), administrators.contains(player.userId()));
    }
}
