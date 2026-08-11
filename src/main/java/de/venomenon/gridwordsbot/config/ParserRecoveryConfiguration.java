package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.application.canonical.GridWordsSourceDeletionService;
import de.venomenon.gridwordsbot.application.submission.InvalidDurationRecoveryService;
import de.venomenon.gridwordsbot.port.in.InvalidDurationRecoveryUseCase;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.out.ParserRecoveryStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("database")
class ParserRecoveryConfiguration {

    @Bean
    InvalidDurationRecoveryUseCase invalidDurationRecoveryUseCase(
            ParserRecoveryStore recoveryStore,
            ProcessSharedResultUseCase processor,
            ObjectProvider<CanonicalGridWordsPublicationService> canonicalProvider,
            ObjectProvider<GridWordsSourceDeletionService> deletionProvider) {
        return new InvalidDurationRecoveryService(recoveryStore, processor, sourceMessageId -> {
            CanonicalGridWordsPublicationService canonical = canonicalProvider.getIfAvailable();
            GridWordsSourceDeletionService deletion = deletionProvider.getIfAvailable();
            if (canonical == null || deletion == null) {
                return false;
            }
            if (!canonical.publishMaintenanceRecovery(sourceMessageId)) {
                return false;
            }
            return deletion.deleteAfterCanonicalPublicationMaintenance(sourceMessageId);
        });
    }
}
