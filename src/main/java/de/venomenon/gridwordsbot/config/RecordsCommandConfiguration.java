package de.venomenon.gridwordsbot.config;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordRecordsCommandListener;
import de.venomenon.gridwordsbot.adapter.discord.record.RecordsOverviewEmbedRenderer;
import de.venomenon.gridwordsbot.application.record.RecordBootstrapReadService;
import de.venomenon.gridwordsbot.application.record.RecordStateReadService;
import de.venomenon.gridwordsbot.application.record.RecordsQueryService;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.port.in.RecordsQueryUseCase;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Wires the read-only /records command without introducing another persistence path. */
@Configuration(proxyBeanMethods = false)
@Profile("database")
@ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
class RecordsCommandConfiguration {
    @Bean
    RecordsQueryUseCase recordsQueryUseCase(
            RecordStateReadService states,
            RecordBootstrapReadService bootstrap,
            RecordDefinitionCatalog catalog,
            PlayerStore players) {
        return new RecordsQueryService(states, bootstrap, catalog, players);
    }

    @Bean
    RecordsOverviewEmbedRenderer recordsOverviewEmbedRenderer() {
        return new RecordsOverviewEmbedRenderer();
    }

    @Bean
    DiscordRecordsCommandListener discordRecordsCommandListener(
            GridwordsBotProperties properties,
            RecordsQueryUseCase records,
            RecordsOverviewEmbedRenderer renderer) {
        return new DiscordRecordsCommandListener(properties, records, renderer);
    }

    @Bean
    ApplicationRunner recordsCommandListenerStartup(JDA jda, DiscordRecordsCommandListener listener) {
        return arguments -> jda.addEventListener(listener);
    }
}
