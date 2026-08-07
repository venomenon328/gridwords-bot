package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.application.record.RecordAnnouncementDeliveryCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class RecordAnnouncementDeliverySchedulerTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=database")
            .withUserConfiguration(SchedulerTestConfiguration.class);

    @Test
    void registersSchedulerWhenDiscordIsEnabledEvenIfCoordinatorComesFromAnotherBeanDefinition() {
        contextRunner.withPropertyValues("gridwords.discord.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(RecordAnnouncementDeliveryCoordinator.class);
            assertThat(context).hasSingleBean(RecordAnnouncementDeliveryScheduler.class);
        });
    }

    @Test
    void doesNotRegisterSchedulerWhenDiscordIsDisabled() {
        contextRunner.withPropertyValues("gridwords.discord.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(RecordAnnouncementDeliveryCoordinator.class);
            assertThat(context).doesNotHaveBean(RecordAnnouncementDeliveryScheduler.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RecordAnnouncementDeliveryScheduler.class)
    static class SchedulerTestConfiguration {
        @Bean
        RecordAnnouncementDeliveryCoordinator recordAnnouncementDeliveryCoordinator() {
            return mock(RecordAnnouncementDeliveryCoordinator.class);
        }
    }
}
