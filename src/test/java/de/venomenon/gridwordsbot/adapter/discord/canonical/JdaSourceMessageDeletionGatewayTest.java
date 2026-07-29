package de.venomenon.gridwordsbot.adapter.discord.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.junit.jupiter.api.Test;

class JdaSourceMessageDeletionGatewayTest {

    @Test
    void deletesOnlyTheExactSourceMessageInTheConfiguredChannel() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        @SuppressWarnings("unchecked")
        AuditableRestAction<Void> delete = mock(AuditableRestAction.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.deleteMessageById(10L)).thenReturn(delete);

        SourceMessageDeletionGateway.DeletionResult result =
                new JdaSourceMessageDeletionGateway(jda).deleteSourceMessage(12L, 10L);

        assertThat(result).isEqualTo(SourceMessageDeletionGateway.DeletionResult.DELETED);
        verify(channel).deleteMessageById(10L);
        verify(channel, never()).deleteMessageById(99L);
    }

    @Test
    void classifiesUnknownMessageAsAnIdempotentSuccess() {
        assertThat(resultFor(ErrorResponse.UNKNOWN_MESSAGE))
                .isEqualTo(SourceMessageDeletionGateway.DeletionResult.ALREADY_MISSING);
    }

    @Test
    void classifiesPermissionsAndAccessAsPermanent() {
        assertThat(resultFor(ErrorResponse.MISSING_ACCESS))
                .isEqualTo(SourceMessageDeletionGateway.DeletionResult.PERMANENT_FAILURE);
        assertThat(resultFor(ErrorResponse.MISSING_PERMISSIONS))
                .isEqualTo(SourceMessageDeletionGateway.DeletionResult.PERMANENT_FAILURE);
    }

    @Test
    void classifiesOtherDiscordFailuresAsRetryable() {
        assertThat(resultFor(ErrorResponse.SERVER_ERROR))
                .isEqualTo(SourceMessageDeletionGateway.DeletionResult.RETRYABLE_FAILURE);
    }

    private static SourceMessageDeletionGateway.DeletionResult resultFor(ErrorResponse error) {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        @SuppressWarnings("unchecked")
        AuditableRestAction<Void> delete = mock(AuditableRestAction.class);
        ErrorResponseException failure = mock(ErrorResponseException.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.deleteMessageById(10L)).thenReturn(delete);
        when(delete.complete()).thenThrow(failure);
        when(failure.getErrorResponse()).thenReturn(error);

        return new JdaSourceMessageDeletionGateway(jda).deleteSourceMessage(12L, 10L);
    }
}
