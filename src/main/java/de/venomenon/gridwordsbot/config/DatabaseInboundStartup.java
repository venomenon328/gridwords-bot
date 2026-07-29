package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.application.submission.ConfiguredPlayerSynchronizer;
import net.dv8tion.jda.api.JDA;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** Runs once after Liquibase: synchronize players, then attach inbound processing when Discord is enabled. */
final class DatabaseInboundStartup implements ApplicationRunner {

    private final ConfiguredPlayerSynchronizer playerSynchronizer;
    private final ObjectProvider<JDA> jdaProvider;
    private final ObjectProvider<DiscordInboundListener> listenerProvider;

    DatabaseInboundStartup(
            ConfiguredPlayerSynchronizer playerSynchronizer,
            ObjectProvider<JDA> jdaProvider,
            ObjectProvider<DiscordInboundListener> listenerProvider) {
        this.playerSynchronizer = playerSynchronizer;
        this.jdaProvider = jdaProvider;
        this.listenerProvider = listenerProvider;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        playerSynchronizer.synchronize();

        JDA jda = jdaProvider.getIfAvailable();
        DiscordInboundListener listener = listenerProvider.getIfAvailable();
        if (jda != null && listener != null) {
            jda.addEventListener(listener);
        }
    }
}
