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
                        new OptionData(OptionType.USER, "user", "Persönliche Rekorde dieses Spielers (nur Admins)", false),
                        new OptionData(OptionType.STRING, "game", "Spiel filtern", false)
                                .addChoice("Alle", "all")
                                .addChoice("GridWords", "gridwords")
                                .addChoice("QuadWords", "quadwords"),
                        new OptionData(OptionType.STRING, "category", "Rekordart filtern", false)
                                .addChoice("Ergebnisse", "results")
                                .addChoice("Serien", "series"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("records") || !event.isFromGuild()
                || event.getGuild().getIdLong() != properties.discord().guildId()) return;

        OptionMapping userOption = event.getOption("user");
        User personalUser = userOption == null ? event.getUser() : userOption.getAsUser();
        Member personalMember = userOption == null ? event.getMember() : userOption.getAsMember();
        String personalDisplay = personalMember == null ? personalUser.getName() : personalMember.getEffectiveName();
        boolean administrator = properties.discord().adminUserIds().contains(event.getUser().getIdLong());

        RecordsQueryUseCase.Result result = records.query(new RecordsQueryUseCase.Query(
                event.getGuild().getIdLong(),
                event.getUser().getIdLong(),
                userOption == null ? Optional.empty() : Optional.of(personalUser.getIdLong()),
                administrator,
                game(event.getOption("game")),
                category(event.getOption("category"))));

        var pages = renderer.render(result, personalDisplay);
        event.replyEmbeds(pages.getFirst())
                .setEphemeral(true)
                .setAllowedMentions(List.of())
                .queue(hook -> {
                    for (int index = 1; index < pages.size(); index++) {
                        hook.sendMessageEmbeds(pages.get(index))
                                .setEphemeral(true)
                                .setAllowedMentions(List.of())
                                .queue();
                    }
                });
    }

    private static RecordsQueryUseCase.GameFilter game(OptionMapping option) {
        if (option == null) return RecordsQueryUseCase.GameFilter.ALL;
        return switch (option.getAsString()) {
            case "gridwords" -> RecordsQueryUseCase.GameFilter.GRIDWORDS;
            case "quadwords" -> RecordsQueryUseCase.GameFilter.QUADWORDS;
            default -> RecordsQueryUseCase.GameFilter.ALL;
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
