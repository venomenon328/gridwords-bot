package de.venomenon.gridwordsbot.adapter.discord.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.reporting.RenderedReportField;
import de.venomenon.gridwordsbot.domain.reporting.RenderedReportPage;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JdaPeriodicReportMessageGatewayTest {

    @Test
    void createsAndEditsTheRenderedPageWithoutMentionsOrAdditionalContent() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        MessageCreateAction create = mock(MessageCreateAction.class);
        Message sent = mock(Message.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(create);
        when(create.setAllowedMentions(any())).thenReturn(create);
        when(create.complete()).thenReturn(sent);
        when(sent.getIdLong()).thenReturn(99L);
        JdaPeriodicReportMessageGateway gateway = new JdaPeriodicReportMessageGateway(jda);
        ArgumentCaptor<MessageEmbed> createdEmbed = ArgumentCaptor.forClass(MessageEmbed.class);

        assertThat(gateway.create(12L, page())).isEqualTo(99L);

        verify(channel).sendMessageEmbeds(createdEmbed.capture());
        assertThat(createdEmbed.getValue().getTitle()).isEqualTo("Wochenbericht");
        assertThat(createdEmbed.getValue().getFields())
                .extracting(MessageEmbed.Field::getName, MessageEmbed.Field::getValue, MessageEmbed.Field::isInline)
                .containsExactly(tuple("Ada", "Teilnahme: 7", false), tuple("Gemeinsam", "Komplett: 5", false));
        assertThat(createdEmbed.getValue().getFooter().getText()).isEqualTo("Seite 2/3");
        assertThat(createdEmbed.getValue().getDescription()).isNull();
        assertThat(createdEmbed.getValue().getFooter().getText()).doesNotContain("delivery", "report-");
        verify(create).setAllowedMentions(Collections.emptyList());

        RestAction<Message> lookup = mock(RestAction.class);
        Message existing = mock(Message.class);
        MessageEditAction edit = mock(MessageEditAction.class);
        when(channel.retrieveMessageById(99L)).thenReturn(lookup);
        when(lookup.complete()).thenReturn(existing);
        when(existing.editMessageEmbeds(any(MessageEmbed.class))).thenReturn(edit);
        when(edit.setAllowedMentions(any())).thenReturn(edit);

        gateway.edit(12L, 99L, page());

        verify(edit).setAllowedMentions(Collections.emptyList());
    }

    @Test
    void loadsOwnMessageWithVisiblePageOrderAndRejectsMessagesFromAnotherAuthor() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        RestAction<Message> lookup = mock(RestAction.class);
        SelfUser self = mock(SelfUser.class);
        Message message = reportMessage(99L, self, renderedPage());
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(jda.getSelfUser()).thenReturn(self);
        when(channel.retrieveMessageById(99L)).thenReturn(lookup);
        when(lookup.complete()).thenReturn(message);

        assertThat(new JdaPeriodicReportMessageGateway(jda).load(12L, 99L))
                .isEqualTo(new PeriodicReportMessageGateway.PublishedReportPage(99L, page()));

        User other = mock(User.class);
        when(message.getAuthor()).thenReturn(other);
        assertThatThrownBy(() -> new JdaPeriodicReportMessageGateway(jda).load(12L, 99L))
                .isInstanceOf(PeriodicReportMessageGateway.PermanentMessageException.class);
    }

    @Test
    void findsAllExactBotMatchesAcrossHistoryPagesInAscendingMessageIdOrder() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        MessageHistory history = mock(MessageHistory.class);
        RestAction<List<Message>> request = mock(RestAction.class);
        SelfUser self = mock(SelfUser.class);
        Message nonMatch = reportMessage(10L, self, new RenderedReportPage("Anderer Bericht", List.of(), Optional.empty()));
        Message firstMatch = reportMessage(70L, self, renderedPage());
        Message secondMatch = reportMessage(30L, self, renderedPage());
        Message otherAuthorMatch = reportMessage(20L, mock(User.class), renderedPage());
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(jda.getSelfUser()).thenReturn(self);
        when(channel.getHistory()).thenReturn(history);
        when(history.retrievePast(100)).thenReturn(request);
        when(request.complete()).thenReturn(Collections.nCopies(100, nonMatch), List.of(firstMatch, secondMatch, otherAuthorMatch));

        assertThat(new JdaPeriodicReportMessageGateway(jda).findExactMatches(12L, page()))
                .containsExactly(
                        new PeriodicReportMessageGateway.PublishedReportPage(30L, page()),
                        new PeriodicReportMessageGateway.PublishedReportPage(70L, page()));

        verify(history, times(2)).retrievePast(100);
    }

    @Test
    void deletesAndMapsKnownMissingMessages() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        AuditableRestAction<Void> delete = mock(AuditableRestAction.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.deleteMessageById(99L)).thenReturn(delete);
        new JdaPeriodicReportMessageGateway(jda).delete(12L, 99L);
        verify(delete).complete();

        assertThat(JdaPeriodicReportMessageGateway.knownMessageFailure(
                "periodic report deletion failed", ErrorResponse.UNKNOWN_MESSAGE, new RuntimeException("missing")))
                .isInstanceOf(PeriodicReportMessageGateway.MissingMessageException.class);
    }

    @Test
    void classifiesUnknownCreationOutcomesRetryableAndPermanentJdaFailures() {
        RuntimeException jdaFailure = new RuntimeException("JDA failure");

        assertThat(JdaPeriodicReportMessageGateway.creationFailure(ErrorResponse.UNKNOWN_MESSAGE, jdaFailure))
                .isInstanceOf(PeriodicReportMessageGateway.UnknownMessageException.class);
        assertThat(JdaPeriodicReportMessageGateway.classified(
                "periodic report creation failed", ErrorResponse.SERVER_ERROR, jdaFailure))
                .isInstanceOf(PeriodicReportMessageGateway.RetryableMessageException.class);
        assertThat(JdaPeriodicReportMessageGateway.classified(
                "periodic report creation failed", ErrorResponse.MISSING_ACCESS, jdaFailure))
                .isInstanceOf(PeriodicReportMessageGateway.PermanentMessageException.class);
    }
    private static PeriodicReportMessageGateway.ReportPage page() {
        return new PeriodicReportMessageGateway.ReportPage(1, renderedPage());
    }

    private static RenderedReportPage renderedPage() {
        return new RenderedReportPage("Wochenbericht", List.of(
                new RenderedReportField("Ada", "Teilnahme: 7"),
                new RenderedReportField("Gemeinsam", "Komplett: 5")), Optional.of("Seite 2/3"));
    }

    private static Message reportMessage(long id, User author, RenderedReportPage page) {
        Message message = mock(Message.class);
        EmbedBuilder builder = new EmbedBuilder().setTitle(page.title());
        page.fields().forEach(field -> builder.addField(field.name(), field.value(), false));
        page.footer().ifPresent(builder::setFooter);
        when(message.getIdLong()).thenReturn(id);
        when(message.getAuthor()).thenReturn(author);
        when(message.getEmbeds()).thenReturn(List.of(builder.build()));
        return message;
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
