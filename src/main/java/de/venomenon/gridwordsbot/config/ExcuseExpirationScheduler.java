package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.port.in.ExcuseExpirationUseCase;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A frequent trigger over bounded, durable expiry work; it is never the source of truth. */
@Component
@Profile("database")
@ConditionalOnProperty(prefix = "gridwords.excuses", name = "enabled", havingValue = "true")
final class ExcuseExpirationScheduler {

    private final ExcuseExpirationUseCase expirations;

    ExcuseExpirationScheduler(ExcuseExpirationUseCase expirations) {
        this.expirations = Objects.requireNonNull(expirations, "expirations");
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void reconcile() {
        expirations.reconcile();
    }
}
