package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.adapter.discord.achievement.AchievementCatalogEmbedRenderer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.AchievementCatalogQueryUseCase;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** Thin self-only Discord adapter for the filtered personal Achievement catalog. */
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
        return Commands.slash("achievement-list", "Alle Achievements mit deinem Status anzeigen")
                .addOptions(
                        new OptionData(OptionType.STRING, "game", "Spielscope filtern", false)
                                .addChoice("Alle", "all")
                                .addChoice("GridWords", "gridwords")
                                .addChoice("QuadWords", "quadwords")
                                .addChoice("GW+QW", "cross-game"),
                        new OptionData(OptionType.STRING, "category", "Kategorie filtern", false)
                                .addChoice("Alle", "all")
                                .addChoice("Erfahrung", "experience")
                                .addChoice("Zuverlässigkeit", "reliability")
                                .addChoice("Leistung", "performance")
                                .addChoice("Besonderes", "special"),
                        new OptionData(OptionType.STRING, "status", "Status filtern", false)
                                .addChoice("Alle", "all")
                                .addChoice("Freigeschaltet", "achieved")
                                .addChoice("Offen", "open"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("achievement-list") || !event.isFromGuild()
                || event.getGuild().getIdLong() != properties.discord().guildId()) return;

        var result = catalog.query(new AchievementCatalogQueryUseCase.Query(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                game(event.getOption("game")),
                category(event.getOption("category")),
                status(event.getOption("status"))));
        event.replyEmbeds(renderer.render(result))
                .setEphemeral(true)
                .setAllowedMentions(List.of())
                .queue();
    }

    private static AchievementCatalogQueryUseCase.GameFilter game(OptionMapping option) {
        if (option == null) return AchievementCatalogQueryUseCase.GameFilter.ALL;
        return switch (option.getAsString()) {
            case "gridwords" -> AchievementCatalogQueryUseCase.GameFilter.GRIDWORDS;
            case "quadwords" -> AchievementCatalogQueryUseCase.GameFilter.QUADWORDS;
            case "cross-game" -> AchievementCatalogQueryUseCase.GameFilter.CROSS_GAME;
            default -> AchievementCatalogQueryUseCase.GameFilter.ALL;
        };
    }

    private static AchievementCatalogQueryUseCase.CategoryFilter category(OptionMapping option) {
        if (option == null) return AchievementCatalogQueryUseCase.CategoryFilter.ALL;
        return switch (option.getAsString()) {
            case "experience" -> AchievementCatalogQueryUseCase.CategoryFilter.EXPERIENCE;
            case "reliability" -> AchievementCatalogQueryUseCase.CategoryFilter.RELIABILITY;
            case "performance" -> AchievementCatalogQueryUseCase.CategoryFilter.PERFORMANCE;
            case "special" -> AchievementCatalogQueryUseCase.CategoryFilter.SPECIAL;
            default -> AchievementCatalogQueryUseCase.CategoryFilter.ALL;
        };
    }

    private static AchievementCatalogQueryUseCase.StatusFilter status(OptionMapping option) {
        if (option == null) return AchievementCatalogQueryUseCase.StatusFilter.ALL;
        return switch (option.getAsString()) {
            case "achieved" -> AchievementCatalogQueryUseCase.StatusFilter.ACHIEVED;
            case "open" -> AchievementCatalogQueryUseCase.StatusFilter.OPEN;
            default -> AchievementCatalogQueryUseCase.StatusFilter.ALL;
        };
    }
}
