package de.venomenon.gridwordsbot.adapter.discord.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.adapter.discord.record.RecordsOverviewEmbedRenderer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.RecordsQueryUseCase;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DiscordRecordsCommandListenerTest {
    private static final long GUILD = 11L;
    private static final long ACTOR = 101L;

    @Test
    void registersThePackageOptionsOnly() {
        var command = DiscordRecordsCommandListener.commandData();

        assertThat(command.getName()).isEqualTo("records");
        assertThat(command.getOptions()).extracting(option -> option.getName())
                .containsExactly("user", "game");
        assertThat(command.getOptions()).allSatisfy(option -> assertThat(option.isRequired()).isFalse());
    }

    @Test
    void standardUserDefaultsPersonalViewToCallerAndRepliesEphemerallyWithoutMentions() {
        RecordsQueryUseCase records = mock(RecordsQueryUseCase.class);
        when(records.query(any())).thenReturn(new RecordsQueryUseCase.Ready(List.of()));
        EventFixture fixture = event(GUILD, ACTOR, "Server Actor");

        listener(records, List.of()).onSlashCommandInteraction(fixture.event());

        ArgumentCaptor<RecordsQueryUseCase.Query> query = ArgumentCaptor.forClass(RecordsQueryUseCase.Query.class);
        verify(records).query(query.capture());
        assertThat(query.getValue().requesterPlayerId()).isEqualTo(ACTOR);
        assertThat(query.getValue().targetPlayerId()).isEmpty();
        assertThat(query.getValue().requesterAdministrator()).isFalse();
        assertThat(query.getValue().game()).isEqualTo(RecordsQueryUseCase.GameFilter.ALL);
        verify(fixture.reply()).setEphemeral(true);
        verify(fixture.reply()).setAllowedMentions(List.of());
        verify(fixture.reply()).queue();
    }

    @Test
    void administratorTargetAndGameFilterArePassedReadOnlyToUseCase() {
        RecordsQueryUseCase records = mock(RecordsQueryUseCase.class);
        when(records.query(any())).thenReturn(new RecordsQueryUseCase.Ready(List.of()));
        EventFixture fixture = event(GUILD, ACTOR, "Admin");
        OptionMapping game = option("quadwords");
        OptionMapping userOption = mock(OptionMapping.class);
        User target = mock(User.class);
        Member targetMember = mock(Member.class);
        when(target.getIdLong()).thenReturn(202L);
        when(target.getName()).thenReturn("target");
        when(targetMember.getEffectiveName()).thenReturn("Server Target");
        when(userOption.getAsUser()).thenReturn(target);
        when(userOption.getAsMember()).thenReturn(targetMember);
        when(fixture.event().getOption("game")).thenReturn(game);
        when(fixture.event().getOption("user")).thenReturn(userOption);

        listener(records, List.of(ACTOR)).onSlashCommandInteraction(fixture.event());

        ArgumentCaptor<RecordsQueryUseCase.Query> query = ArgumentCaptor.forClass(RecordsQueryUseCase.Query.class);
        verify(records).query(query.capture());
        assertThat(query.getValue().targetPlayerId()).contains(202L);
        assertThat(query.getValue().requesterAdministrator()).isTrue();
        assertThat(query.getValue().game()).isEqualTo(RecordsQueryUseCase.GameFilter.QUADWORDS);
    }

    @Test
    void unknownGameValueFallsBackToAll() {
        RecordsQueryUseCase records = mock(RecordsQueryUseCase.class);
        when(records.query(any())).thenReturn(new RecordsQueryUseCase.Ready(List.of()));
        EventFixture fixture = event(GUILD, ACTOR, "Actor");
        when(fixture.event().getOption("game")).thenReturn(option("unexpected"));

        listener(records, List.of()).onSlashCommandInteraction(fixture.event());

        ArgumentCaptor<RecordsQueryUseCase.Query> query = ArgumentCaptor.forClass(RecordsQueryUseCase.Query.class);
        verify(records).query(query.capture());
        assertThat(query.getValue().game()).isEqualTo(RecordsQueryUseCase.GameFilter.ALL);
    }

    @Test
    void ignoresOtherGuilds() {
        RecordsQueryUseCase records = mock(RecordsQueryUseCase.class);
        EventFixture fixture = event(GUILD + 1, ACTOR, "Actor");

        listener(records, List.of()).onSlashCommandInteraction(fixture.event());

        verifyNoInteractions(records);
    }

    private static DiscordRecordsCommandListener listener(RecordsQueryUseCase records, List<Long> admins) {
        return new DiscordRecordsCommandListener(properties(admins), records, new RecordsOverviewEmbedRenderer());
    }

    private static OptionMapping option(String value) {
        OptionMapping option = mock(OptionMapping.class);
        when(option.getAsString()).thenReturn(value);
        return option;
    }

    private static EventFixture event(long guildId, long actorId, String effectiveName) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        User actor = mock(User.class);
        Member member = mock(Member.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.getName()).thenReturn("records");
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(guildId);
        when(event.getUser()).thenReturn(actor);
        when(event.getMember()).thenReturn(member);
        when(actor.getIdLong()).thenReturn(actorId);
        when(actor.getName()).thenReturn("actor");
        when(member.getEffectiveName()).thenReturn(effectiveName);
        when(event.replyEmbeds(org.mockito.ArgumentMatchers.<MessageEmbed>anyCollection())).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);
        when(reply.setAllowedMentions(anyCollection())).thenReturn(reply);
        return new EventFixture(event, reply);
    }

    private static GridwordsBotProperties properties(List<Long> admins) {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", GUILD, 12L, admins), null, null);
    }

    private record EventFixture(SlashCommandInteractionEvent event, ReplyCallbackAction reply) { }
}
