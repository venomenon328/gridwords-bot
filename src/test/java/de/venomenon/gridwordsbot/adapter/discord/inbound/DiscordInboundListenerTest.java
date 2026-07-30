package de.venomenon.gridwordsbot.adapter.discord.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentReference;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DiscordInboundListenerTest {

    private static final long GUILD = 11L;
    private static final long CHANNEL = 12L;
    private static final long TOBIAS = 101L;
    private static final long GEORGIA = 102L;

    @Test
    void ignoresWrongGuildChannelForeignUserBotsAndWebhooks() {
        assertIgnored(event(GUILD + 1, CHANNEL, TOBIAS, false, false));
        assertIgnored(event(GUILD, CHANNEL + 1, TOBIAS, false, false));
        assertIgnored(event(GUILD, CHANNEL, TOBIAS, true, false));
        assertIgnored(event(GUILD, CHANNEL, TOBIAS, false, true));
    }

    @Test
    void delegatesARelevantMessageOnceAndReactAfterAcceptedPersistence() {
        QueueingExecutor executor = new QueueingExecutor();
        ProcessSharedResultUseCase useCase = mock(ProcessSharedResultUseCase.class);
        DiscordReactionGateway reactions = mock(DiscordReactionGateway.class);
        MessageReceivedEvent event = event(GUILD, CHANNEL, TOBIAS, false, false);
        Message message = event.getMessage();
        when(useCase.process(any())).thenReturn(new ProcessingResult.Accepted(GameType.QUADWORDS));

        listener(executor, useCase, reactions).onMessageReceived(event);

        verifyNoInteractions(useCase, reactions);
        executor.runAll();

        ArgumentCaptor<InboundSharedMessage> inbound = ArgumentCaptor.forClass(InboundSharedMessage.class);
        verify(useCase).process(inbound.capture());
        assertThat(inbound.getValue().guildId()).isEqualTo(GUILD);
        assertThat(inbound.getValue().channelId()).isEqualTo(CHANNEL);
        assertThat(inbound.getValue().messageId()).isEqualTo(500L);
        assertThat(inbound.getValue().authorId()).isEqualTo(TOBIAS);
        verifyNoInteractions(reactions);
    }

    @Test
    void copiesTheExactAttachmentIdIntoTheTransportNeutralInboundSnapshot() {
        QueueingExecutor executor = new QueueingExecutor();
        ProcessSharedResultUseCase useCase = mock(ProcessSharedResultUseCase.class);
        DiscordReactionGateway reactions = mock(DiscordReactionGateway.class);
        MessageReceivedEvent event = event(GUILD, CHANNEL, TOBIAS, false, false);
        Message.Attachment attachment = mock(Message.Attachment.class);
        when(attachment.getFileName()).thenReturn("quadwords.png");
        when(attachment.getContentType()).thenReturn("image/png");
        when(attachment.getSize()).thenReturn(42);
        when(attachment.getIdLong()).thenReturn(700L);
        when(event.getMessage().getAttachments()).thenReturn(List.of(attachment));
        when(useCase.process(any())).thenReturn(new ProcessingResult.Ignored());

        listener(executor, useCase, reactions).onMessageReceived(event);
        executor.runAll();

        ArgumentCaptor<InboundSharedMessage> inbound = ArgumentCaptor.forClass(InboundSharedMessage.class);
        verify(useCase).process(inbound.capture());
        assertThat(inbound.getValue().attachments()).singleElement().satisfies(metadata ->
                assertThat(metadata.reference()).contains(new AttachmentReference(CHANNEL, 500L, 700L)));
        verify(attachment, never()).getUrl();
    }
    @Test
    void reactsWithWarningOnlyAfterRejectedProcessing() {
        QueueingExecutor executor = new QueueingExecutor();
        ProcessSharedResultUseCase useCase = mock(ProcessSharedResultUseCase.class);
        DiscordReactionGateway reactions = mock(DiscordReactionGateway.class);
        MessageReceivedEvent event = event(GUILD, CHANNEL, TOBIAS, false, false);
        when(useCase.process(any())).thenReturn(new ProcessingResult.Rejected("MISSING_BOARD"));

        listener(executor, useCase, reactions).onMessageReceived(event);
        executor.runAll();

        verify(reactions).addReaction(event.getMessage(), "\u26A0\uFE0F");
    }

    @Test
    void doesNotReactForIgnoredOrTechnicalFailures() {
        QueueingExecutor executor = new QueueingExecutor();
        ProcessSharedResultUseCase useCase = mock(ProcessSharedResultUseCase.class);
        DiscordReactionGateway reactions = mock(DiscordReactionGateway.class);
        MessageReceivedEvent event = event(GUILD, CHANNEL, TOBIAS, false, false);
        when(useCase.process(any()))
                .thenReturn(new ProcessingResult.Ignored())
                .thenThrow(new IllegalStateException("persistence unavailable"));
        DiscordInboundListener listener = listener(executor, useCase, reactions);

        listener.onMessageReceived(event);
        listener.onMessageReceived(event(GUILD, CHANNEL, GEORGIA, false, false));
        executor.runAll();

        verifyNoInteractions(reactions);
    }

    @Test
    void handlesAFullQueueWithoutInvokingTheUseCaseOrReacting() {
        Executor rejecting = command -> {
            throw new RejectedExecutionException("queue full");
        };
        ProcessSharedResultUseCase useCase = mock(ProcessSharedResultUseCase.class);
        DiscordReactionGateway reactions = mock(DiscordReactionGateway.class);

        listener(rejecting, useCase, reactions).onMessageReceived(event(GUILD, CHANNEL, TOBIAS, false, false));

        verifyNoInteractions(useCase, reactions);
    }

    private void assertIgnored(MessageReceivedEvent event) {
        QueueingExecutor executor = new QueueingExecutor();
        ProcessSharedResultUseCase useCase = mock(ProcessSharedResultUseCase.class);
        DiscordReactionGateway reactions = mock(DiscordReactionGateway.class);

        listener(executor, useCase, reactions).onMessageReceived(event);

        assertThat(executor.tasks).isEmpty();
        verifyNoInteractions(useCase, reactions);
    }

    private DiscordInboundListener listener(Executor executor, ProcessSharedResultUseCase useCase, DiscordReactionGateway reactions) {
        return new DiscordInboundListener(
                properties(), Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC), executor, useCase, reactions);
    }

    private GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", GUILD, CHANNEL, List.of(TOBIAS)), null, null);
    }

    private MessageReceivedEvent event(long guildId, long channelId, long authorId, boolean bot, boolean webhook) {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        Guild guild = mock(Guild.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        User author = mock(User.class);
        Member member = mock(Member.class);
        Message message = mock(Message.class);
        when(event.isFromGuild()).thenReturn(true);
        when(event.isWebhookMessage()).thenReturn(webhook);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(guildId);
        when(event.getChannel()).thenReturn(channel);
        when(channel.getIdLong()).thenReturn(channelId);
        when(event.getAuthor()).thenReturn(author);
        when(author.getIdLong()).thenReturn(authorId);
        when(author.isBot()).thenReturn(bot);
        when(author.getName()).thenReturn("Player");
        when(event.getMember()).thenReturn(member);
        when(member.getEffectiveName()).thenReturn("Player");
        when(event.getMessage()).thenReturn(message);
        when(message.getIdLong()).thenReturn(500L);
        when(message.getContentRaw()).thenReturn("GridWords (29. Juli 2026) 3/6 in 1:25");
        when(message.getAttachments()).thenReturn(List.of());
        return event;
    }

    private static final class QueueingExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runAll() {
            List<Runnable> queued = List.copyOf(tasks);
            tasks.clear();
            queued.forEach(Runnable::run);
        }
    }
}
