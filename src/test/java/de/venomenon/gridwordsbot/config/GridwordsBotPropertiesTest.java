package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class GridwordsBotPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "gridwords.discord.enabled=false",
                    "gridwords.discord.token=",
                    "gridwords.discord.guild-id=255064124902473729",
                    "gridwords.discord.channel-id=1531398793713549494",
                    "gridwords.discord.admin-user-ids=255063936410451978,451773931351834634",
                    "gridwords.players.first.user-id=255063936410451978",
                    "gridwords.players.first.display-name=Tobias",
                    "gridwords.players.second.user-id=451773931351834634",
                    "gridwords.players.second.display-name=Georgia",
                    "gridwords.schedule.first-reminder=18:00",
                    "gridwords.schedule.second-reminder=23:00",
                    "gridwords.schedule.weekly-report=08:00",
                    "gridwords.schedule.monthly-report=08:15",
                    "gridwords.schedule.time-zone=Europe/Berlin",
                    "gridwords.storage.raw-image-retention-hours=48");

    @Test
    void bindsConfiguredDiscordTargetPlayersAndSchedule() {
        contextRunner.run(context -> {
            GridwordsBotProperties properties = context.getBean(GridwordsBotProperties.class);

            assertThat(properties.discord().guildId()).isEqualTo(255064124902473729L);
            assertThat(properties.discord().channelId()).isEqualTo(1531398793713549494L);
            assertThat(properties.players().first().displayName()).isEqualTo("Tobias");
            assertThat(properties.players().second().displayName()).isEqualTo("Georgia");
            assertThat(properties.schedule().firstReminder()).isEqualTo(LocalTime.of(18, 0));
            assertThat(properties.schedule().secondReminder()).isEqualTo(LocalTime.of(23, 0));
            assertThat(properties.schedule().timeZone()).isEqualTo(ZoneId.of("Europe/Berlin"));
            assertThat(properties.storage().rawImageRetentionHours()).isEqualTo(48);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GridwordsBotProperties.class)
    static class TestConfiguration {
    }
}
