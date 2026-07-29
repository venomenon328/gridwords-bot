package de.venomenon.gridwordsbot.adapter.discord.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentReference;
import de.venomenon.gridwordsbot.port.out.AttachmentContentLoader.AttachmentTooLargeException;
import de.venomenon.gridwordsbot.port.out.AttachmentContentLoader.AttachmentUnavailableException;
import de.venomenon.gridwordsbot.port.out.AttachmentContentLoader.RetryableAttachmentException;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.NamedAttachmentProxy;
import org.junit.jupiter.api.Test;

class JdaAttachmentContentLoaderTest {

    private static final AttachmentReference REFERENCE = new AttachmentReference(12L, 500L, 700L);

    @Test
    void loadsOnlyTheAttachmentSelectedByTheOpaqueReference() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        Message message = mock(Message.class);
        Message.Attachment selected = attachment(700L, 3, new byte[] {1, 2, 3});
        Message.Attachment other = attachment(701L, 3, new byte[] {4, 5, 6});
        @SuppressWarnings("unchecked")
        RestAction<Message> retrieve = mock(RestAction.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.retrieveMessageById(500L)).thenReturn(retrieve);
        when(retrieve.complete()).thenReturn(message);
        when(message.getAttachments()).thenReturn(List.of(other, selected));

        byte[] content = new JdaAttachmentContentLoader(jda, 10).load(metadata(3));

        assertThat(content).containsExactly(1, 2, 3);
        verify(channel).retrieveMessageById(500L);
        verify(selected).getProxy();
        verify(other, never()).getProxy();
    }

    @Test
    void rejectsAnOversizedDeclaredAttachmentBeforeAnyDiscordCall() {
        JDA jda = mock(JDA.class);

        assertThatThrownBy(() -> new JdaAttachmentContentLoader(jda, 3).load(metadata(4)))
                .isInstanceOf(AttachmentTooLargeException.class);

        verifyNoInteractions(jda);
    }

    @Test
    void enforcesTheLimitWhileReadingWhenDiscordMetadataIsIncorrect() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        Message message = mock(Message.class);
        Message.Attachment selected = attachment(700L, 2, new byte[] {1, 2, 3, 4});
        @SuppressWarnings("unchecked")
        RestAction<Message> retrieve = mock(RestAction.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.retrieveMessageById(500L)).thenReturn(retrieve);
        when(retrieve.complete()).thenReturn(message);
        when(message.getAttachments()).thenReturn(List.of(selected));

        assertThatThrownBy(() -> new JdaAttachmentContentLoader(jda, 3).load(metadata(2)))
                .isInstanceOf(AttachmentTooLargeException.class);
    }

    @Test
    void translatesTransientDiscordFailuresToARetryablePortFailure() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        @SuppressWarnings("unchecked")
        RestAction<Message> retrieve = mock(RestAction.class);
        ErrorResponseException failure = mock(ErrorResponseException.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.retrieveMessageById(500L)).thenReturn(retrieve);
        when(retrieve.complete()).thenThrow(failure);
        when(failure.getErrorResponse()).thenReturn(ErrorResponse.SERVER_ERROR);

        assertThatThrownBy(() -> new JdaAttachmentContentLoader(jda).load(metadata(1)))
                .isInstanceOf(RetryableAttachmentException.class)
                .hasCause(failure);
    }

    @Test
    void translatesMissingDiscordSourceToAnUnavailableAttachment() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        @SuppressWarnings("unchecked")
        RestAction<Message> retrieve = mock(RestAction.class);
        ErrorResponseException failure = mock(ErrorResponseException.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.retrieveMessageById(500L)).thenReturn(retrieve);
        when(retrieve.complete()).thenThrow(failure);
        when(failure.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE);

        assertThatThrownBy(() -> new JdaAttachmentContentLoader(jda).load(metadata(1)))
                .isInstanceOf(AttachmentUnavailableException.class)
                .hasCause(failure);
    }

    @Test
    void rejectsAMetadataOnlyAttachmentWithoutAttemptingToDownloadIt() {
        JDA jda = mock(JDA.class);
        AttachmentMetadata metadata = new AttachmentMetadata("result.png", "image/png", 1);

        assertThatThrownBy(() -> new JdaAttachmentContentLoader(jda).load(metadata))
                .isInstanceOf(AttachmentUnavailableException.class);

        verifyNoInteractions(jda);
    }

    private static AttachmentMetadata metadata(long size) {
        return new AttachmentMetadata("result.png", "image/png", size, Optional.of(REFERENCE));
    }

    private static Message.Attachment attachment(long id, int size, byte[] bytes) {
        Message.Attachment attachment = mock(Message.Attachment.class);
        NamedAttachmentProxy proxy = mock(NamedAttachmentProxy.class);
        when(attachment.getIdLong()).thenReturn(id);
        when(attachment.getSize()).thenReturn(size);
        when(attachment.getProxy()).thenReturn(proxy);
        when(proxy.download()).thenReturn(CompletableFuture.completedFuture(new ByteArrayInputStream(bytes)));
        return attachment;
    }
}
