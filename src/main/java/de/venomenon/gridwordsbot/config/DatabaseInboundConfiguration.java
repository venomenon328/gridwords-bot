package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.submission.ConfiguredPlayerSynchronizer;
import de.venomenon.gridwordsbot.application.submission.ProcessSharedResultService;
import de.venomenon.gridwordsbot.parser.gridwords.GridWordsShareParser;
import de.venomenon.gridwordsbot.parser.quadwords.QuadWordsShareParser;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
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
        return new ConfiguredPlayerSynchronizer(properties, playerStore);
    }

    @Bean
    ApplicationRunner configuredPlayerSynchronizationRunner(ConfiguredPlayerSynchronizer synchronizer) {
        return arguments -> synchronizer.synchronize();
    }
}
