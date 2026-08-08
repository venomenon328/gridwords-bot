package de.venomenon.gridwordsbot.adapter.discord.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.adapter.discord.achievement.AchievementsOverviewEmbedRenderer;
import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.port.in.AchievementsQueryUseCase;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DiscordAchievementsCommandListenerTest {
    private static final long GUILD = 11L;
    private static final long ACTOR = 101L;

    @Test
    void commandDefinesOnlyOptionalUserAndGameOptionsWithTheBindingChoices() {
        var command = DiscordAchievementsCommandListener.commandData();

        assertThat(command.getName()).isEqualTo("achievements");
        assertThat(command.getOptions()).extracting(option -> option.getName()).containsExactly("user", "game");
        assertThat(command.getOptions()).allSatisfy(option -> assertThat(option.isRequired()).isFalse());
        assertThat(command.getOptions().get(1).getChoices()).extracting(choice -> choice.getName())
                .containsExactly("Alle", "GridWords", "QuadWords");
    }

    @Test
    void defaultsToCallerAndAllowsForeignProfileWithoutAdminCheck() {
        AchievementsQueryUseCase achievements = mock(AchievementsQueryUseCase.class);
        when(achievements.query(any())).thenReturn(new AchievementsQueryUseCase.Result(List.of()));
        EventFixture fixture = event(ACTOR, "Caller");

        listener(achievements).onSlashCommandInteraction(fixture.event());

        ArgumentCaptor<AchievementsQueryUseCase.Query> query = ArgumentCaptor.forClass(AchievementsQueryUseCase.Query.class);
        verify(achievements).query(query.capture());
        assertThat(query.getValue()).isEqualTo(new AchievementsQueryUseCase.Query(
                GUILD, ACTOR, AchievementsQueryUseCase.GameFilter.ALL));
        verify(fixture.reply()).setEphemeral(true);
        verify(fixture.reply()).setAllowedMentions(List.of());

        OptionMapping userOption = mock(OptionMapping.class);
        User target = mock(User.class);
        Member targetMember = mock(Member.class);
        OptionMapping game = mock(OptionMapping.class);
        when(target.getIdLong()).thenReturn(202L);
        when(target.getName()).thenReturn("target");
        when(targetMember.getEffectiveName()).thenReturn("Server Target");
        when(userOption.getAsUser()).thenReturn(target);
        when(userOption.getAsMember()).thenReturn(targetMember);
        when(game.getAsString()).thenReturn("quadwords");
        when(fixture.event().getOption("user")).thenReturn(userOption);
        when(fixture.event().getOption("game")).thenReturn(game);

        listener(achievements).onSlashCommandInteraction(fixture.event());

        verify(achievements).query(new AchievementsQueryUseCase.Query(
                GUILD, 202L, AchievementsQueryUseCase.GameFilter.QUADWORDS));
    }

    @Test
    void everyPaginatedResponseIsEphemeralAndMentionSafe() {
        AchievementsQueryUseCase achievements = mock(AchievementsQueryUseCase.class);
        var entries = AchievementDefinitionCatalog.achievementsV1().definitions().stream()
                .map(definition -> new AchievementsQueryUseCase.Entry(
                        definition.key(), definition.category(), definition.scope(), definition.fallbackEmoji(),
                        definition.displayName(), definition.description()))
                .toList();
        when(achievements.query(any())).thenReturn(new AchievementsQueryUseCase.Result(entries));
        EventFixture fixture = event(ACTOR, "Caller");
        InteractionHook hook = mock(InteractionHook.class);
        @SuppressWarnings("unchecked")
        WebhookMessageCreateAction<Message> followUp = mock(WebhookMessageCreateAction.class);
        when(hook.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(followUp);
        when(followUp.setEphemeral(true)).thenReturn(followUp);
        when(followUp.setAllowedMentions(anyCollection())).thenReturn(followUp);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<InteractionHook> callback = invocation.getArgument(0);
            callback.accept(hook);
            return null;
        }).when(fixture.reply()).queue(any());

        listener(achievements).onSlashCommandInteraction(fixture.event());

        verify(fixture.reply()).setEphemeral(true);
        verify(fixture.reply()).setAllowedMentions(List.of());
        verify(hook, atLeastOnce()).sendMessageEmbeds(any(MessageEmbed.class));
        verify(followUp, atLeastOnce()).setEphemeral(true);
        verify(followUp, atLeastOnce()).setAllowedMentions(List.of());
        verify(followUp, atLeastOnce()).queue();
    }

    @Test
    void ignoresOtherGuilds() {
        AchievementsQueryUseCase achievements = mock(AchievementsQueryUseCase.class);
        EventFixture fixture = event(ACTOR, "Caller");
        when(fixture.guild().getIdLong()).thenReturn(GUILD + 1);

        listener(achievements).onSlashCommandInteraction(fixture.event());

        verifyNoInteractions(achievements);
    }

    private DiscordAchievementsCommandListener listener(AchievementsQueryUseCase achievements) {
        return new DiscordAchievementsCommandListener(
                properties(), achievements, new AchievementsOverviewEmbedRenderer(AchievementEmojiResolver.unicodeOnly()));
    }

    private EventFixture event(long actorId, String effectiveName) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        User actor = mock(User.class);
        Member member = mock(Member.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.getName()).thenReturn("achievements");
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(GUILD);
        when(event.getUser()).thenReturn(actor);
        when(event.getMember()).thenReturn(member);
        when(actor.getIdLong()).thenReturn(actorId);
        when(actor.getName()).thenReturn("caller");
        when(member.getEffectiveName()).thenReturn(effectiveName);
        when(event.replyEmbeds(any(MessageEmbed.class))).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);
        when(reply.setAllowedMentions(anyCollection())).thenReturn(reply);
        return new EventFixture(event, guild, reply);
    }

    private GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", GUILD, 12L, List.of()), null, null);
    }

    private record EventFixture(SlashCommandInteractionEvent event, Guild guild, ReplyCallbackAction reply) { }
}
