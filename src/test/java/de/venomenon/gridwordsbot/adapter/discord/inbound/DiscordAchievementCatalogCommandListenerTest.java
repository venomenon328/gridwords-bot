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
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

class DiscordAchievementCatalogCommandListenerTest {
    private static final long GUILD = 11L;
    private static final long ACTOR = 101L;

    @Test
    void commandIsSelfOnlyAndDefinesExactlyTheThreeOptionalFilters() {
        var command = DiscordAchievementCatalogCommandListener.commandData();

        assertThat(command.getName()).isEqualTo("achievement-list");
        assertThat(command.getOptions()).extracting(option -> option.getName())
                .containsExactly("game", "category", "status");
        assertThat(command.getOptions()).allSatisfy(option -> assertThat(option.isRequired()).isFalse());
        assertThat(command.getOptions().get(0).getChoices()).extracting(choice -> choice.getName())
                .containsExactly("Alle", "GridWords", "QuadWords", "GW+QW");
        assertThat(command.getOptions().get(1).getChoices()).extracting(choice -> choice.getName())
                .containsExactly("Alle", "Erfahrung", "Zuverlässigkeit", "Leistung", "Besonderes");
        assertThat(command.getOptions().get(2).getChoices()).extracting(choice -> choice.getName())
                .containsExactly("Alle", "Freigeschaltet", "Offen");
    }

    @Test
    void defaultsToCallerAndAllFiltersWhileReplyingEphemeralAndMentionSafe() {
        AchievementCatalogQueryUseCase catalog = mock(AchievementCatalogQueryUseCase.class);
        when(catalog.query(any())).thenReturn(result());
        EventFixture fixture = event(GUILD);

        listener(catalog).onSlashCommandInteraction(fixture.event());

        verify(catalog).query(new AchievementCatalogQueryUseCase.Query(GUILD, ACTOR));
        verify(fixture.event()).replyEmbeds(anyCollection());
        verify(fixture.reply()).setEphemeral(true);
        verify(fixture.reply()).setAllowedMentions(List.of());
        verify(fixture.reply()).queue();
    }

    @Test
    void mapsAllThreeFiltersToTypedQueryWithoutChangingSelfOnlyTarget() {
        AchievementCatalogQueryUseCase catalog = mock(AchievementCatalogQueryUseCase.class);
        when(catalog.query(any())).thenReturn(result());
        EventFixture fixture = event(GUILD);
        OptionMapping game = option("cross-game");
        OptionMapping category = option("special");
        OptionMapping status = option("open");
        when(fixture.event().getOption("game")).thenReturn(game);
        when(fixture.event().getOption("category")).thenReturn(category);
        when(fixture.event().getOption("status")).thenReturn(status);

        listener(catalog).onSlashCommandInteraction(fixture.event());

        verify(catalog).query(new AchievementCatalogQueryUseCase.Query(
                GUILD,
                ACTOR,
                AchievementCatalogQueryUseCase.GameFilter.CROSS_GAME,
                AchievementCatalogQueryUseCase.CategoryFilter.SPECIAL,
                AchievementCatalogQueryUseCase.StatusFilter.OPEN));
    }

    @Test
    void unknownFilterValuesFallBackToAll() {
        AchievementCatalogQueryUseCase catalog = mock(AchievementCatalogQueryUseCase.class);
        when(catalog.query(any())).thenReturn(result());
        EventFixture fixture = event(GUILD);
        OptionMapping unexpected = option("unexpected");
        when(fixture.event().getOption("game")).thenReturn(unexpected);
        when(fixture.event().getOption("category")).thenReturn(unexpected);
        when(fixture.event().getOption("status")).thenReturn(unexpected);

        listener(catalog).onSlashCommandInteraction(fixture.event());

        verify(catalog).query(new AchievementCatalogQueryUseCase.Query(GUILD, ACTOR));
    }

    @Test
    void ignoresOtherGuilds() {
        AchievementCatalogQueryUseCase catalog = mock(AchievementCatalogQueryUseCase.class);
        EventFixture fixture = event(GUILD + 1);

        listener(catalog).onSlashCommandInteraction(fixture.event());

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

    private static OptionMapping option(String value) {
        OptionMapping option = mock(OptionMapping.class);
        when(option.getAsString()).thenReturn(value);
        return option;
    }

    private static EventFixture event(long guildId) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        User user = mock(User.class);
        ReplyCallbackAction reply = mock(ReplyCallbackAction.class);
        when(event.getName()).thenReturn("achievement-list");
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(guildId);
        when(event.getUser()).thenReturn(user);
        when(user.getIdLong()).thenReturn(ACTOR);
        when(event.replyEmbeds(anyCollection())).thenReturn(reply);
        when(reply.setEphemeral(true)).thenReturn(reply);
        when(reply.setAllowedMentions(anyCollection())).thenReturn(reply);
        return new EventFixture(event, reply);
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", GUILD, 12L, List.of()), null, null);
    }

    private record EventFixture(SlashCommandInteractionEvent event, ReplyCallbackAction reply) { }
}
