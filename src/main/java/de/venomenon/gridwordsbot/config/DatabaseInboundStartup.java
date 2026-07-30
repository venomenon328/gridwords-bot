package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.application.canonical.GridWordsSourceDeletionService;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** Resumes durable result work and attaches inbound processing; player registration is entirely event-driven. */
final class DatabaseInboundStartup implements ApplicationRunner {
    private final ObjectProvider<JDA> jdaProvider;
    private final ObjectProvider<DiscordInboundListener> listenerProvider;
    private final ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider;
    private final ObjectProvider<GridWordsSourceDeletionService> deletionProvider;
    DatabaseInboundStartup(ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider, ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider, ObjectProvider<GridWordsSourceDeletionService> deletionProvider) { this.jdaProvider = jdaProvider; this.listenerProvider = listenerProvider; this.canonicalProvider = canonicalProvider; this.deletionProvider = deletionProvider; }
    @Override public void run(ApplicationArguments arguments) {
        CanonicalGridWordsPublicationService canonical = canonicalProvider.getIfAvailable(); if (canonical != null) canonical.resumeOpenPublications();
        GridWordsSourceDeletionService deletion = deletionProvider.getIfAvailable(); if (deletion != null) deletion.resumeOpenDeletions();
        JDA jda = jdaProvider.getIfAvailable(); DiscordInboundListener listener = listenerProvider.getIfAvailable();
        if (jda != null && listener != null) jda.addEventListener(listener);
    }
}