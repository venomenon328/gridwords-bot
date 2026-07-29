package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.application.submission.ConfiguredPlayerSynchronizer;
import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** Runs once after Liquibase: synchronize players, then attach inbound processing when Discord is enabled. */
final class DatabaseInboundStartup implements ApplicationRunner {

    private final ConfiguredPlayerSynchronizer playerSynchronizer;
    private final ObjectProvider<JDA> jdaProvider;
    private final ObjectProvider<DiscordInboundListener> listenerProvider;
    private final ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider;

    DatabaseInboundStartup(ConfiguredPlayerSynchronizer playerSynchronizer, ObjectProvider<JDA> jdaProvider, ObjectProvider<DiscordInboundListener> listenerProvider) {
        this(playerSynchronizer, jdaProvider, listenerProvider, () -> null);
    }

    DatabaseInboundStartup(
            ConfiguredPlayerSynchronizer playerSynchronizer,
            ObjectProvider<JDA> jdaProvider,
            ObjectProvider<DiscordInboundListener> listenerProvider,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider) {
        this.playerSynchronizer = playerSynchronizer;
        this.jdaProvider = jdaProvider;
        this.listenerProvider = listenerProvider;
        this.canonicalProvider = canonicalProvider;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        playerSynchronizer.synchronize();
        CanonicalGridWordsPublicationService canonical = canonicalProvider.getIfAvailable();
        if (canonical != null) canonical.resumeOpenPublications();

        JDA jda = jdaProvider.getIfAvailable();
        DiscordInboundListener listener = listenerProvider.getIfAvailable();
        if (jda != null && listener != null) {
            jda.addEventListener(listener);
        }
    }
}
