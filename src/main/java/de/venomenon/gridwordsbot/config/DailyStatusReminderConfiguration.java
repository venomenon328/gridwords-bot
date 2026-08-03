package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.status.JdaDailyStatusMessageGateway;
import de.venomenon.gridwordsbot.application.cleanup.ChannelMessageRetirementService;
import de.venomenon.gridwordsbot.application.cleanup.DailyChannelCleanupService;
import de.venomenon.gridwordsbot.application.reminder.ReminderDeliveryService;
import de.venomenon.gridwordsbot.application.status.DailyStatusProjector;
import de.venomenon.gridwordsbot.application.status.DailyStatusRefreshService;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.ChannelMessageRetirementStore;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("database")
class DailyStatusReminderConfiguration {
    @Bean
    DailyStatusProjector dailyStatusProjector(GameResultStore results, PlayerStore players) {
        return new DailyStatusProjector(results, players);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    JdaDailyStatusMessageGateway jdaDailyStatusMessageGateway(JDA jda) {
        return new JdaDailyStatusMessageGateway(jda);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    DailyStatusRefreshService dailyStatusRefreshService(
            DailyStatusProjector projector,
            DailyStatusStore store,
            JdaDailyStatusMessageGateway gateway,
            Clock clock,
            GridwordsBotProperties properties) {
        return new DailyStatusRefreshService(
                projector,
                store,
                gateway,
                clock,
                properties.schedule().timeZone(),
                properties.discord().guildId(),
                properties.discord().channelId());
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    ChannelMessageRetirementService channelMessageRetirementService(
            ChannelMessageRetirementStore store,
            CanonicalMessageGateway canonical,
            JdaDailyStatusMessageGateway reminders,
            Clock clock,
            GridwordsBotProperties properties) {
        return new ChannelMessageRetirementService(
                store,
                canonical,
                reminders,
                clock,
                properties.discord().guildId(),
                properties.discord().channelId());
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    DailyChannelCleanupService dailyChannelCleanupService(
            DailyStatusRefreshService status,
            ChannelMessageRetirementService retirement,
            DailyStatusStore deliveries,
            Clock clock,
            GridwordsBotProperties properties) {
        return new DailyChannelCleanupService(
                status,
                retirement,
                deliveries,
                clock,
                properties.schedule().timeZone(),
                properties.schedule().dailyCleanup(),
                properties.discord().guildId(),
                properties.discord().channelId());
    }

    @Bean
    @ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
    ReminderDeliveryService reminderDeliveryService(
            DailyStatusStore store,
            PlayerStore players,
            JdaDailyStatusMessageGateway gateway,
            Clock clock,
            GridwordsBotProperties properties) {
        return new ReminderDeliveryService(
                store,
                players,
                gateway,
                clock,
                properties.discord().guildId(),
                properties.discord().channelId());
    }
}
