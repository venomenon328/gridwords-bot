package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.adapter.discord.achievement.AchievementCatalogEmbedRenderer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.AchievementCatalogQueryUseCase;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** Thin self-only Discord adapter for the complete personal Achievement catalog. */
public final class DiscordAchievementCatalogCommandListener extends ListenerAdapter {
    private final GridwordsBotProperties properties;
    private final AchievementCatalogQueryUseCase catalog;
    private final AchievementCatalogEmbedRenderer renderer;

    public DiscordAchievementCatalogCommandListener(
            GridwordsBotProperties properties,
            AchievementCatalogQueryUseCase catalog,
            AchievementCatalogEmbedRenderer renderer) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public static SlashCommandData commandData() {
        return Commands.slash("achievement-list", "Alle Achievements mit deinem Status anzeigen");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("achievement-list") || !event.isFromGuild()
                || event.getGuild().getIdLong() != properties.discord().guildId()) return;

        var result = catalog.query(new AchievementCatalogQueryUseCase.Query(
                event.getGuild().getIdLong(), event.getUser().getIdLong()));
        event.replyEmbeds(renderer.render(result))
                .setEphemeral(true)
                .setAllowedMentions(List.of())
                .queue();
    }
}
