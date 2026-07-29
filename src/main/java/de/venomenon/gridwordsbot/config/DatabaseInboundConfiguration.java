package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
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

/** Wires database-backed inbound processing while leaving the offline gateway profile independent. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
class DatabaseInboundConfiguration {

    @Bean
    ProcessSharedResultUseCase processSharedResultUseCase(
            Clock clock,
            GridwordsBotProperties properties,
            PlayerStore playerStore,
            SubmissionStore submissionStore) {
        return new ProcessSharedResultService(
                new GridWordsShareParser(), new QuadWordsShareParser(), clock, properties.schedule().timeZone(), playerStore,
                submissionStore);
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
            ObjectProvider<DiscordInboundListener> listenerProvider) {
        return new DatabaseInboundStartup(synchronizer, jdaProvider, listenerProvider);
    }

    private ConfiguredPlayer configuredPlayer(GridwordsBotProperties.Player player, List<Long> administrators) {
        return new ConfiguredPlayer(player.userId(), player.displayName(), administrators.contains(player.userId()));
    }
}
