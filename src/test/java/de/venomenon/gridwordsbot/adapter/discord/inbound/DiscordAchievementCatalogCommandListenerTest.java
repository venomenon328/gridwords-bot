package de.venomenon.gridwordsbot.adapter.discord.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.adapter.discord.achievement.AchievementCatalogEmbedRenderer;
import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.port.in.AchievementCatalogQueryUseCase;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

class DiscordAchievementCatalogCommandListenerTest {
    private static final long GUILD = 11L;
    private static final long ACTOR = 101L;

    @Test
    void commandIsSelfOnlyAndHasNoOptions() {
        var command = DiscordAchievementCatalogCommandListener.commandData();

        assertThat(command.getName()).isEqualTo("achievement-list");
        assertThat(command.getOptions()).isEmpty();
    }

    @Test
    void queriesOnlyCallerAndRepliesOnceEphemeralAndMentionSafe() {
        AchievementCatalogQueryUseCase catalog = mock(AchievementCatalogQueryUseCase.class);
        when(catalog.query(any())).thenReturn(result());
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        User user = mock(User.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.getName()).thenReturn("achievement-list");
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(GUILD);
        when(event.getUser()).thenReturn(user);
        when(user.getIdLong()).thenReturn(ACTOR);
        when(event.replyEmbeds(anyCollection())).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);
        when(reply.setAllowedMentions(anyCollection())).thenReturn(reply);

        listener(catalog).onSlashCommandInteraction(event);

        verify(catalog).query(new AchievementCatalogQueryUseCase.Query(GUILD, ACTOR));
        verify(event).replyEmbeds(anyCollection());
        verify(reply).setEphemeral(true);
        verify(reply).setAllowedMentions(List.of());
        verify(reply).queue();
    }

    @Test
    void ignoresOtherGuilds() {
        AchievementCatalogQueryUseCase catalog = mock(AchievementCatalogQueryUseCase.class);
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        when(event.getName()).thenReturn("achievement-list");
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(GUILD + 1);

        listener(catalog).onSlashCommandInteraction(event);

        verifyNoInteractions(catalog);
    }

    private static DiscordAchievementCatalogCommandListener listener(AchievementCatalogQueryUseCase catalog) {
        return new DiscordAchievementCatalogCommandListener(
                properties(), catalog, new AchievementCatalogEmbedRenderer(AchievementEmojiResolver.unicodeOnly()));
    }

    private static AchievementCatalogQueryUseCase.Result result() {
        var definitions = AchievementDefinitionCatalog.achievementsV1().definitions();
        return new AchievementCatalogQueryUseCase.Result(definitions.stream()
                .map(definition -> new AchievementCatalogQueryUseCase.Entry(
                        definition.key(), definition.category(), definition.scope(), definition.fallbackEmoji(),
                        definition.displayName(), definition.description(), false))
                .toList());
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", GUILD, 12L, List.of()), null, null);
    }
}
