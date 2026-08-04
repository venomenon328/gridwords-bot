package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.persistence.PostgresPersistenceAdapter;
import de.venomenon.gridwordsbot.application.excuse.ExcuseResultLifecycle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityThresholds;
import de.venomenon.gridwordsbot.port.out.ExcuseDailyResultQuery;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** Production wiring for the result-storage lifecycle controlled by {@code EXCUSES_ENABLED}. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
class ExcuseLifecycleConfiguration {

    private static final Constructor<?> LIFECYCLE_AWARE_PERSISTENCE_CONSTRUCTOR = persistenceConstructor();

    /**
     * The persistence adapter retains shorter constructors for focused integration tests and legacy
     * compatibility. Select its lifecycle-aware constructor explicitly for the one Spring-managed
     * production instance instead of creating a second adapter bean.
     */
    @Bean
    static SmartInstantiationAwareBeanPostProcessor excuseLifecyclePersistenceConstructorSelector() {
        return new SmartInstantiationAwareBeanPostProcessor() {
            @Override
            public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName) {
                return beanClass == PostgresPersistenceAdapter.class
                        ? new Constructor<?>[]{LIFECYCLE_AWARE_PERSISTENCE_CONSTRUCTOR}
                        : null;
            }
        };
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "gridwords.excuses",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    ExcuseResultLifecycle disabledExcuseResultLifecycle(ExcuseStateStore states, Clock clock) {
        return ExcuseResultLifecycle.disabled(states, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.excuses", name = "enabled", havingValue = "true")
    ExcuseResultLifecycle enabledExcuseResultLifecycle(
            ExcuseStateStore states,
            ExcuseDailyResultQuery dailyResults,
            ExcuseCatalog catalog,
            Clock clock,
            GridwordsBotProperties properties) {
        return ExcuseResultLifecycle.enabled(
                states,
                dailyResults,
                new ExcuseEligibilityPolicy(ExcuseEligibilityThresholds.defaults()),
                catalog,
                clock,
                properties.excuses().offerLifetime());
    }

    private static Constructor<?> persistenceConstructor() {
        try {
            return PostgresPersistenceAdapter.class.getConstructor(
                    JdbcTemplate.class,
                    Clock.class,
                    ZoneId.class,
                    ExcuseResultLifecycle.class);
        } catch (NoSuchMethodException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
