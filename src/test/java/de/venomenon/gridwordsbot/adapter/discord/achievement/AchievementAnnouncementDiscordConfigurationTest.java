package de.venomenon.gridwordsbot.adapter.discord.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementMessageGateway;
import java.util.Map;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class AchievementAnnouncementDiscordConfigurationTest {
    @Test
    void exposesGatewayWhenDiscordIsEnabledEvenIfJdaDefinitionIsRegisteredLater() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("gridwords.discord.enabled", "true")));
            context.register(AchievementAnnouncementDiscordConfiguration.class, JdaConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(AchievementAnnouncementMessageGateway.class)).hasSize(1);
        }
    }

    @Test
    void doesNotExposeGatewayWhenDiscordIsDisabled() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AchievementAnnouncementDiscordConfiguration.class, JdaConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(AchievementAnnouncementMessageGateway.class)).isEmpty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdaConfiguration {
        @Bean
        JDA jda() {
            return mock(JDA.class);
        }
    }
}
