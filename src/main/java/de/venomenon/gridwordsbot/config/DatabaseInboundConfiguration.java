package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.canonical.JdaCanonicalMessageGateway;
import de.venomenon.gridwordsbot.adapter.discord.canonical.JdaSourceMessageDeletionGateway;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.JdaAttachmentContentLoader;
import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.application.canonical.GridWordsSourceDeletionService;
import de.venomenon.gridwordsbot.application.submission.ProcessSharedResultService;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsImageParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.out.AttachmentContentLoader;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.CanonicalPublicationContextStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceDeletionRecoveryStore;
import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@Profile("database")
class DatabaseInboundConfiguration {
    @Bean ProcessSharedResultUseCase processSharedResultUseCase(Clock clock, GridwordsBotProperties properties, PlayerStore players, SubmissionStore submissions, ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider, ObjectProvider<AttachmentContentLoader> loaderProvider) {
        AttachmentContentLoader loader = loaderProvider.getIfAvailable(() -> attachment -> { throw new AttachmentContentLoader.RetryableAttachmentException("attachment loader is unavailable", null); });
        return new ProcessSharedResultService(new GridWordsShareParser(), new QuadWordsShareParser(), loader, new QuadWordsImageParser(), clock, properties.schedule().timeZone(), players, submissions, List.of(), sourceMessageId -> {
            CanonicalGridWordsPublicationService canonical = canonicalProvider.getIfAvailable();
            return canonical != null && canonical.publish(sourceMessageId);
        });
    }
    @Bean @ConditionalOnBean(JDA.class) AttachmentContentLoader attachmentContentLoader(JDA jda) { return new JdaAttachmentContentLoader(jda); }
    @Bean @ConditionalOnBean(JDA.class) CanonicalMessageGateway canonicalMessageGateway(JDA jda, ObjectProvider<CanonicalPublicationContextStore> contexts) { return new JdaCanonicalMessageGateway(jda, contexts.getIfAvailable(CanonicalPublicationContextStore::none)); }
    @Bean @ConditionalOnBean(JDA.class) SourceMessageDeletionGateway sourceMessageDeletionGateway(JDA jda) { return new JdaSourceMessageDeletionGateway(jda); }
    @Bean(destroyMethod = "shutdown") ThreadPoolTaskScheduler canonicalPublicationTaskScheduler() { ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("gridwords-canonical-retry-"); return scheduler; }
    @Bean PublicationRetryScheduler publicationRetryScheduler(ThreadPoolTaskScheduler scheduler) { return (at, action) -> scheduler.schedule(action, at); }
    @Bean @ConditionalOnBean(CanonicalMessageGateway.class) CanonicalGridWordsPublicationService canonicalGridWordsPublicationService(GameResultStore results, PlayerStore players, SubmissionStore submissions, CanonicalMessageGateway discord, Clock clock, GridwordsBotProperties properties, PublicationRetryScheduler retries, ObjectProvider<GridWordsSourceDeletionService> deletions) {
        return new CanonicalGridWordsPublicationService(results, players, submissions, discord, clock, properties.schedule().timeZone(), List.of(), retries, sourceMessageId -> { GridWordsSourceDeletionService deletion = deletions.getIfAvailable(); if (deletion != null) deletion.reconcileAfterCanonicalPublication(sourceMessageId); });
    }
    @Bean @ConditionalOnBean(SourceMessageDeletionGateway.class) GridWordsSourceDeletionService gridWordsSourceDeletionService(SubmissionStore submissions, SourceMessageDeletionGateway deletionGateway, Clock clock, PublicationRetryScheduler retries, ObjectProvider<SourceDeletionRecoveryStore> recovery) { return new GridWordsSourceDeletionService(submissions, deletionGateway, clock, retries, recovery.getIfAvailable(() -> ignored -> 0)); }
    @Bean @DependsOn("liquibase") ApplicationRunner databaseInboundStartup(ObjectProvider<JDA> jda, ObjectProvider<DiscordInboundListener> listener, ObjectProvider<CanonicalGridWordsPublicationService> canonical, ObjectProvider<GridWordsSourceDeletionService> deletion) { return new DatabaseInboundStartup(jda, listener, canonical, deletion); }
}