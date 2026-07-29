package de.venomenon.gridwordsbot.adapter.discord.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.Test;

class JdaCanonicalMessageGatewayTest {

    @Test
    void createAndEditDisableAllAllowedMentions() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        MessageCreateAction create = mock(MessageCreateAction.class);
        Message sent = mock(Message.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(create);
        when(create.setAllowedMentions(any())).thenReturn(create);
        when(create.complete()).thenReturn(sent);
        when(sent.getIdLong()).thenReturn(99L);
        JdaCanonicalMessageGateway gateway = new JdaCanonicalMessageGateway(jda);

        assertThat(gateway.create(12L, message())).isEqualTo(99L);

        verify(create).setAllowedMentions(Collections.emptyList());

        RestAction<Message> lookup = mock(RestAction.class);
        Message original = mock(Message.class);
        MessageEditAction edit = mock(MessageEditAction.class);
        when(channel.retrieveMessageById(99L)).thenReturn(lookup);
        when(lookup.complete()).thenReturn(original);
        when(original.editMessageEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(edit);
        when(edit.setAllowedMentions(any())).thenReturn(edit);
        gateway.edit(12L, 99L, message());

        verify(edit).setAllowedMentions(Collections.emptyList());
    }

    @Test
    void mapsOnlyUnknownMessageErrorsToThePortException() {
        ErrorResponseException unknown = mock(ErrorResponseException.class);
        ErrorResponseException other = mock(ErrorResponseException.class);
        when(unknown.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE);
        when(other.getErrorResponse()).thenReturn(ErrorResponse.MISSING_ACCESS);

        assertThat(JdaCanonicalMessageGateway.isUnknownMessage(unknown)).isTrue();
        assertThat(JdaCanonicalMessageGateway.isUnknownMessage(other)).isFalse();

        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        RestAction<Message> lookup = mock(RestAction.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.retrieveMessageById(99L)).thenReturn(lookup);
        when(lookup.complete()).thenThrow(unknown);

        assertThatThrownBy(() -> new JdaCanonicalMessageGateway(jda).edit(12L, 99L, message()))
                .isInstanceOf(CanonicalMessageGateway.UnknownMessageException.class);
    }

    @Test
    void searchesPastTheFirstHundredMessagesForTheHiddenPublicationKey() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        MessageHistory history = mock(MessageHistory.class);
        RestAction<List<Message>> pageRequest = mock(RestAction.class);
        SelfUser self = mock(SelfUser.class);
        Message otherMessage = mock(Message.class);
        Message matchingMessage = mock(Message.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(jda.getSelfUser()).thenReturn(self);
        when(channel.getHistory()).thenReturn(history);
        when(history.retrievePast(100)).thenReturn(pageRequest);
        when(pageRequest.complete()).thenReturn(Collections.nCopies(100, otherMessage), List.of(matchingMessage));
        when(otherMessage.getAuthor()).thenReturn(mock(User.class));
        when(matchingMessage.getAuthor()).thenReturn(self);
        when(matchingMessage.getEmbeds()).thenReturn(List.of(new EmbedBuilder()
                .setFooter(DiscordPublicationKey.encode("gridwords-result-20"))
                .build()));
        when(matchingMessage.getIdLong()).thenReturn(99L);

        assertThat(new JdaCanonicalMessageGateway(jda).findByPublicationKey(12L, "gridwords-result-20"))
                .hasValue(99L);

        verify(history, times(2)).retrievePast(100);
    }

    private static CanonicalResultMessage message() {
        return new CanonicalResultMessage(
                "Tobias",
                GameType.GRIDWORDS,
                LocalDate.of(2026, 7, 29),
                new ShareOutcome.Solved(3, 6),
                Duration.ofSeconds(85),
                new NormalizedBoard(List.of("\u2B1C\u2B1C\u2B1C\u2B1C\u2B1C")),
                new StreakSummary(1, 1, 1, 0, 1, 0, 0),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                "gridwords-result-20");
    }
}
