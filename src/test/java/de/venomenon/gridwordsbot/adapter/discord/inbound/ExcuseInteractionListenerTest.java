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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ExcuseInteractionListenerTest {
    private static final long GUILD_ID = 11L;
    private static final long CHANNEL_ID = 12L;
    private static final long MESSAGE_ID = 13L;
    private static final long EPHEMERAL_MESSAGE_ID = 99L;
    private static final long RESULT_ID = 14L;
    private static final long ACTOR_ID = 15L;

    @Test
    void editsTheExistingEphemeralMessageToRenderTheStyleMenu() {
        ExcuseInteractionUseCase interactions = mock(ExcuseInteractionUseCase.class);
        Fixture fixture = fixture("excuse:v1:reroll:" + RESULT_ID + ":1");
        ExcuseInteractionUseCase.ActionRequest request = request();
        when(interactions.openStyleMenu(request)).thenReturn(new ExcuseInteractionUseCase.StyleMenu(List.of(ExcuseStyle.COSMIC)));

        listener(Runnable::run, interactions).onButtonInteraction(fixture.event());

        verify(fixture.event()).deferEdit();
        verify(fixture.event(), never()).deferReply(true);
        verify(interactions).openStyleMenu(request);
        ArgumentCaptor<List<ActionRow>> components = ArgumentCaptor.forClass(List.class);
        verify(fixture.edit()).setComponents(components.capture());
        assertThat(components.getValue()).singleElement().satisfies(row ->
                assertThat(((StringSelectMenu) row.getComponents().getFirst()).getCustomId()).isEqualTo("excuse:v1:style:14:1"));
        verify(fixture.edit()).queue();
    }

    @Test
    void styleSelectionEditsTheExistingEphemeralMessage() {
        ExcuseInteractionUseCase interactions = mock(ExcuseInteractionUseCase.class);
        SelectFixture fixture = selectFixture("excuse:v1:style:" + RESULT_ID + ":1", ExcuseStyle.SPORTING.name());
        ExcuseInteractionUseCase.StyleRequest request = new ExcuseInteractionUseCase.StyleRequest(request(), ExcuseStyle.SPORTING);
        when(interactions.selectStyle(request)).thenReturn(
                new ExcuseInteractionUseCase.Rejected(ExcuseInteractionUseCase.Reason.REROLL_UNAVAILABLE));

        listener(Runnable::run, interactions).onStringSelectInteraction(fixture.event());

        verify(fixture.event()).deferEdit();
        verify(interactions).selectStyle(request);
        verify(fixture.hook()).editOriginal("Diese Ausrede ist nicht verf\u00fcgbar oder nicht mehr aktuell.");
        verify(fixture.edit()).setEmbeds(List.of());
        verify(fixture.edit()).setComponents(List.of());
        verify(fixture.edit()).queue();
    }

    @Test
    void selectionConfirmsOnTheExistingMessageAndDeletesItAfterTheRefreshHandoff() {
        ExcuseInteractionUseCase interactions = mock(ExcuseInteractionUseCase.class);
        Fixture fixture = fixture("excuse:v1:pick:" + RESULT_ID + ":1:INITIAL:2", EPHEMERAL_MESSAGE_ID);
        when(interactions.pick(new ExcuseInteractionUseCase.PickRequest(request(), ExcuseRound.INITIAL, 2)))
                .thenReturn(ExcuseInteractionUseCase.Selected.INSTANCE);

        listener(Runnable::run, interactions).onButtonInteraction(fixture.event());

        verify(fixture.event()).deferEdit();
        verify(interactions).pick(new ExcuseInteractionUseCase.PickRequest(request(), ExcuseRound.INITIAL, 2));
        verify(fixture.hook()).editOriginal("Die Ausrede wird in der Ergebnisnachricht \u00fcbernommen.");
        verify(fixture.edit()).setEmbeds(List.of());
        verify(fixture.edit()).setComponents(List.of());
        verify(fixture.edit()).queue(any());
        verify(fixture.delete()).queueAfter(2L, TimeUnit.SECONDS);
    }

    @Test
    void declineConfirmsOnTheExistingMessageAndDeletesIt() {
        ExcuseInteractionUseCase interactions = mock(ExcuseInteractionUseCase.class);
        Fixture fixture = fixture("excuse:v1:decline:" + RESULT_ID + ":1", EPHEMERAL_MESSAGE_ID);
        when(interactions.decline(request())).thenReturn(ExcuseInteractionUseCase.Declined.INSTANCE);

        listener(Runnable::run, interactions).onButtonInteraction(fixture.event());

        verify(fixture.event()).deferEdit();
        verify(interactions).decline(request());
        verify(fixture.hook()).editOriginal("Es wurde keine Ausrede ausgew\u00e4hlt.");
        verify(fixture.edit()).setEmbeds(List.of());
        verify(fixture.edit()).setComponents(List.of());
        verify(fixture.delete()).queueAfter(2L, TimeUnit.SECONDS);
    }

    @Test
    void leavesTheCanonicalOpenButtonForItsDedicatedListener() {
        ExcuseInteractionUseCase interactions = mock(ExcuseInteractionUseCase.class);
        Fixture fixture = fixture("excuse:v1:open:" + RESULT_ID);

        listener(Runnable::run, interactions).onButtonInteraction(fixture.event());

        verify(fixture.event(), never()).deferEdit();
        verify(fixture.event(), never()).deferReply(true);
        verifyNoInteractions(interactions);
    }

    private static ExcuseInteractionListener listener(
            java.util.concurrent.Executor executor, ExcuseInteractionUseCase interactions) {
        return new ExcuseInteractionListener(properties(), executor, interactions);
    }

    private static ExcuseInteractionUseCase.ActionRequest request() {
        return new ExcuseInteractionUseCase.ActionRequest(GUILD_ID, CHANNEL_ID, RESULT_ID, ACTOR_ID, 1);
    }

    private static Fixture fixture(String componentId) {
        return fixture(componentId, MESSAGE_ID);
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(String componentId, long messageId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, Mockito.RETURNS_DEEP_STUBS);
        MessageEditCallbackAction deferred = mock(MessageEditCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        AuditableRestAction<Void> delete = mock(AuditableRestAction.class);
        when(event.getComponentId()).thenReturn(componentId);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild().getIdLong()).thenReturn(GUILD_ID);
        when(event.getChannel().getIdLong()).thenReturn(CHANNEL_ID);
        when(event.getMessageIdLong()).thenReturn(messageId);
        when(event.getUser().getIdLong()).thenReturn(ACTOR_ID);
        when(event.deferEdit()).thenReturn(deferred);
        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(deferred).queue(any());
        when(hook.editOriginal(anyString())).thenReturn(edit);
        when(hook.editOriginalEmbeds(any(MessageEmbed[].class))).thenReturn(edit);
        when(hook.deleteOriginal()).thenReturn(delete);
        when(edit.setEmbeds(any(List.class))).thenReturn(edit);
        when(edit.setComponents(any(List.class))).thenReturn(edit);
        doAnswer(invocation -> {
            Consumer<Message> success = invocation.getArgument(0);
            success.accept(mock(Message.class));
            return null;
        }).when(edit).queue(any());
        return new Fixture(event, hook, edit, delete);
    }

    @SuppressWarnings("unchecked")
    private static SelectFixture selectFixture(String componentId, String selectedValue) {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class, Mockito.RETURNS_DEEP_STUBS);
        MessageEditCallbackAction deferred = mock(MessageEditCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(event.getComponentId()).thenReturn(componentId);
        when(event.getValues()).thenReturn(List.of(selectedValue));
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild().getIdLong()).thenReturn(GUILD_ID);
        when(event.getChannel().getIdLong()).thenReturn(CHANNEL_ID);
        when(event.getUser().getIdLong()).thenReturn(ACTOR_ID);
        when(event.deferEdit()).thenReturn(deferred);
        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(deferred).queue(any());
        when(hook.editOriginal(anyString())).thenReturn(edit);
        when(hook.editOriginalEmbeds(any(MessageEmbed[].class))).thenReturn(edit);
        when(edit.setEmbeds(any(List.class))).thenReturn(edit);
        when(edit.setComponents(any(List.class))).thenReturn(edit);
        return new SelectFixture(event, hook, edit);
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", GUILD_ID, CHANNEL_ID, List.of()), null, null);
    }

    private record Fixture(
            ButtonInteractionEvent event,
            InteractionHook hook,
            WebhookMessageEditAction<Message> edit,
            AuditableRestAction<Void> delete) {
    }

    private record SelectFixture(
            StringSelectInteractionEvent event,
            InteractionHook hook,
            WebhookMessageEditAction<Message> edit) {
    }
}
