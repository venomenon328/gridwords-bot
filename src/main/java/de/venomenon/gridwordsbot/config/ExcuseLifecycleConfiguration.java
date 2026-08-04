package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.catalog.JsonExcuseCatalogLoader;
import de.venomenon.gridwordsbot.application.excuse.ContextualExcuseLifecycle;
import de.venomenon.gridwordsbot.application.excuse.ExcuseLifecycle;
import de.venomenon.gridwordsbot.application.excuse.NoOpExcuseLifecycle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalogCoverage;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalogValidator;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.PriorValidResultQuery;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Production wiring for the result-storage lifecycle. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
class ExcuseLifecycleConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "true")
    ExcuseLifecycle contextualExcuseLifecycle(
            PriorValidResultQuery priorValidResultQuery,
            @Value("${gridwords.business-time-zone-id:Europe/Berlin}") ZoneId businessZone
    ) {
        return new ContextualExcuseLifecycle(priorValidResultQuery, businessZone);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuse-generator",
            name = "contextual-enabled",
            havingValue = "false",
            matchIfMissing = true)
    ExcuseLifecycle noOpExcuseLifecycle() {
        return NoOpExcuseLifecycle.INSTANCE;
    }

    @Bean
    ExcuseLifecycle.Context excuseLifecycleContext(
            ExcuseStateStore states,
            ExcuseCatalog catalog,
            Clock clock,
            GridwordsBotProperties properties) {
        return new ExcuseLifecycle.Context(states, catalog, clock, properties.excuses().offerLifetime());
    }

    @Bean
    ExcuseCatalog excuseCatalog() {
        return new JsonExcuseCatalogLoader(
                tools.jackson.databind.json.JsonMapper.builder().build(),
                new ExcuseCatalogValidator(),
                ExcuseCatalogCoverage.production())
                .loadResource(getClass().getClassLoader(), "excuses/catalog.json");
    }
}
