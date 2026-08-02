package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalLong;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.junit.jupiter.api.Test;

class JdaDailyStatusMessageRetirementTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);

    @Test
    void deletesAmbiguousReminderFoundOnlyByStableDeliveryKey() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        SelfUser self = mock(SelfUser.class);
        MessageHistory history = mock(MessageHistory.class);
        RestAction<List<Message>> historyRequest = mock(RestAction.class);
        Message reminder = mock(Message.class);
        AuditableRestAction<Void> deletion = mock(AuditableRestAction.class);

        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(jda.getSelfUser()).thenReturn(self);
        when(channel.getHistory()).thenReturn(history);
        when(history.retrievePast(100)).thenReturn(historyRequest);
        when(historyRequest.complete()).thenReturn(List.of(reminder));
        when(reminder.getAuthor()).thenReturn(self);
        when(reminder.getIdLong()).thenReturn(66L);
        when(reminder.getContentRaw()).thenReturn(
                "**[GridWords](https://gridgames.app/gridwords#"
                        + JdaDailyStatusMessageGateway.reminderKey(12L, DATE, 1)
                        + ")**: Name");
        when(channel.deleteMessageById(66L)).thenReturn(deletion);

        new JdaDailyStatusMessageGateway(jda).delete(12L, DATE, 1, OptionalLong.empty());

        verify(deletion).complete();
    }

    @Test
    void deletesPersistedIdAndEveryKeyMatchedDuplicate() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        SelfUser self = mock(SelfUser.class);
        MessageHistory history = mock(MessageHistory.class);
        RestAction<List<Message>> historyRequest = mock(RestAction.class);
        Message duplicate = mock(Message.class);
        AuditableRestAction<Void> persistedDeletion = mock(AuditableRestAction.class);
        AuditableRestAction<Void> duplicateDeletion = mock(AuditableRestAction.class);

        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(jda.getSelfUser()).thenReturn(self);
        when(channel.getHistory()).thenReturn(history);
        when(history.retrievePast(100)).thenReturn(historyRequest);
        when(historyRequest.complete()).thenReturn(List.of(duplicate));
        when(duplicate.getAuthor()).thenReturn(self);
        when(duplicate.getIdLong()).thenReturn(67L);
        when(duplicate.getContentRaw()).thenReturn(
                "**[GridWords](https://gridgames.app/gridwords#"
                        + JdaDailyStatusMessageGateway.reminderKey(12L, DATE, 1)
                        + ")**: Name");
        when(channel.deleteMessageById(66L)).thenReturn(persistedDeletion);
        when(channel.deleteMessageById(67L)).thenReturn(duplicateDeletion);

        new JdaDailyStatusMessageGateway(jda).delete(12L, DATE, 1, OptionalLong.of(66L));

        verify(persistedDeletion).complete();
        verify(duplicateDeletion).complete();
    }
}
