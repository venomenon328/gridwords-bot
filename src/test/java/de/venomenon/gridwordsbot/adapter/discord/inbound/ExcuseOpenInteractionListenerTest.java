package de.venomenon.gridwordsbot.adapter.discord.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTopic;
import de.venomenon.gridwordsbot.port.in.ExcuseOpenUseCase;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ExcuseOpenInteractionListenerTest {
    private static final long GUILD_ID = 11L;
    private static final long CHANNEL_ID = 12L;
    private static final long MESSAGE_ID = 13L;
    private static final long RESULT_ID = 14L;
    private static final long ACTOR_ID = 15L;

    @Test
    void defersEphemerallyBeforeOpeningThePersistedOptions() {
        ExcuseOpenUseCase open = mock(ExcuseOpenUseCase.class);
        Fixture fixture = fixture("excuse:v1:open:" + RESULT_ID, GUILD_ID, CHANNEL_ID);
        ExcuseOpenUseCase.Request request = new ExcuseOpenUseCase.Request(
                GUILD_ID, CHANNEL_ID, MESSAGE_ID, RESULT_ID, ACTOR_ID);
        when(open.open(request)).thenReturn(new ExcuseOpenUseCase.Options(options()));

        new ExcuseOpenInteractionListener(properties(), command -> {
            verify(fixture.event()).deferReply(true);
            command.run();
        }, open).onButtonInteraction(fixture.event());

        verify(open).open(request);
        ArgumentCaptor<MessageEmbed[]> embeds = ArgumentCaptor.forClass(MessageEmbed[].class);
        verify(fixture.hook()).editOriginalEmbeds(embeds.capture());
        assertThat(embeds.getValue()).singleElement().satisfies(embed ->
                assertThat(embed.getDescription()).contains("technisch", "Text eins", "Text drei"));
        verify(fixture.edit()).queue();
    }

    @Test
    void ignoresUnknownCodecVersionsWithoutAcknowledgingThemSoTheirOwnDispatcherCanRespond() {
        ExcuseOpenUseCase open = mock(ExcuseOpenUseCase.class);
        Fixture fixture = fixture("excuse:v2:open:" + RESULT_ID, GUILD_ID, CHANNEL_ID);

        listener(Runnable::run, open).onButtonInteraction(fixture.event());

        verify(fixture.event(), never()).deferReply(true);
        verifyNoInteractions(open);
    }

    @Test
    void ignoresFollowUpExcuseActionsWithoutAcknowledgingThemTwice() {
        ExcuseOpenUseCase open = mock(ExcuseOpenUseCase.class);
        Fixture fixture = fixture("excuse:v1:decline:" + RESULT_ID + ":1", GUILD_ID, CHANNEL_ID);

        listener(Runnable::run, open).onButtonInteraction(fixture.event());

        verify(fixture.event(), never()).deferReply(true);
        verifyNoInteractions(open);
    }

    @Test
    void givesOnlyTheAuthorAnEphemeralAuthorizationHint() {
        ExcuseOpenUseCase open = mock(ExcuseOpenUseCase.class);
        Fixture fixture = fixture("excuse:v1:open:" + RESULT_ID, GUILD_ID, CHANNEL_ID);
        when(open.open(any())).thenReturn(new ExcuseOpenUseCase.Rejected(ExcuseOpenUseCase.Reason.NOT_RESULT_AUTHOR));

        listener(Runnable::run, open).onButtonInteraction(fixture.event());

        verify(fixture.hook()).editOriginal("Diesen Button kann nur der Ergebnisautor verwenden.");
        verify(fixture.edit()).queue();
    }

    @Test
    void reportsAFullWorkerQueueAfterTheEphemeralDefer() {
        ExcuseOpenUseCase open = mock(ExcuseOpenUseCase.class);
        Fixture fixture = fixture("excuse:v1:open:" + RESULT_ID, GUILD_ID, CHANNEL_ID);

        listener(command -> { throw new java.util.concurrent.RejectedExecutionException("full"); }, open)
                .onButtonInteraction(fixture.event());

        verify(fixture.hook()).editOriginal("Der Bot ist gerade ausgelastet. Bitte versuche es erneut.");
        verify(fixture.edit()).queue();
        verifyNoInteractions(open);
    }

    @Test
    void ignoresButtonsOutsideTheConfiguredGuildOrChannel() {
        ExcuseOpenUseCase open = mock(ExcuseOpenUseCase.class);
        Fixture fixture = fixture("excuse:v1:open:" + RESULT_ID, GUILD_ID, CHANNEL_ID + 1);

        listener(Runnable::run, open).onButtonInteraction(fixture.event());

        verify(fixture.event(), never()).deferReply(true);
        verifyNoInteractions(open);
    }

    private static ExcuseOpenInteractionListener listener(java.util.concurrent.Executor executor, ExcuseOpenUseCase open) {
        return new ExcuseOpenInteractionListener(properties(), executor, open);
    }

    private static List<ExcuseOption> options() {
        return List.of(
                new ExcuseOption(ExcuseRound.INITIAL, 1, "one", ExcuseStyle.TECHNICAL,
                        ExcuseTopic.TECHNICAL_FAILURE, "Text eins"),
                new ExcuseOption(ExcuseRound.INITIAL, 2, "two", ExcuseStyle.TACTICAL,
                        ExcuseTopic.LONG_TERM_PLAN, "Text zwei"),
                new ExcuseOption(ExcuseRound.INITIAL, 3, "three", ExcuseStyle.LEGAL,
                        ExcuseTopic.RESPONSIBILITY, "Text drei"));
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(String componentId, long guildId, long channelId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, Mockito.RETURNS_DEEP_STUBS);
        ReplyCallbackAction deferred = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(event.getComponentId()).thenReturn(componentId);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild().getIdLong()).thenReturn(guildId);
        when(event.getChannel().getIdLong()).thenReturn(channelId);
        when(event.getMessageIdLong()).thenReturn(MESSAGE_ID);
        when(event.getUser().getIdLong()).thenReturn(ACTOR_ID);
        when(event.deferReply(true)).thenReturn(deferred);
        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(deferred).queue(any());
        when(hook.editOriginal(anyString())).thenReturn(edit);
        when(hook.editOriginalEmbeds(any(MessageEmbed[].class))).thenReturn(edit);
        when(edit.setComponents(any(List.class))).thenReturn(edit);
        return new Fixture(event, hook, edit);
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", GUILD_ID, CHANNEL_ID, List.of()), null, null);
    }

    private record Fixture(
            ButtonInteractionEvent event,
            InteractionHook hook,
            WebhookMessageEditAction<Message> edit) {
    }
}
