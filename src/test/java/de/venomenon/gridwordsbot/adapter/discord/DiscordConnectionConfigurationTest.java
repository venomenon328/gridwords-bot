package de.venomenon.gridwordsbot.adapter.discord;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DiscordConnectionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class, DiscordConnectionConfiguration.class)
            .withPropertyValues(
                    "gridwords.discord.guild-id=255064124902473729",
                    "gridwords.discord.channel-id=1531398793713549494");

    @Test
    void doesNotCreateDiscordConnectionWhenDisabledWithoutToken() {
        contextRunner
                .withPropertyValues(
                        "gridwords.discord.enabled=false",
                        "gridwords.discord.token=")
                .run(context -> assertThat(context).doesNotHaveBean(JDA.class));
    }

    @Test
    void failsClearlyWhenDiscordIsEnabledWithoutToken() {
        contextRunner
                .withPropertyValues(
                        "gridwords.discord.enabled=true",
                        "gridwords.discord.token=")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasRootCauseMessage("DISCORD_BOT_TOKEN must be set when DISCORD_ENABLED=true"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GridwordsBotProperties.class)
    static class TestConfiguration {
    }
}
