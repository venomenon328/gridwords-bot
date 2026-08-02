package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordParticipationCommandListener;
import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.application.canonical.GridWordsSourceDeletionService;
import de.venomenon.gridwordsbot.application.cleanup.DailyChannelCleanupService;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** Resumes durable result work and attaches inbound processing; player registration is entirely event-driven. */
final class DatabaseInboundStartup implements ApplicationRunner {
    private final ObjectProvider<JDA> jdaProvider;
    private final ObjectProvider<DiscordInboundListener> listenerProvider;
    private final ObjectProvider<DiscordParticipationCommandListener> commandProvider;
    private final ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider;
    private final ObjectProvider<GridWordsSourceDeletionService> deletionProvider;
    private final ObjectProvider<DailyChannelCleanupService> cleanupProvider;
    DatabaseInboundStartup(ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider, ObjectProvider<DiscordParticipationCommandListener> commandProvider, ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider, ObjectProvider<GridWordsSourceDeletionService> deletionProvider, ObjectProvider<DailyChannelCleanupService> cleanupProvider) { this.jdaProvider = jdaProvider; this.listenerProvider = listenerProvider; this.commandProvider = commandProvider; this.canonicalProvider = canonicalProvider; this.deletionProvider = deletionProvider; this.cleanupProvider = cleanupProvider; }
    DatabaseInboundStartup(ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider, ObjectProvider<DiscordParticipationCommandListener> commandProvider, ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider, ObjectProvider<GridWordsSourceDeletionService> deletionProvider) {
        this(jdaProvider, listenerProvider, commandProvider, canonicalProvider, deletionProvider, new ObjectProvider<>() { @Override public DailyChannelCleanupService getObject() { return null; } });
    }
    @Override public void run(ApplicationArguments arguments) {
        DailyChannelCleanupService cleanup = cleanupProvider.getIfAvailable();
        if (cleanup != null) cleanup.reconcile();
        CanonicalGridWordsPublicationService canonical = canonicalProvider.getIfAvailable(); if (canonical != null) canonical.resumeOpenPublications();
        GridWordsSourceDeletionService deletion = deletionProvider.getIfAvailable(); if (deletion != null) deletion.resumeOpenDeletions();
        JDA jda = jdaProvider.getIfAvailable(); DiscordInboundListener listener = listenerProvider.getIfAvailable();
        if (jda != null && listener != null) jda.addEventListener(listener);
        DiscordParticipationCommandListener commands = commandProvider.getIfAvailable();
        if (jda != null && commands != null) { jda.addEventListener(commands); commands.registerCommands(jda); }
    }
}