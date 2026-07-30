package de.venomenon.gridwordsbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            assertThat(properties.schedule().firstReminder()).isEqualTo(LocalTime.of(18, 0));
            assertThat(properties.schedule().secondReminder()).isEqualTo(LocalTime.of(23, 0));
            assertThat(properties.schedule().timeZone()).isEqualTo(ZoneId.of("Europe/Berlin"));
            assertThat(properties.storage().rawImageRetentionHours()).isEqualTo(48);
        });
    }

    @Test
    void rejectsEqualReminderTimes() {
        assertThatThrownBy(() -> schedule(LocalTime.of(18, 0), LocalTime.of(18, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsReversedReminderTimes() {
        assertThatThrownBy(() -> schedule(LocalTime.of(23, 0), LocalTime.of(18, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingReminderTimes() {
        assertThatThrownBy(() -> schedule(null, LocalTime.of(23, 0)))
                .isInstanceOf(NullPointerException.class);
    }

    private static GridwordsBotProperties.Schedule schedule(LocalTime first, LocalTime second) {
        return new GridwordsBotProperties.Schedule(first, second, LocalTime.of(8, 0), LocalTime.of(8, 15),
                ZoneId.of("Europe/Berlin"));
    }
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GridwordsBotProperties.class)
    static class TestConfiguration {
    }
}
