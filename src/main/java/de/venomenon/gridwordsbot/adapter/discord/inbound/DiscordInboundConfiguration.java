package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Creates the bounded inbound worker only when Discord and database-backed processing are both active. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
@ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
class DiscordInboundConfiguration {

    @Bean(destroyMethod = "shutdown")
    ExecutorService discordInboundExecutor() {
        return new ThreadPoolExecutor(
                1,
                2,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32),
                Thread.ofPlatform().name("gridwords-discord-inbound-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    DiscordReactionGateway discordReactionGateway() {
        return new JdaDiscordReactionGateway();
    }

    @Bean
    DiscordInboundListener discordInboundListener(
            GridwordsBotProperties properties,
            Clock clock,
            ExecutorService discordInboundExecutor,
            ProcessSharedResultUseCase processSharedResultUseCase,
            DiscordReactionGateway discordReactionGateway) {
        return new DiscordInboundListener(
                properties, clock, discordInboundExecutor, processSharedResultUseCase, discordReactionGateway);
    }
}
