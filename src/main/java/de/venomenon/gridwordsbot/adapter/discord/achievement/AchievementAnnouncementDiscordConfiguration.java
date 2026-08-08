package de.venomenon.gridwordsbot.adapter.discord.achievement;

import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementMessageGateway;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
class AchievementAnnouncementDiscordConfiguration {
    @Bean AchievementAnnouncementMessageGateway achievementAnnouncementMessageGateway(JDA jda) {
        return new JdaAchievementAnnouncementMessageGateway(jda);
    }
}
