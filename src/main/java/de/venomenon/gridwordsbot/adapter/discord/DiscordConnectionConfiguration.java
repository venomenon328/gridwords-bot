package de.venomenon.gridwordsbot.adapter.discord;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.application.submission.ConfiguredPlayerSynchronizer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

@Configuration
@ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
public class DiscordConnectionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DiscordConnectionConfiguration.class);

    @Bean(destroyMethod = "shutdown")
    JDA jda(
            GridwordsBotProperties properties,
            ObjectProvider<ConfiguredPlayerSynchronizer> playerSynchronizerProvider,
            ObjectProvider<DiscordInboundListener> inboundListenerProvider) throws InterruptedException {
        String token = properties.discord().token();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "DISCORD_BOT_TOKEN must be set when DISCORD_ENABLED=true");
        }

        ConfiguredPlayerSynchronizer synchronizer = playerSynchronizerProvider.getIfAvailable();
        if (synchronizer != null) {
            synchronizer.synchronize();
        }

        JDABuilder builder = JDABuilder.createDefault(token).enableIntents(GatewayIntent.MESSAGE_CONTENT);
        DiscordInboundListener inboundListener = inboundListenerProvider.getIfAvailable();
        if (inboundListener != null) {
            builder.addEventListeners(inboundListener);
        }
        JDA jda = builder.build().awaitReady();

        log.info(
                "Discord connection ready as {} (application user id {}).",
                jda.getSelfUser().getName(),
                jda.getSelfUser().getId());
        return jda;
    }
}
