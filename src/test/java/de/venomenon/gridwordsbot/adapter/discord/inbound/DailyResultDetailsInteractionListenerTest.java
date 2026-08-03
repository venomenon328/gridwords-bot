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
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DailyResultDetailsInteractionListenerTest {
    private static final long GUILD_ID = 11L;
    private static final long CHANNEL_ID = 12L;
    private static final long MESSAGE_ID = 13L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);

    @Test
    void defersEphemerallyBeforeRunningTheReadOnlyUseCase() {
        DailyResultDetailsUseCase details = mock(DailyResultDetailsUseCase.class);
        Fixture fixture = fixture("daily-result:v1:2026-08-03:g:0", List.of("user:42"), GUILD_ID, CHANNEL_ID);
        DailyResultDetailsUseCase.Request expected = new DailyResultDetailsUseCase.Request(
                GUILD_ID, CHANNEL_ID, MESSAGE_ID, DATE, GameType.GRIDWORDS, 0, 42L);
        when(details.get(expected)).thenReturn(new DailyResultDetailsUseCase.Missing("Player", GameType.GRIDWORDS, DATE));

        new DailyResultDetailsInteractionListener(properties(), command -> {
            verify(fixture.event()).deferReply(true);
            command.run();
        }, details).onStringSelectInteraction(fixture.event());

        verify(details).get(expected);
        verify(fixture.hook()).editOriginalEmbeds(any(MessageEmbed[].class));
        verify(fixture.edit()).queue();
        verify(fixture.hook(), never()).editOriginal(anyString());
    }

    @Test
    void rejectsManipulatedComponentValuesEphemerallyWithoutReadingPersistence() {
        DailyResultDetailsUseCase details = mock(DailyResultDetailsUseCase.class);
        Fixture fixture = fixture("daily-result:v2:2026-08-03:g:0", List.of("user:42"), GUILD_ID, CHANNEL_ID);

        listener(Runnable::run, details).onStringSelectInteraction(fixture.event());

        verify(fixture.hook()).editOriginal("Diese Auswahl ist ungültig oder veraltet.");
        verify(fixture.edit()).queue();
        verifyNoInteractions(details);
    }

    @Test
    void rendersServerSideRejectionWithoutExposingItsInternalReason() {
        DailyResultDetailsUseCase details = mock(DailyResultDetailsUseCase.class);
        Fixture fixture = fixture("daily-result:v1:2026-08-03:q:0", List.of("user:42"), GUILD_ID, CHANNEL_ID);
        when(details.get(any())).thenReturn(new DailyResultDetailsUseCase.Rejected(
                DailyResultDetailsUseCase.Reason.STATUS_NOT_CURRENT));

        listener(Runnable::run, details).onStringSelectInteraction(fixture.event());

        ArgumentCaptor<MessageEmbed[]> embeds = ArgumentCaptor.forClass(MessageEmbed[].class);
        verify(fixture.hook()).editOriginalEmbeds(embeds.capture());
        assertThat(embeds.getValue()).singleElement().satisfies(embed ->
                assertThat(embed.getDescription())
                        .contains("aktuelle Tagesnachricht")
                        .doesNotContain("STATUS_NOT_CURRENT"));
    }

    @Test
    void ignoresComponentsOutsideTheConfiguredGuildOrChannel() {
        DailyResultDetailsUseCase details = mock(DailyResultDetailsUseCase.class);
        Fixture wrongChannel = fixture(
                "daily-result:v1:2026-08-03:g:0", List.of("user:42"), GUILD_ID, CHANNEL_ID + 1);

        listener(Runnable::run, details).onStringSelectInteraction(wrongChannel.event());

        verify(wrongChannel.event(), never()).deferReply(true);
        verifyNoInteractions(details);
    }

    @Test
    void reportsAFullWorkerQueueEphemerally() {
        DailyResultDetailsUseCase details = mock(DailyResultDetailsUseCase.class);
        Fixture fixture = fixture("daily-result:v1:2026-08-03:g:0", List.of("user:42"), GUILD_ID, CHANNEL_ID);

        listener(command -> {
            throw new java.util.concurrent.RejectedExecutionException("full");
        }, details).onStringSelectInteraction(fixture.event());

        verify(fixture.hook()).editOriginal("Der Bot ist gerade ausgelastet. Bitte versuche es erneut.");
        verify(fixture.edit()).queue();
        verifyNoInteractions(details);
    }

    @Test
    void convertsUnexpectedWorkerFailuresToASafeEphemeralReply() {
        DailyResultDetailsUseCase details = mock(DailyResultDetailsUseCase.class);
        Fixture fixture = fixture("daily-result:v1:2026-08-03:g:0", List.of("user:42"), GUILD_ID, CHANNEL_ID);
        when(details.get(any())).thenThrow(new IllegalStateException("database secret"));

        listener(Runnable::run, details).onStringSelectInteraction(fixture.event());

        verify(fixture.hook()).editOriginal(
                "Die Ergebnisdetails konnten nicht geladen werden. Bitte versuche es erneut.");
        verify(fixture.edit()).queue();
    }

    private static DailyResultDetailsInteractionListener listener(
            java.util.concurrent.Executor executor,
            DailyResultDetailsUseCase details) {
        return new DailyResultDetailsInteractionListener(properties(), executor, details);
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(
            String componentId,
            List<String> values,
            long guildId,
            long channelId) {
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class, Mockito.RETURNS_DEEP_STUBS);
        ReplyCallbackAction deferred = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> edit = mock(WebhookMessageEditAction.class);
        when(event.getComponentId()).thenReturn(componentId);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild().getIdLong()).thenReturn(guildId);
        when(event.getChannel().getIdLong()).thenReturn(channelId);
        when(event.getMessageIdLong()).thenReturn(MESSAGE_ID);
        when(event.getValues()).thenReturn(values);
        when(event.deferReply(true)).thenReturn(deferred);
        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(deferred).queue(any());
        when(hook.editOriginal(anyString())).thenReturn(edit);
        when(hook.editOriginalEmbeds(any(MessageEmbed[].class))).thenReturn(edit);
        return new Fixture(event, hook, edit);
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", GUILD_ID, CHANNEL_ID, List.of()),
                null,
                null);
    }

    private record Fixture(
            StringSelectInteractionEvent event,
            InteractionHook hook,
            WebhookMessageEditAction<Message> edit) {
    }
}
