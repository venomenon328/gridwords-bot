package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.adapter.discord.status.PersonalStatusEmbedRenderer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.domain.model.GameParticipationSelection;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

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
        var update = guild.updateCommands();
        update.addCommands(
                Commands.slash("participation", "Teilnahme verwalten")
                        .addSubcommands(participationCommand("join", "Ab heute teilnehmen"),
                                participationCommand("leave", "Ab morgen nicht mehr teilnehmen")),
                Commands.slash("status", "Persönlichen Status anzeigen"),
                Commands.slash("player", "Teilnahme eines Spielers verwalten")
                        .addSubcommands(playerCommand("activate", "Ab heute aktivieren"),
                                playerCommand("deactivate", "Ab morgen deaktivieren"),
                                playerCommand("status", "Status anzeigen")),
                Commands.slash("reminders", "Reminder-Einstellung verwalten")
                        .addSubcommands(new SubcommandData("on", "Reminder aktivieren"),
                                new SubcommandData("off", "Reminder deaktivieren"),
                                new SubcommandData("status", "Reminder-Status anzeigen")));
        update.addCommands(DiscordRecordsCommandListener.commandData());
        update.addCommands(DiscordAchievementsCommandListener.commandData());
        update.addCommands(DiscordAchievementCatalogCommandListener.commandData());
        update.queue();
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
            case "participation" -> participation(event.getSubcommandName(), actor, selection(event.getOption("game")));
            case "reminders" -> reminders(event.getSubcommandName(), actor);
            case "player" -> player(event.getSubcommandName(), actor, event.getOption("user"), event.getOption("game"));
            default -> null;
        };
        if (status != null) {
            String message = event.getName().equals("reminders") ? reminderMessage(status) : status.message();
            event.reply(message).setEphemeral(true).queue();
        }
    }

    private PlayerParticipationUseCase.PlayerStatus participation(
            String subcommand, PlayerParticipationUseCase.PlayerIdentity actor, GameParticipationSelection selection) {
        return switch (subcommand) {
            case "join" -> commands.join(actor, selection);
            case "leave" -> commands.leave(actor, selection);
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

    private PlayerParticipationUseCase.PlayerStatus player(
            String subcommand, PlayerParticipationUseCase.PlayerIdentity actor, OptionMapping userOption,
            OptionMapping gameOption) {
        if (userOption == null) return null;
        User user = userOption.getAsUser();
        PlayerParticipationUseCase.PlayerIdentity target = identity(user, userOption.getAsMember());
        return switch (subcommand) {
            case "activate" -> commands.activate(actor, target, selection(gameOption));
            case "deactivate" -> commands.deactivate(actor, target, selection(gameOption));
            case "status" -> commands.status(actor, target);
            default -> null;
        };
    }

    private String reminderMessage(PlayerParticipationUseCase.PlayerStatus status) {
        if (!status.known()) {
            return status.message();
        }
        boolean enabled = status.reminderOptIn();
        StringBuilder message = new StringBuilder(enabled ? "🔔 Reminder: an" : "🔕 Reminder: aus");
        if (enabled) {
            message.append("\nBei offenen Spielen wirst du in den geplanten Erinnerungen erwähnt.");
        } else {
            message.append("\nDu wirst bei offenen Spielen nicht erwähnt.")
                    .append("\nDein Name kann weiterhin ohne Ping in der gemeinsamen Reminder-Übersicht erscheinen.");
        }
        message.append("\n\nGeplante Erinnerungen: ")
                .append(time(properties.schedule().firstReminder()))
                .append(" und ")
                .append(time(properties.schedule().secondReminder()))
                .append(" Uhr");
        return message.toString();
    }

    private static String time(LocalTime time) {
        return TIME.format(time);
    }

    private static SubcommandData participationCommand(String name, String description) {
        return new SubcommandData(name, description).addOptions(gameOption());
    }

    private static SubcommandData playerCommand(String name, String description) {
        SubcommandData command = new SubcommandData(name, description)
                .addOptions(new OptionData(OptionType.USER, "user", "Discord-Nutzer", true));
        return name.equals("status") ? command : command.addOptions(gameOption());
    }

    private static OptionData gameOption() {
        return new OptionData(OptionType.STRING, "game", "Spielauswahl (Standard: beide)", false)
                .addChoice("GridWords", "gridwords")
                .addChoice("QuadWords", "quadwords")
                .addChoice("Beide Spiele", "both");
    }

    private static GameParticipationSelection selection(OptionMapping option) {
        if (option == null) return GameParticipationSelection.BOTH;
        return switch (option.getAsString()) {
            case "gridwords" -> GameParticipationSelection.GRIDWORDS;
            case "quadwords" -> GameParticipationSelection.QUADWORDS;
            case "both" -> GameParticipationSelection.BOTH;
            default -> GameParticipationSelection.BOTH;
        };
    }

    private static PlayerParticipationUseCase.PlayerIdentity identity(User user, Member member) {
        return new PlayerParticipationUseCase.PlayerIdentity(
                user.getIdLong(), member == null ? user.getName() : member.getEffectiveName());
    }

    private static PersonalStatusUseCase.PlayerIdentity personalIdentity(User user, Member member) {
        return new PersonalStatusUseCase.PlayerIdentity(
                user.getIdLong(), member == null ? user.getName() : member.getEffectiveName());
    }
}
