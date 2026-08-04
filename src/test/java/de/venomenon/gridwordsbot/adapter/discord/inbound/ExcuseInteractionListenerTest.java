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
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.port.in.ExcuseInteractionUseCase;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ExcuseInteractionListenerTest {
    private static final long GUILD_ID = 11L;
    private static final long CHANNEL_ID = 12L;
    private static final long MESSAGE_ID = 13L;
    private static final long RESULT_ID = 14L;
    private static final long ACTOR_ID = 15L;

    @Test
    void defersAndRendersTheStyleMenuOnlyEphemerally() {
        ExcuseInteractionUseCase interactions = mock(ExcuseInteractionUseCase.class);
        Fixture fixture = fixture("excuse:v1:reroll:" + RESULT_ID + ":1");
        ExcuseInteractionUseCase.ActionRequest request = request();
        when(interactions.openStyleMenu(request)).thenReturn(new ExcuseInteractionUseCase.StyleMenu(List.of(ExcuseStyle.COSMIC)));

        listener(Runnable::run, interactions).onButtonInteraction(fixture.event());

        verify(fixture.event()).deferReply(true);
        verify(interactions).openStyleMenu(request);
        ArgumentCaptor<List<ActionRow>> components = ArgumentCaptor.forClass(List.class);
        verify(fixture.edit()).setComponents(components.capture());
        assertThat(components.getValue()).singleElement().satisfies(row ->
                assertThat(((StringSelectMenu) row.getComponents().getFirst()).getCustomId()).isEqualTo("excuse:v1:style:14:1"));
        verify(fixture.edit()).queue();
    }

    @Test
    void selectionConfirmsEphemerallyAfterTheUseCaseHasHandedOffTheRefresh() {
        ExcuseInteractionUseCase interactions = mock(ExcuseInteractionUseCase.class);
        Fixture fixture = fixture("excuse:v1:pick:" + RESULT_ID + ":1:INITIAL:2");
        when(interactions.pick(new ExcuseInteractionUseCase.PickRequest(request(), ExcuseRound.INITIAL, 2)))
                .thenReturn(ExcuseInteractionUseCase.Selected.INSTANCE);

        listener(Runnable::run, interactions).onButtonInteraction(fixture.event());

        verify(interactions).pick(new ExcuseInteractionUseCase.PickRequest(request(), ExcuseRound.INITIAL, 2));
        verify(fixture.hook()).editOriginal("Die Ausrede wird in der Ergebnisnachricht übernommen.");
        verify(fixture.edit()).queue();
    }

    @Test
    void leavesTheCanonicalOpenButtonForItsDedicatedListener() {
        ExcuseInteractionUseCase interactions = mock(ExcuseInteractionUseCase.class);
        Fixture fixture = fixture("excuse:v1:open:" + RESULT_ID);

        listener(Runnable::run, interactions).onButtonInteraction(fixture.event());

        verify(fixture.event(), never()).deferReply(true);
        verifyNoInteractions(interactions);
    }

    private static ExcuseInteractionListener listener(
            java.util.concurrent.Executor executor, ExcuseInteractionUseCase interactions) {
        return new ExcuseInteractionListener(properties(), executor, interactions);
    }

    private static ExcuseInteractionUseCase.ActionRequest request() {
        return new ExcuseInteractionUseCase.ActionRequest(GUILD_ID, CHANNEL_ID, MESSAGE_ID, RESULT_ID, ACTOR_ID, 1);
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(String componentId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, Mockito.RETURNS_DEEP_STUBS);
        ReplyCallbackAction deferred = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(event.getComponentId()).thenReturn(componentId);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild().getIdLong()).thenReturn(GUILD_ID);
        when(event.getChannel().getIdLong()).thenReturn(CHANNEL_ID);
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
