package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.out.ReminderCandidateStore;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.Test;

class JdaDailyStatusMessageGatewayTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);

    @Test
    void statusCreateDisablesAllMentions() {
        Fixture fixture = new Fixture();
        fixture.emptyHistory();
        MessageCreateAction create = mock(MessageCreateAction.class);
        Message sent = mock(Message.class);
        when(fixture.channel.sendMessageEmbeds(anyCollection())).thenReturn(create);
        when(create.setAllowedMentions(anyCollection())).thenReturn(create);
        when(create.complete()).thenReturn(sent);
        when(sent.getIdLong()).thenReturn(99L);

        assertThat(fixture.gateway.publishOrEdit(12L, Optional.empty(), status(), true)).isEqualTo(99L);

        verify(create).setAllowedMentions(Collections.emptyList());
    }

    @Test
    void unchangedDeliveredStatusChecksPresenceWithoutEditing() {
        Fixture fixture = new Fixture();
        Message existing = mock(Message.class);
        RestAction<Message> retrieve = mock(RestAction.class);
        when(fixture.channel.retrieveMessageById(99L)).thenReturn(retrieve);
        when(retrieve.complete()).thenReturn(existing);
        when(existing.getIdLong()).thenReturn(99L);

        assertThat(fixture.gateway.publishOrEdit(12L, Optional.of(99L), status(), false)).isEqualTo(99L);

        verify(existing, never()).editMessageEmbeds(anyCollection());
    }

    @Test
    void changedDeliveredStatusEditsStoredMessage() {
        Fixture fixture = new Fixture();
        Message existing = mock(Message.class);
        RestAction<Message> retrieve = mock(RestAction.class);
        MessageEditAction edit = mock(MessageEditAction.class);
        when(fixture.channel.retrieveMessageById(99L)).thenReturn(retrieve);
        when(retrieve.complete()).thenReturn(existing);
        when(existing.getIdLong()).thenReturn(99L);
        when(existing.editMessageEmbeds(anyCollection())).thenReturn(edit);
        when(edit.setAllowedMentions(anyCollection())).thenReturn(edit);

        assertThat(fixture.gateway.publishOrEdit(12L, Optional.of(99L), status(), true)).isEqualTo(99L);

        verify(edit).setAllowedMentions(Collections.emptyList());
    }

    @Test
    void externallyDeletedStoredStatusCreatesReplacement() {
        Fixture fixture = new Fixture();
        RestAction<Message> retrieve = mock(RestAction.class);
        ErrorResponseException missing = mock(ErrorResponseException.class);
        when(missing.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE);
        when(fixture.channel.retrieveMessageById(99L)).thenReturn(retrieve);
        when(retrieve.complete()).thenThrow(missing);
        fixture.emptyHistory();
        MessageCreateAction create = mock(MessageCreateAction.class);
        Message replacement = mock(Message.class);
        when(fixture.channel.sendMessageEmbeds(anyCollection())).thenReturn(create);
        when(create.setAllowedMentions(anyCollection())).thenReturn(create);
        when(create.complete()).thenReturn(replacement);
        when(replacement.getIdLong()).thenReturn(101L);

        assertThat(fixture.gateway.publishOrEdit(12L, Optional.of(99L), status(), false)).isEqualTo(101L);

        verify(create).setAllowedMentions(Collections.emptyList());
    }

    @Test
    void missingStoredStatusIsRecoveredByStableFooterWithoutCreatingDuplicate() {
        Fixture fixture = new Fixture();
        Message matching = fixture.matchingMessage(DailyStatusEmbedRenderer.statusKey(12L, status()), 88L);
        fixture.history(List.of(matching));
        MessageEditAction edit = mock(MessageEditAction.class);
        when(matching.editMessageEmbeds(anyCollection())).thenReturn(edit);
        when(edit.setAllowedMentions(anyCollection())).thenReturn(edit);

        assertThat(fixture.gateway.publishOrEdit(12L, Optional.empty(), status(), true)).isEqualTo(88L);

        verify(fixture.channel, never()).sendMessageEmbeds(anyCollection());
        verify(edit).setAllowedMentions(Collections.emptyList());
    }

    @Test
    void reminderAllowsExactlySelectedUserMentions() {
        Fixture fixture = new Fixture();
        fixture.emptyHistory();
        MessageCreateAction create = mock(MessageCreateAction.class);
        Message sent = mock(Message.class);
        when(fixture.channel.sendMessage(any(String.class))).thenReturn(create);
        when(create.addEmbeds(any(net.dv8tion.jda.api.entities.MessageEmbed.class))).thenReturn(create);
        when(create.setAllowedMentions(anyCollection())).thenReturn(create);
        when(create.mentionUsers(anyCollection())).thenReturn(create);
        when(create.complete()).thenReturn(sent);
        when(sent.getIdLong()).thenReturn(77L);
        List<ReminderCandidateStore.ReminderCandidate> candidates = List.of(
                new ReminderCandidateStore.ReminderCandidate(42L, "@everyone", List.of(GameType.GRIDWORDS)),
                new ReminderCandidateStore.ReminderCandidate(43L, "Role", List.of(GameType.QUADWORDS)));

        assertThat(fixture.gateway.send(12L, DATE, 1, candidates, Set.of(42L, 43L))).isEqualTo(77L);

        verify(create).setAllowedMentions(List.of(Message.MentionType.USER));
        org.mockito.ArgumentCaptor<java.util.Collection<String>> mentions = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(create).mentionUsers(mentions.capture());
        assertThat(mentions.getValue()).containsExactlyInAnyOrder("42", "43");
    }

    @Test
    void interruptedReminderCompletionFindsExistingDeliveryAndDoesNotSendAgain() {
        Fixture fixture = new Fixture();
        Message matching = fixture.matchingMessage(JdaDailyStatusMessageGateway.reminderKey(12L, DATE, 2), 66L);
        fixture.history(List.of(matching));
        List<ReminderCandidateStore.ReminderCandidate> candidates = List.of(
                new ReminderCandidateStore.ReminderCandidate(42L, "Name", List.of(GameType.GRIDWORDS)));

        assertThat(fixture.gateway.send(12L, DATE, 2, candidates, Set.of(42L))).isEqualTo(66L);

        verify(fixture.channel, never()).sendMessage(any(String.class));
    }

    @Test
    void reconciliationKeepsSmallestSnowflakeAndDeletesDuplicateDelivery() {
        Fixture fixture = new Fixture();
        String key = JdaDailyStatusMessageGateway.reminderKey(12L, DATE, 1);
        Message canonical = fixture.matchingMessage(key, 66L);
        Message duplicate = fixture.matchingMessage(key, 77L);
        net.dv8tion.jda.api.requests.restaction.AuditableRestAction<Void> deletion =
                mock(net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class);
        when(duplicate.delete()).thenReturn(deletion);
        fixture.history(List.of(duplicate, canonical));
        List<ReminderCandidateStore.ReminderCandidate> candidates = List.of(
                new ReminderCandidateStore.ReminderCandidate(42L, "Name", List.of(GameType.GRIDWORDS)));

        assertThat(fixture.gateway.send(12L, DATE, 1, candidates, Set.of(42L))).isEqualTo(66L);

        verify(deletion).complete();
        verify(canonical, never()).delete();
    }
    private static DailyStatus status() {
        return new DailyStatus(DATE, List.of(new DailyStatus.PlayerLine(42L, "Player", Optional.empty(),
                Optional.empty(), new StreakSummary(1, 0, 0, 0, 0, 0, 0))), 0, 0);
    }

    private static final class Fixture {
        final JDA jda = mock(JDA.class);
        final TextChannel channel = mock(TextChannel.class);
        final SelfUser self = mock(SelfUser.class);
        final JdaDailyStatusMessageGateway gateway = new JdaDailyStatusMessageGateway(jda);

        Fixture() {
            when(jda.getTextChannelById(12L)).thenReturn(channel);
            when(jda.getSelfUser()).thenReturn(self);
        }

        void emptyHistory() { history(List.of()); }

        void history(List<Message> messages) {
            MessageHistory history = mock(MessageHistory.class);
            RestAction<List<Message>> request = mock(RestAction.class);
            when(channel.getHistory()).thenReturn(history);
            when(history.retrievePast(100)).thenReturn(request);
            when(request.complete()).thenReturn(messages);
        }

        Message matchingMessage(String key, long id) {
            Message message = mock(Message.class);
            when(message.getAuthor()).thenReturn(self);
            when(message.getIdLong()).thenReturn(id);
            when(message.getEmbeds()).thenReturn(List.of(new EmbedBuilder().setFooter(key).build()));
            return message;
        }
    }
}
