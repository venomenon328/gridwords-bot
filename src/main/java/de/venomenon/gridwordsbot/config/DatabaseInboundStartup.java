package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DailyResultDetailsInteractionListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.ExcuseOpenInteractionListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.ExcuseInteractionListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordParticipationCommandListener;
import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.application.canonical.GridWordsSourceDeletionService;
import de.venomenon.gridwordsbot.application.cleanup.DailyChannelCleanupService;
import de.venomenon.gridwordsbot.application.achievement.AchievementBootstrapCoordinator;
import de.venomenon.gridwordsbot.application.achievement.AchievementResultLifecycle;
import de.venomenon.gridwordsbot.port.in.ExcuseExpirationUseCase;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** Resumes durable result work and attaches inbound processing; player registration is entirely event-driven. */
final class DatabaseInboundStartup implements ApplicationRunner {
    private final ObjectProvider<JDA> jdaProvider;
    private final ObjectProvider<DiscordInboundListener> listenerProvider;
    private final ObjectProvider<DiscordParticipationCommandListener> commandProvider;
    private final ObjectProvider<DailyResultDetailsInteractionListener> resultDetailsProvider;
    private final ObjectProvider<ExcuseOpenInteractionListener> excuseOpenProvider;
    private final ObjectProvider<ExcuseInteractionListener> excuseInteractionProvider;
    private final ObjectProvider<ExcuseExpirationUseCase> excuseExpirationProvider;
    private final ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider;
    private final ObjectProvider<GridWordsSourceDeletionService> deletionProvider;
    private final ObjectProvider<DailyChannelCleanupService> cleanupProvider;
    private final ObjectProvider<AchievementBootstrapCoordinator> achievementBootstrapProvider;
    private final ObjectProvider<AchievementResultLifecycle> achievementLifecycleProvider;
    private final GridwordsBotProperties properties;
    DatabaseInboundStartup(ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider,
            ObjectProvider<DiscordParticipationCommandListener> commandProvider,
            ObjectProvider<DailyResultDetailsInteractionListener> resultDetailsProvider,
            ObjectProvider<ExcuseOpenInteractionListener> excuseOpenProvider,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider,
            ObjectProvider<GridWordsSourceDeletionService> deletionProvider,
            ObjectProvider<DailyChannelCleanupService> cleanupProvider) {
        this(jdaProvider, listenerProvider, commandProvider, resultDetailsProvider, excuseOpenProvider,
                absentExcuseInteractions(), canonicalProvider, deletionProvider, cleanupProvider);
    }
    DatabaseInboundStartup(ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider,
            ObjectProvider<DiscordParticipationCommandListener> commandProvider,
            ObjectProvider<DailyResultDetailsInteractionListener> resultDetailsProvider,
            ObjectProvider<ExcuseOpenInteractionListener> excuseOpenProvider,
            ObjectProvider<ExcuseInteractionListener> excuseInteractionProvider,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider,
            ObjectProvider<GridWordsSourceDeletionService> deletionProvider,
            ObjectProvider<DailyChannelCleanupService> cleanupProvider) {
        this(jdaProvider, listenerProvider, commandProvider, resultDetailsProvider, excuseOpenProvider,
                excuseInteractionProvider, absentExcuseExpirations(), canonicalProvider, deletionProvider, cleanupProvider);
    }
    DatabaseInboundStartup(ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider,
            ObjectProvider<DiscordParticipationCommandListener> commandProvider,
            ObjectProvider<DailyResultDetailsInteractionListener> resultDetailsProvider,
            ObjectProvider<ExcuseOpenInteractionListener> excuseOpenProvider,
            ObjectProvider<ExcuseInteractionListener> excuseInteractionProvider,
            ObjectProvider<ExcuseExpirationUseCase> excuseExpirationProvider,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider,
            ObjectProvider<GridWordsSourceDeletionService> deletionProvider,
            ObjectProvider<DailyChannelCleanupService> cleanupProvider) {
        this(jdaProvider, listenerProvider, commandProvider, resultDetailsProvider, excuseOpenProvider,
                excuseInteractionProvider, excuseExpirationProvider, canonicalProvider, deletionProvider, cleanupProvider,
                absentAchievementBootstrap(), absentAchievementLifecycle(), null);
    }
    DatabaseInboundStartup(ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider,
            ObjectProvider<DiscordParticipationCommandListener> commandProvider,
            ObjectProvider<DailyResultDetailsInteractionListener> resultDetailsProvider,
            ObjectProvider<ExcuseOpenInteractionListener> excuseOpenProvider,
            ObjectProvider<ExcuseInteractionListener> excuseInteractionProvider,
            ObjectProvider<ExcuseExpirationUseCase> excuseExpirationProvider,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider,
            ObjectProvider<GridWordsSourceDeletionService> deletionProvider,
            ObjectProvider<DailyChannelCleanupService> cleanupProvider,
            ObjectProvider<AchievementBootstrapCoordinator> achievementBootstrapProvider,
            ObjectProvider<AchievementResultLifecycle> achievementLifecycleProvider,
            GridwordsBotProperties properties) {
        this.jdaProvider = jdaProvider; this.listenerProvider = listenerProvider; this.commandProvider = commandProvider;
        this.resultDetailsProvider = resultDetailsProvider; this.excuseOpenProvider = excuseOpenProvider;
        this.excuseInteractionProvider = excuseInteractionProvider; this.excuseExpirationProvider = excuseExpirationProvider;
        this.canonicalProvider = canonicalProvider;
        this.deletionProvider = deletionProvider; this.cleanupProvider = cleanupProvider;
        this.achievementBootstrapProvider = achievementBootstrapProvider;
        this.achievementLifecycleProvider = achievementLifecycleProvider;
        this.properties = properties;
    }
    DatabaseInboundStartup(ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider,
            ObjectProvider<DiscordParticipationCommandListener> commandProvider,
            ObjectProvider<DailyResultDetailsInteractionListener> resultDetailsProvider,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider,
            ObjectProvider<GridWordsSourceDeletionService> deletionProvider,
            ObjectProvider<DailyChannelCleanupService> cleanupProvider) {
        this(jdaProvider, listenerProvider, commandProvider, resultDetailsProvider, absentExcuseOpen(), absentExcuseInteractions(), canonicalProvider,
                deletionProvider, cleanupProvider);
    }
    DatabaseInboundStartup(ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider,
            ObjectProvider<DiscordParticipationCommandListener> commandProvider,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider,
            ObjectProvider<GridWordsSourceDeletionService> deletionProvider,
            ObjectProvider<DailyChannelCleanupService> cleanupProvider) {
        this(jdaProvider, listenerProvider, commandProvider, absentResultDetails(), absentExcuseOpen(), absentExcuseInteractions(), canonicalProvider, deletionProvider, cleanupProvider);
    }
    DatabaseInboundStartup(ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider,
            ObjectProvider<DiscordParticipationCommandListener> commandProvider,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider,
            ObjectProvider<GridWordsSourceDeletionService> deletionProvider) {
        this(jdaProvider, listenerProvider, commandProvider, absentResultDetails(), absentExcuseOpen(), absentExcuseInteractions(), canonicalProvider, deletionProvider,
                new ObjectProvider<>() { @Override public DailyChannelCleanupService getObject() { return null; } });
    }
    private static ObjectProvider<DailyResultDetailsInteractionListener> absentResultDetails() {
        return new ObjectProvider<>() { @Override public DailyResultDetailsInteractionListener getObject() { return null; } };
    }
    private static ObjectProvider<ExcuseOpenInteractionListener> absentExcuseOpen() {
        return new ObjectProvider<>() { @Override public ExcuseOpenInteractionListener getObject() { return null; } };
    }
    private static ObjectProvider<ExcuseInteractionListener> absentExcuseInteractions() {
        return new ObjectProvider<>() { @Override public ExcuseInteractionListener getObject() { return null; } };
    }
    private static ObjectProvider<ExcuseExpirationUseCase> absentExcuseExpirations() {
        return new ObjectProvider<>() { @Override public ExcuseExpirationUseCase getObject() { return null; } };
    }
    private static ObjectProvider<AchievementBootstrapCoordinator> absentAchievementBootstrap() {
        return new ObjectProvider<>() { @Override public AchievementBootstrapCoordinator getObject() { return null; } };
    }
    private static ObjectProvider<AchievementResultLifecycle> absentAchievementLifecycle() {
        return new ObjectProvider<>() { @Override public AchievementResultLifecycle getObject() { return null; } };
    }
    @Override public void run(ApplicationArguments arguments) {
        AchievementBootstrapCoordinator achievementBootstrap = achievementBootstrapProvider.getIfAvailable();
        if (achievementBootstrap != null && properties != null) {
            achievementBootstrap.run(properties.discord().guildId(), properties.discord().channelId());
        }
        AchievementResultLifecycle achievementLifecycle = achievementLifecycleProvider.getIfAvailable();
        if (achievementLifecycle != null) achievementLifecycle.recoverPendingResults();
        DailyChannelCleanupService cleanup = cleanupProvider.getIfAvailable();
        if (cleanup != null) cleanup.reconcile();
        ExcuseExpirationUseCase expirations = excuseExpirationProvider.getIfAvailable();
        if (expirations != null) expirations.reconcile();
        CanonicalGridWordsPublicationService canonical = canonicalProvider.getIfAvailable(); if (canonical != null) canonical.resumeOpenPublications();
        GridWordsSourceDeletionService deletion = deletionProvider.getIfAvailable(); if (deletion != null) deletion.resumeOpenDeletions();
        JDA jda = jdaProvider.getIfAvailable(); DiscordInboundListener listener = listenerProvider.getIfAvailable();
        if (jda != null && listener != null) jda.addEventListener(listener);
        DailyResultDetailsInteractionListener resultDetails = resultDetailsProvider.getIfAvailable(); if (jda != null && resultDetails != null) jda.addEventListener(resultDetails);
        ExcuseOpenInteractionListener excuseOpen = excuseOpenProvider.getIfAvailable(); if (jda != null && excuseOpen != null) jda.addEventListener(excuseOpen);
        ExcuseInteractionListener excuseInteractions = excuseInteractionProvider.getIfAvailable(); if (jda != null && excuseInteractions != null) jda.addEventListener(excuseInteractions);
        DiscordParticipationCommandListener commands = commandProvider.getIfAvailable();
        if (jda != null && commands != null) { jda.addEventListener(commands); commands.registerCommands(jda); }
    }
}
