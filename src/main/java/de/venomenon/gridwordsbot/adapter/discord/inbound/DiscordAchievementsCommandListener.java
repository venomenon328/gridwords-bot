package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.adapter.discord.achievement.AchievementsOverviewEmbedRenderer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.AchievementsQueryUseCase;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** Thin, read-only Discord adapter for the ephemeral Achievement profile command. */
public final class DiscordAchievementsCommandListener extends ListenerAdapter {
    private final GridwordsBotProperties properties;
    private final AchievementsQueryUseCase achievements;
    private final AchievementsOverviewEmbedRenderer renderer;

    public DiscordAchievementsCommandListener(
            GridwordsBotProperties properties,
            AchievementsQueryUseCase achievements,
            AchievementsOverviewEmbedRenderer renderer) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.achievements = Objects.requireNonNull(achievements, "achievements");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public static SlashCommandData commandData() {
        return Commands.slash("achievements", "Freigeschaltete Achievements anzeigen")
                .addOptions(
                        new OptionData(OptionType.USER, "user", "Achievements dieses Discord-Nutzers", false),
                        new OptionData(OptionType.STRING, "game", "Spiel filtern", false)
                                .addChoice("Alle", "all")
                                .addChoice("GridWords", "gridwords")
                                .addChoice("QuadWords", "quadwords"));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("achievements") || !event.isFromGuild()
                || event.getGuild().getIdLong() != properties.discord().guildId()) return;

        OptionMapping userOption = event.getOption("user");
        User targetUser = userOption == null ? event.getUser() : userOption.getAsUser();
        Member targetMember = userOption == null ? event.getMember() : userOption.getAsMember();
        String targetDisplay = targetMember == null ? targetUser.getName() : targetMember.getEffectiveName();

        AchievementsQueryUseCase.Result result = achievements.query(new AchievementsQueryUseCase.Query(
                event.getGuild().getIdLong(), targetUser.getIdLong(), game(event.getOption("game"))));
        var pages = renderer.render(result, targetDisplay);
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

    private static AchievementsQueryUseCase.GameFilter game(OptionMapping option) {
        if (option == null) return AchievementsQueryUseCase.GameFilter.ALL;
        return switch (option.getAsString()) {
            case "gridwords" -> AchievementsQueryUseCase.GameFilter.GRIDWORDS;
            case "quadwords" -> AchievementsQueryUseCase.GameFilter.QUADWORDS;
            default -> AchievementsQueryUseCase.GameFilter.ALL;
        };
    }
}
