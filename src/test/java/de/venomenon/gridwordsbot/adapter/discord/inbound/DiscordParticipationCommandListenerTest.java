package de.venomenon.gridwordsbot.adapter.discord.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.adapter.discord.status.PersonalStatusEmbedRenderer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase.PlayerIdentity;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase.PlayerStatus;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class DiscordParticipationCommandListenerTest {
    private static final long GUILD_ID = 11L;
    private static final long ACTOR_ID = 101L;
    private static final long TARGET_ID = 202L;

    @Test
    void delegatesSelfServiceWithTheServerDisplayNameAndRepliesEphemerally() {
        PlayerParticipationUseCase useCase = mock(PlayerParticipationUseCase.class);
        EventFixture fixture = event("participation", "join", GUILD_ID, ACTOR_ID, "Server Actor");
        PlayerStatus status = new PlayerStatus(true, true, true, false, "Teilnahme ist ab heute aktiv.");
        when(useCase.join(new PlayerIdentity(ACTOR_ID, "Server Actor"))).thenReturn(status);

        listener(useCase, mock(PersonalStatusUseCase.class))
                .onSlashCommandInteraction(fixture.event());

        verify(useCase).join(new PlayerIdentity(ACTOR_ID, "Server Actor"));
        verify(fixture.event()).reply(status.message());
        verify(fixture.reply()).setEphemeral(true);
        verify(fixture.reply()).queue();
    }

    @Test
    void delegatesAdminStatusForTheSelectedGuildMember() {
        PlayerParticipationUseCase useCase = mock(PlayerParticipationUseCase.class);
        EventFixture fixture = event("player", "status", GUILD_ID, ACTOR_ID, "Admin");
        OptionMapping option = mock(OptionMapping.class);
        User target = mock(User.class);
        Member targetMember = mock(Member.class);
        when(fixture.event().getOption("user")).thenReturn(option);
        when(option.getAsUser()).thenReturn(target);
        when(option.getAsMember()).thenReturn(targetMember);
        when(target.getIdLong()).thenReturn(TARGET_ID);
        when(target.getName()).thenReturn("global-target");
        when(targetMember.getEffectiveName()).thenReturn("Server Target");
        PlayerStatus status = new PlayerStatus(true, true, false, false, "Teilnahme: inaktiv; Reminder: aus.");
        when(useCase.status(new PlayerIdentity(ACTOR_ID, "Admin"), new PlayerIdentity(TARGET_ID, "Server Target")))
                .thenReturn(status);

        listener(useCase, mock(PersonalStatusUseCase.class))
                .onSlashCommandInteraction(fixture.event());

        verify(useCase).status(
                new PlayerIdentity(ACTOR_ID, "Admin"),
                new PlayerIdentity(TARGET_ID, "Server Target"));
        verify(fixture.event()).reply(status.message());
        verify(fixture.reply()).setEphemeral(true);
    }

    @Test
    void ignoresCommandsOutsideTheConfiguredGuild() {
        PlayerParticipationUseCase useCase = mock(PlayerParticipationUseCase.class);
        EventFixture fixture = event("reminders", "on", GUILD_ID + 1, ACTOR_ID, "Actor");

        listener(useCase, mock(PersonalStatusUseCase.class))
                .onSlashCommandInteraction(fixture.event());

        verifyNoInteractions(useCase);
    }

    @Test
    void delegatesRootStatusAndRepliesWithAnEphemeralEmbed() {
        PlayerParticipationUseCase commands = mock(PlayerParticipationUseCase.class);
        PersonalStatusUseCase personalStatus = mock(PersonalStatusUseCase.class);
        EventFixture fixture = event("status", null, GUILD_ID, ACTOR_ID, "Server Actor");
        PersonalStatusUseCase.PersonalStatus status = new PersonalStatusUseCase.PersonalStatus(
                new PersonalStatusUseCase.ParticipationStatus(false, Optional.empty(), Optional.empty()),
                false, Optional.empty(), Optional.empty());
        when(personalStatus.status(new PersonalStatusUseCase.PlayerIdentity(ACTOR_ID, "Server Actor")))
                .thenReturn(status);

        listener(commands, personalStatus).onSlashCommandInteraction(fixture.event());

        verify(personalStatus).status(new PersonalStatusUseCase.PlayerIdentity(ACTOR_ID, "Server Actor"));
        verify(fixture.event()).replyEmbeds(any(MessageEmbed.class));
        verify(fixture.reply()).setEphemeral(true);
        verify(fixture.reply()).queue();
        verifyNoInteractions(commands);
    }

    @Test
    void registersRootStatusAndParticipationWithOnlyJoinAndLeave() {
        PlayerParticipationUseCase commands = mock(PlayerParticipationUseCase.class);
        PersonalStatusUseCase personalStatus = mock(PersonalStatusUseCase.class);
        JDA jda = mock(JDA.class);
        Guild guild = mock(Guild.class);
        CommandListUpdateAction commandUpdate = mock(CommandListUpdateAction.class);
        when(jda.getGuildById(GUILD_ID)).thenReturn(guild);
        when(guild.updateCommands()).thenReturn(commandUpdate);
        when(commandUpdate.addCommands(any(CommandData.class), any(CommandData.class), any(CommandData.class),
                any(CommandData.class))).thenReturn(commandUpdate);

        listener(commands, personalStatus).registerCommands(jda);

        ArgumentCaptor<CommandData> commandData = ArgumentCaptor.forClass(CommandData.class);
        verify(commandUpdate).addCommands(commandData.capture(), commandData.capture(), commandData.capture(),
                commandData.capture());
        assertThat(commandData.getAllValues()).extracting(CommandData::getName)
                .containsExactly("participation", "status", "player", "reminders");
        SlashCommandData participation = (SlashCommandData) commandData.getAllValues().stream()
                .filter(command -> command.getName().equals("participation"))
                .findFirst()
                .orElseThrow();
        assertThat(participation.getSubcommands()).extracting(subcommand -> subcommand.getName())
                .containsExactly("join", "leave");
        SlashCommandData status = (SlashCommandData) commandData.getAllValues().stream()
                .filter(command -> command.getName().equals("status"))
                .findFirst()
                .orElseThrow();
        assertThat(status.getOptions()).isEmpty();
        assertThat(status.getSubcommands()).isEmpty();
        SlashCommandData player = (SlashCommandData) commandData.getAllValues().stream()
                .filter(command -> command.getName().equals("player"))
                .findFirst()
                .orElseThrow();
        assertThat(player.getSubcommands()).extracting(subcommand -> subcommand.getName())
                .containsExactly("activate", "deactivate", "status");
        SlashCommandData reminders = (SlashCommandData) commandData.getAllValues().stream()
                .filter(command -> command.getName().equals("reminders"))
                .findFirst()
                .orElseThrow();
        assertThat(reminders.getSubcommands()).extracting(subcommand -> subcommand.getName())
                .containsExactly("on", "off", "status");
    }

    private static DiscordParticipationCommandListener listener(
            PlayerParticipationUseCase commands, PersonalStatusUseCase personalStatus) {
        return new DiscordParticipationCommandListener(properties(), commands, personalStatus,
                new PersonalStatusEmbedRenderer(ZoneId.of("Europe/Berlin")));
    }

    private static EventFixture event(
            String command, String subcommand, long guildId, long actorId, String effectiveName) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        User actor = mock(User.class);
        Member member = mock(Member.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(guildId);
        when(event.getName()).thenReturn(command);
        when(event.getSubcommandName()).thenReturn(subcommand);
        when(event.getUser()).thenReturn(actor);
        when(event.getMember()).thenReturn(member);
        when(actor.getIdLong()).thenReturn(actorId);
        when(actor.getName()).thenReturn("global-actor");
        when(member.getEffectiveName()).thenReturn(effectiveName);
        when(event.reply(org.mockito.ArgumentMatchers.anyString())).thenReturn(reply);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);
        return new EventFixture(event, reply);
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", GUILD_ID, 12L, List.of(ACTOR_ID)),
                null,
                null);
    }

    private record EventFixture(SlashCommandInteractionEvent event, ReplyCallbackAction reply) { }
}
