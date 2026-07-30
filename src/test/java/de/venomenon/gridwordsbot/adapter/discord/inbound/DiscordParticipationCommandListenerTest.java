package de.venomenon.gridwordsbot.adapter.discord.inbound;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase.PlayerIdentity;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase.PlayerStatus;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

class DiscordParticipationCommandListenerTest {
    private static final long GUILD_ID = 11L;
    private static final long ACTOR_ID = 101L;
    private static final long TARGET_ID = 202L;

    @Test
    void delegatesSelfServiceWithTheServerDisplayNameAndRepliesEphemerally() {
        PlayerParticipationUseCase useCase = mock(PlayerParticipationUseCase.class);
        SlashCommandInteractionEvent event = event("participation", "join", GUILD_ID, ACTOR_ID, "Server Actor");
        PlayerStatus status = new PlayerStatus(true, true, true, false, "Teilnahme ist ab heute aktiv.");
        when(useCase.join(new PlayerIdentity(ACTOR_ID, "Server Actor"))).thenReturn(status);

        new DiscordParticipationCommandListener(properties(), useCase).onSlashCommandInteraction(event);

        verify(useCase).join(new PlayerIdentity(ACTOR_ID, "Server Actor"));
        verify(event.reply(status.message())).setEphemeral(true);
        verify(event.reply(status.message()).setEphemeral(true)).queue();
    }

    @Test
    void delegatesAdminStatusForTheSelectedGuildMember() {
        PlayerParticipationUseCase useCase = mock(PlayerParticipationUseCase.class);
        SlashCommandInteractionEvent event = event("player", "status", GUILD_ID, ACTOR_ID, "Admin");
        OptionMapping option = mock(OptionMapping.class);
        User target = mock(User.class);
        Member targetMember = mock(Member.class);
        when(event.getOption("user")).thenReturn(option);
        when(option.getAsUser()).thenReturn(target);
        when(option.getAsMember()).thenReturn(targetMember);
        when(target.getIdLong()).thenReturn(TARGET_ID);
        when(target.getName()).thenReturn("global-target");
        when(targetMember.getEffectiveName()).thenReturn("Server Target");
        PlayerStatus status = new PlayerStatus(true, true, false, false, "Teilnahme: inaktiv; Reminder: aus.");
        when(useCase.status(new PlayerIdentity(ACTOR_ID, "Admin"), new PlayerIdentity(TARGET_ID, "Server Target")))
                .thenReturn(status);

        new DiscordParticipationCommandListener(properties(), useCase).onSlashCommandInteraction(event);

        verify(useCase).status(
                new PlayerIdentity(ACTOR_ID, "Admin"),
                new PlayerIdentity(TARGET_ID, "Server Target"));
        verify(event.reply(status.message())).setEphemeral(true);
    }

    @Test
    void ignoresCommandsOutsideTheConfiguredGuild() {
        PlayerParticipationUseCase useCase = mock(PlayerParticipationUseCase.class);
        SlashCommandInteractionEvent event = event("reminders", "on", GUILD_ID + 1, ACTOR_ID, "Actor");

        new DiscordParticipationCommandListener(properties(), useCase).onSlashCommandInteraction(event);

        verifyNoInteractions(useCase);
    }

    private static SlashCommandInteractionEvent event(
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
        when(reply.setEphemeral(true)).thenReturn(reply);
        return event;
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", GUILD_ID, 12L, List.of(ACTOR_ID)),
                null,
                null);
    }
}
