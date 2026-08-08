package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.achievement.AchievementCatalogEmbedRenderer;
import de.venomenon.gridwordsbot.adapter.discord.achievement.AchievementsOverviewEmbedRenderer;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordAchievementCatalogCommandListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordAchievementsCommandListener;
import de.venomenon.gridwordsbot.application.achievement.AchievementCatalogQueryService;
import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.application.achievement.AchievementsQueryService;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.port.in.AchievementCatalogQueryUseCase;
import de.venomenon.gridwordsbot.port.in.AchievementsQueryUseCase;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Wires the read-only Achievement commands against the materialized award projection. */
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
    AchievementCatalogQueryUseCase achievementCatalogQueryUseCase(
            AchievementAwardStateStore awards,
            AchievementDefinitionCatalog catalog) {
        return new AchievementCatalogQueryService(awards, catalog);
    }

    @Bean
    AchievementsOverviewEmbedRenderer achievementsOverviewEmbedRenderer(AchievementEmojiResolver emojis) {
        return new AchievementsOverviewEmbedRenderer(emojis);
    }

    @Bean
    AchievementCatalogEmbedRenderer achievementCatalogEmbedRenderer(AchievementEmojiResolver emojis) {
        return new AchievementCatalogEmbedRenderer(emojis);
    }

    @Bean
    DiscordAchievementsCommandListener discordAchievementsCommandListener(
            GridwordsBotProperties properties,
            AchievementsQueryUseCase achievements,
            AchievementsOverviewEmbedRenderer renderer) {
        return new DiscordAchievementsCommandListener(properties, achievements, renderer);
    }

    @Bean
    DiscordAchievementCatalogCommandListener discordAchievementCatalogCommandListener(
            GridwordsBotProperties properties,
            AchievementCatalogQueryUseCase catalog,
            AchievementCatalogEmbedRenderer renderer) {
        return new DiscordAchievementCatalogCommandListener(properties, catalog, renderer);
    }

    @Bean
    ApplicationRunner achievementsCommandListenersStartup(
            JDA jda,
            DiscordAchievementsCommandListener achievementsListener,
            DiscordAchievementCatalogCommandListener catalogListener) {
        return arguments -> {
            jda.addEventListener(achievementsListener);
            jda.addEventListener(catalogListener);
        };
    }
}
