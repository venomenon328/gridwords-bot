package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.adapter.discord.status.PersonalStatusEmbedRenderer;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/** Thin Discord adapter for the transport-neutral player participation commands. */
public final class DiscordParticipationCommandListener extends ListenerAdapter {
    private final GridwordsBotProperties properties;
    private final PlayerParticipationUseCase commands;
    private final PersonalStatusUseCase personalStatus;
    private final PersonalStatusEmbedRenderer personalStatusRenderer;

    public DiscordParticipationCommandListener(GridwordsBotProperties properties, PlayerParticipationUseCase commands,
            PersonalStatusUseCase personalStatus, PersonalStatusEmbedRenderer personalStatusRenderer) {
        this.properties = properties;
        this.commands = commands;
        this.personalStatus = personalStatus;
        this.personalStatusRenderer = personalStatusRenderer;
    }

    public void registerCommands(JDA jda) {
        var guild = jda.getGuildById(properties.discord().guildId());
        if (guild == null) return;
        guild.updateCommands().addCommands(
                Commands.slash("participation", "Teilnahme verwalten")
                        .addSubcommands(new SubcommandData("join", "Ab heute teilnehmen"),
                                new SubcommandData("leave", "Ab morgen nicht mehr teilnehmen")),
                Commands.slash("status", "Persönlichen Status anzeigen"),
                Commands.slash("player", "Teilnahme eines Spielers verwalten")
                        .addSubcommands(playerCommand("activate", "Ab heute aktivieren"),
                                playerCommand("deactivate", "Ab morgen deaktivieren"),
                                playerCommand("status", "Status anzeigen")),
                Commands.slash("reminders", "Reminder-Einstellung verwalten")
                        .addSubcommands(new SubcommandData("on", "Reminder aktivieren"),
                                new SubcommandData("off", "Reminder deaktivieren"),
                                new SubcommandData("status", "Reminder-Status anzeigen")))
                .queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild().getIdLong() != properties.discord().guildId()) return;
        PlayerParticipationUseCase.PlayerIdentity actor = identity(event.getUser(), event.getMember());
        if (event.getName().equals("status")) {
            var status = personalStatus.status(personalIdentity(event.getUser(), event.getMember()));
            event.replyEmbeds(personalStatusRenderer.render(status)).setEphemeral(true).queue();
            return;
        }
        PlayerParticipationUseCase.PlayerStatus status = switch (event.getName()) {
            case "participation" -> participation(event.getSubcommandName(), actor);
            case "reminders" -> reminders(event.getSubcommandName(), actor);
            case "player" -> player(event.getSubcommandName(), actor, event.getOption("user"));
            default -> null;
        };
        if (status != null) event.reply(status.message()).setEphemeral(true).queue();
    }

    private PlayerParticipationUseCase.PlayerStatus participation(String subcommand, PlayerParticipationUseCase.PlayerIdentity actor) {
        return switch (subcommand) {
            case "join" -> commands.join(actor);
            case "leave" -> commands.leave(actor);
            default -> null;
        };
    }
    private PlayerParticipationUseCase.PlayerStatus reminders(String subcommand, PlayerParticipationUseCase.PlayerIdentity actor) {
        return switch (subcommand) {
            case "on" -> commands.enableReminders(actor);
            case "off" -> commands.disableReminders(actor);
            case "status" -> commands.reminderStatus(actor);
            default -> null;
        };
    }
    private PlayerParticipationUseCase.PlayerStatus player(String subcommand, PlayerParticipationUseCase.PlayerIdentity actor, OptionMapping option) {
        if (option == null) return null;
        User user = option.getAsUser();
        return switch (subcommand) {
            case "activate" -> commands.activate(actor, identity(user, option.getAsMember()));
            case "deactivate" -> commands.deactivate(actor, identity(user, option.getAsMember()));
            case "status" -> commands.status(actor, identity(user, option.getAsMember()));
            default -> null;
        };
    }
    private static SubcommandData playerCommand(String name, String description) {
        return new SubcommandData(name, description).addOptions(new OptionData(OptionType.USER, "user", "Discord-Nutzer", true));
    }
    private static PlayerParticipationUseCase.PlayerIdentity identity(User user, Member member) {
        return new PlayerParticipationUseCase.PlayerIdentity(user.getIdLong(), member == null ? user.getName() : member.getEffectiveName());
    }
    private static PersonalStatusUseCase.PlayerIdentity personalIdentity(User user, Member member) {
        return new PersonalStatusUseCase.PlayerIdentity(
                user.getIdLong(), member == null ? user.getName() : member.getEffectiveName());
    }
}
