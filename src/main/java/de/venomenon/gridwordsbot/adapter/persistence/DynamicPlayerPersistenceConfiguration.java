package de.venomenon.gridwordsbot.adapter.persistence;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Selects the dynamic-player specialization as the single database-profile persistence bean. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
class DynamicPlayerPersistenceConfiguration {

    @Bean
    static BeanDefinitionRegistryPostProcessor dynamicPlayerPersistenceAdapterSelector() {
        return registry -> removeBaseRepositoryBean(registry);
    }

    private static void removeBaseRepositoryBean(BeanDefinitionRegistry registry) {
        if (registry.containsBeanDefinition("postgresPersistenceAdapter")) {
            registry.removeBeanDefinition("postgresPersistenceAdapter");
        }
    }
}
