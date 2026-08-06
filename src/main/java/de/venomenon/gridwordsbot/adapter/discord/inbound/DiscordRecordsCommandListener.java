package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.adapter.discord.record.RecordsOverviewEmbedRenderer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.RecordsQueryUseCase;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** Thin Discord adapter for the read-only current-record overview. */
public final class DiscordRecordsCommandListener extends ListenerAdapter {
    private final GridwordsBotProperties properties;
    private final RecordsQueryUseCase records;
    private final RecordsOverviewEmbedRenderer renderer;

    public DiscordRecordsCommandListener(
            GridwordsBotProperties properties,
            RecordsQueryUseCase records,
            RecordsOverviewEmbedRenderer renderer) {
        this.properties = java.util.Objects.requireNonNull(properties, "properties");
        this.records = java.util.Objects.requireNonNull(records, "records");
        this.renderer = java.util.Objects.requireNonNull(renderer, "renderer");
    }

    public static SlashCommandData commandData() {
        return Commands.slash("records", "Aktuelle Rekorde anzeigen")
                .addOptions(
                        new OptionData(OptionType.STRING, "game", "Spiel filtern", false)
                                .addChoice("Alle", "all")
                                .addChoice("GridWords", "gridwords")
                                .addChoice("QuadWords", "quadwords"),
                        new OptionData(OptionType.STRING, "scope", "Vergleichsraum filtern", false)
                                .addChoice("Alle", "all")
                                .addChoice("Persönlich", "personal")
                                .addChoice("Serverweit individuell", "server")
                                .addChoice("Gemeinsam", "shared"),
                        new OptionData(OptionType.USER, "user", "Spieler für persönliche Rekorde", false),
                        new OptionData(OptionType.STRING, "category", "Rekordart filtern", false)
                                .addChoice("Alle", "all")
                                .addChoice("Ergebnisrekorde", "results")
                                .addChoice("Serienrekorde", "series"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("records") || !event.isFromGuild()
                || event.getGuild().getIdLong() != properties.discord().guildId()) return;

        OptionMapping userOption = event.getOption("user");
        User personalUser = userOption == null ? event.getUser() : userOption.getAsUser();
        Member personalMember = userOption == null ? event.getMember() : userOption.getAsMember();
        String personalDisplay = personalMember == null ? personalUser.getName() : personalMember.getEffectiveName();

        RecordsQueryUseCase.Result result = records.query(new RecordsQueryUseCase.Query(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                userOption == null ? Optional.empty() : Optional.of(personalUser.getIdLong()),
                game(event.getOption("game")),
                scope(event.getOption("scope")),
                category(event.getOption("category"))));

        event.replyEmbeds(renderer.render(result, personalDisplay))
                .setEphemeral(true)
                .setAllowedMentions(List.of())
                .queue();
    }

    private static RecordsQueryUseCase.GameFilter game(OptionMapping option) {
        if (option == null) return RecordsQueryUseCase.GameFilter.ALL;
        return switch (option.getAsString()) {
            case "gridwords" -> RecordsQueryUseCase.GameFilter.GRIDWORDS;
            case "quadwords" -> RecordsQueryUseCase.GameFilter.QUADWORDS;
            default -> RecordsQueryUseCase.GameFilter.ALL;
        };
    }

    private static RecordsQueryUseCase.ScopeFilter scope(OptionMapping option) {
        if (option == null) return RecordsQueryUseCase.ScopeFilter.ALL;
        return switch (option.getAsString()) {
            case "personal" -> RecordsQueryUseCase.ScopeFilter.PERSONAL;
            case "server" -> RecordsQueryUseCase.ScopeFilter.SERVER_INDIVIDUAL;
            case "shared" -> RecordsQueryUseCase.ScopeFilter.SHARED;
            default -> RecordsQueryUseCase.ScopeFilter.ALL;
        };
    }

    private static RecordsQueryUseCase.CategoryFilter category(OptionMapping option) {
        if (option == null) return RecordsQueryUseCase.CategoryFilter.ALL;
        return switch (option.getAsString()) {
            case "results" -> RecordsQueryUseCase.CategoryFilter.RESULTS;
            case "series" -> RecordsQueryUseCase.CategoryFilter.SERIES;
            default -> RecordsQueryUseCase.CategoryFilter.ALL;
        };
    }
}
