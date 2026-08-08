package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.achievement.AchievementsOverviewEmbedRenderer;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordAchievementsCommandListener;
import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.application.achievement.AchievementsQueryService;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.port.in.AchievementsQueryUseCase;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Wires the read-only /achievements command against the materialized award projection. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
@ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
class AchievementsCommandConfiguration {
    @Bean
    AchievementsQueryUseCase achievementsQueryUseCase(
            AchievementAwardStateStore awards,
            AchievementDefinitionCatalog catalog) {
        return new AchievementsQueryService(awards, catalog);
    }

    @Bean
    AchievementsOverviewEmbedRenderer achievementsOverviewEmbedRenderer(AchievementEmojiResolver emojis) {
        return new AchievementsOverviewEmbedRenderer(emojis);
    }

    @Bean
    DiscordAchievementsCommandListener discordAchievementsCommandListener(
            GridwordsBotProperties properties,
            AchievementsQueryUseCase achievements,
            AchievementsOverviewEmbedRenderer renderer) {
        return new DiscordAchievementsCommandListener(properties, achievements, renderer);
    }

    @Bean
    ApplicationRunner achievementsCommandListenerStartup(JDA jda, DiscordAchievementsCommandListener listener) {
        return arguments -> jda.addEventListener(listener);
    }
}
