package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Enables periodic report delivery for the real Discord transport or an explicitly supplied transport gateway.
 *
 * <p>The property branch keeps production wiring independent of configuration-class processing order. The gateway
 * branch preserves transport-neutral integration tests and alternative adapters while Discord itself is disabled.
 */
final class PeriodicReportActivationCondition extends AnyNestedCondition {

    PeriodicReportActivationCondition() {
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    static final class DiscordEnabled {}

    @ConditionalOnBean(PeriodicReportMessageGateway.class)
    static final class ExplicitGatewayAvailable {}
}
