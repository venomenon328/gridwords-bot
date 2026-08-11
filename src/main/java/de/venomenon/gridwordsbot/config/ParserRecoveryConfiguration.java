package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.submission.InvalidDurationRecoveryService;
import de.venomenon.gridwordsbot.port.in.InvalidDurationRecoveryUseCase;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.out.ParserRecoveryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("database")
class ParserRecoveryConfiguration {

    @Bean
    InvalidDurationRecoveryUseCase invalidDurationRecoveryUseCase(
            ParserRecoveryStore recoveryStore,
            ProcessSharedResultUseCase processor) {
        return new InvalidDurationRecoveryService(recoveryStore, processor);
    }
}
