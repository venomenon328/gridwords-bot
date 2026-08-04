package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.application.excuse.ExcuseLifecycle;
import de.venomenon.gridwordsbot.application.excuse.NoOpExcuseLifecycle;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.PriorValidResultQuery;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

class ExcuseLifecycleConfigurationTest {

    @Test
    void missingFeaturePropertyUsesTheNoOpLifecycleIndependentOfTheHostEnvironment() {
        contextRunnerWithoutHostProperties().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ExcuseLifecycle.class);
            assertThat(context.getBean(ExcuseLifecycle.class)).isSameAs(NoOpExcuseLifecycle.INSTANCE);
        });
    }

    private ApplicationContextRunner contextRunnerWithoutHostProperties() {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles("database");
                    context.getEnvironment().getPropertySources()
                            .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    context.getEnvironment().getPropertySources()
                            .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                })
                .withUserConfiguration(ExcuseLifecycleConfiguration.class)
                .withBean(ExcuseStateStore.class, () -> mock(ExcuseStateStore.class))
                .withBean(PriorValidResultQuery.class, () -> mock(PriorValidResultQuery.class))
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(GridwordsBotProperties.class, () -> new GridwordsBotProperties(null, null, null));
    }
}
