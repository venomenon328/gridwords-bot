package de.venomenon.gridwordsbot.adapter.discord.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.application.record.RenderedRecordAnnouncementPage;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementMessageGateway;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JdaRecordAnnouncementMessageGatewayTest {

    @Test
    void createsAndEditsPagesWithoutVisibleTechnicalMarker() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        MessageCreateAction create = mock(MessageCreateAction.class);
        Message sent = mock(Message.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(create);
        when(create.setNonce(any())).thenReturn(create);
        when(create.setAllowedMentions(any())).thenReturn(create);
        when(create.complete()).thenReturn(sent);
        when(sent.getIdLong()).thenReturn(99L);
        JdaRecordAnnouncementMessageGateway gateway = new JdaRecordAnnouncementMessageGateway(jda);
        ArgumentCaptor<MessageEmbed> createdEmbed = ArgumentCaptor.forClass(MessageEmbed.class);
        ArgumentCaptor<String> nonce = ArgumentCaptor.forClass(String.class);

        assertThat(gateway.create(12L, page())).isEqualTo(99L);

        verify(channel).sendMessageEmbeds(createdEmbed.capture());
        assertThat(createdEmbed.getValue().getTitle()).isEqualTo("Neuer Rekord");
        assertThat(createdEmbed.getValue().getFooter()).isNull();
        verify(create).setNonce(nonce.capture());
        assertThat(nonce.getValue()).startsWith("ra:").doesNotContain("record-announcement");
        assertThat(nonce.getValue().length()).isLessThanOrEqualTo(Message.MAX_NONCE_LENGTH);
        verify(create).setAllowedMentions(Collections.emptyList());

        RestAction<Message> lookup = mock(RestAction.class);
        Message existing = mock(Message.class);
        MessageEditAction edit = mock(MessageEditAction.class);
        when(channel.retrieveMessageById(99L)).thenReturn(lookup);
        when(lookup.complete()).thenReturn(existing);
        when(existing.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(edit);
        when(edit.setAllowedMentions(any())).thenReturn(edit);
        ArgumentCaptor<MessageEmbed> editedEmbed = ArgumentCaptor.forClass(MessageEmbed.class);

        gateway.edit(12L, 99L, page());

        verify(existing).editMessageEmbeds(editedEmbed.capture());
        assertThat(editedEmbed.getValue().getFooter()).isNull();
        verify(edit).setAllowedMentions(Collections.emptyList());
    }

    @Test
    void findsOnlyOwnPublicationPagesByNonceInStablePositionAndMessageOrder() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        MessageHistory history = mock(MessageHistory.class);
        RestAction<List<Message>> request = mock(RestAction.class);
        SelfUser self = mock(SelfUser.class);
        User other = mock(User.class);
        String key = "record-announcement:" + "a".repeat(64);
        Message second = messageWithNonce(70L, self, JdaRecordAnnouncementMessageGateway.publicationNonce(key, 1));
        Message firstLaterId = messageWithNonce(90L, self, JdaRecordAnnouncementMessageGateway.publicationNonce(key, 0));
        Message first = messageWithNonce(30L, self, JdaRecordAnnouncementMessageGateway.publicationNonce(key, 0));
        Message foreign = messageWithNonce(20L, other, JdaRecordAnnouncementMessageGateway.publicationNonce(key, 0));
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(jda.getSelfUser()).thenReturn(self);
        when(channel.getHistory()).thenReturn(history);
        when(history.retrievePast(100)).thenReturn(request);
        when(request.complete()).thenReturn(List.of(second, firstLaterId, first, foreign));

        assertThat(new JdaRecordAnnouncementMessageGateway(jda).findByPublicationKey(12L, key))
                .containsExactly(
                        new RecordAnnouncementMessageGateway.PublishedPage(30L, 0),
                        new RecordAnnouncementMessageGateway.PublishedPage(90L, 0),
                        new RecordAnnouncementMessageGateway.PublishedPage(70L, 1));
    }

    @Test
    void stillFindsLegacyFooterMarkerDuringUnreleasedUpgrade() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        MessageHistory history = mock(MessageHistory.class);
        RestAction<List<Message>> request = mock(RestAction.class);
        SelfUser self = mock(SelfUser.class);
        String key = "record-announcement:" + "b".repeat(64);
        Message legacy = messageWithFooter(41L, self, key + "|page:2/2");
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(jda.getSelfUser()).thenReturn(self);
        when(channel.getHistory()).thenReturn(history);
        when(history.retrievePast(100)).thenReturn(request);
        when(request.complete()).thenReturn(List.of(legacy));

        assertThat(new JdaRecordAnnouncementMessageGateway(jda).findByPublicationKey(12L, key))
                .containsExactly(new RecordAnnouncementMessageGateway.PublishedPage(41L, 1));
    }

    @Test
    void publicationNonceIsDeterministicDistinctPerPageAndWithinDiscordLimit() {
        String key = "record-announcement:" + "c".repeat(64);

        String first = JdaRecordAnnouncementMessageGateway.publicationNonce(key, 0);
        String same = JdaRecordAnnouncementMessageGateway.publicationNonce(key, 0);
        String second = JdaRecordAnnouncementMessageGateway.publicationNonce(key, 1);

        assertThat(first).isEqualTo(same).isNotEqualTo(second);
        assertThat(first.length()).isLessThanOrEqualTo(Message.MAX_NONCE_LENGTH);
        assertThat(second.length()).isLessThanOrEqualTo(Message.MAX_NONCE_LENGTH);
    }

    @Test
    void treatsUnknownMessageDuringDeleteAsSuccess() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        ErrorResponseException unknown = mock(ErrorResponseException.class);
        when(unknown.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        AuditableRestAction<Void> deletion = mock(AuditableRestAction.class);
        when(channel.deleteMessageById(99L)).thenReturn(deletion);
        doThrow(unknown).when(deletion).complete();

        JdaRecordAnnouncementMessageGateway gateway = new JdaRecordAnnouncementMessageGateway(jda);

        assertThatCode(() -> gateway.delete(12L, 99L)).doesNotThrowAnyException();
    }

    @Test
    void classifiesMissingPermissionDuringCreateAsPermanent() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        ErrorResponseException missingPermission = mock(ErrorResponseException.class);
        when(missingPermission.getErrorResponse()).thenReturn(ErrorResponse.MISSING_PERMISSIONS);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        MessageCreateAction create = mock(MessageCreateAction.class);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(create);
        when(create.setNonce(any())).thenReturn(create);
        when(create.setAllowedMentions(any())).thenReturn(create);
        doThrow(missingPermission).when(create).complete();

        JdaRecordAnnouncementMessageGateway gateway = new JdaRecordAnnouncementMessageGateway(jda);

        assertThatThrownBy(() -> gateway.create(12L, page()))
                .isInstanceOf(RecordAnnouncementMessageGateway.PermanentMessageException.class);
    }

    private static RenderedRecordAnnouncementPage page() {
        return new RenderedRecordAnnouncementPage(0, "Neuer Rekord", "Ada erreicht etwas Neues.",
                "record-announcement:" + "a".repeat(64) + "|page:1/1");
    }

    private static Message messageWithNonce(long id, User author, String nonce) {
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(id);
        when(message.getAuthor()).thenReturn(author);
        when(message.getNonce()).thenReturn(nonce);
        return message;
    }

    private static Message messageWithFooter(long id, User author, String footer) {
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(id);
        when(message.getAuthor()).thenReturn(author);
        when(message.getNonce()).thenReturn(null);
        when(message.getEmbeds()).thenReturn(List.of(new EmbedBuilder()
                .setTitle("Neuer Rekord").setDescription("Ada erreicht etwas Neues.").setFooter(footer).build()));
        return message;
    }
}
