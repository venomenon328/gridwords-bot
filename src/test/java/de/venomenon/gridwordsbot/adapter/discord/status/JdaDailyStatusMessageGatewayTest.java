package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
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
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    void missingStoredStatusIsRecoveredByVisibleTitleWithoutTechnicalFooter() {
        Fixture fixture = new Fixture();
        Message matching = fixture.statusMessage(DailyStatusEmbedRenderer.statusTitle(status()), 88L);
        fixture.history(List.of(matching));
        MessageEditAction edit = mock(MessageEditAction.class);
        when(matching.editMessageEmbeds(anyCollection())).thenReturn(edit);
        when(edit.setAllowedMentions(anyCollection())).thenReturn(edit);

        assertThat(fixture.gateway.publishOrEdit(12L, Optional.empty(), status(), true)).isEqualTo(88L);

        verify(fixture.channel, never()).sendMessageEmbeds(anyCollection());
        verify(edit).setAllowedMentions(Collections.emptyList());
        assertThat(matching.getEmbeds().getFirst().getFooter()).isNull();
    }

    @Test
    void reminderIsPlainTextGroupsGamesAndMentionsOnlyOptedInPlayers() {
        Fixture fixture = new Fixture();
        fixture.emptyHistory();
        MessageCreateAction create = mock(MessageCreateAction.class);
        Message sent = mock(Message.class);
        when(fixture.channel.sendMessage(anyString())).thenReturn(create);
        when(create.setAllowedMentions(anyCollection())).thenReturn(create);
        when(create.mentionUsers(anyCollection())).thenReturn(create);
        when(create.complete()).thenReturn(sent);
        when(sent.getIdLong()).thenReturn(77L);
        List<ReminderCandidateStore.ReminderCandidate> candidates = List.of(
                new ReminderCandidateStore.ReminderCandidate(
                        42L, "Mentioned", List.of(GameType.GRIDWORDS), true),
                new ReminderCandidateStore.ReminderCandidate(
                        43L, "@everyone_Role", List.of(GameType.GRIDWORDS, GameType.QUADWORDS), false));

        assertThat(fixture.gateway.send(12L, DATE, 1, candidates, Set.of(42L))).isEqualTo(77L);
        verify(sent).suppressEmbeds(true);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(fixture.channel).sendMessage(content.capture());
        assertThat(content.getValue())
                .contains("**Denkt bitte noch an eure Wortspiele:**")
                .contains("**[GridWords](https://gridgames.app/gridwords#gridwords-reminder:12:2026-07-30:1)**:")
                .contains("**[QuadWords](https://gridgames.app/quadwords#gridwords-reminder:12:2026-07-30:1)**:")
                .contains("<@42>", "@\u200Beveryone\\_Role")
                .doesNotContain("Erinnerung ·", "gridwords-reminder:12:2026-07-30:1\n");
        verify(create, never()).addEmbeds(any(MessageEmbed.class));
        verify(create).setAllowedMentions(List.of(Message.MentionType.USER));
        ArgumentCaptor<java.util.Collection<String>> mentions = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(create).mentionUsers(mentions.capture());
        assertThat(mentions.getValue()).containsExactly("42");
    }

    @Test
    void reminderWithOnlyOptOutPlayersDisplaysNamesAndDisablesAllMentions() {
        Fixture fixture = new Fixture();
        fixture.emptyHistory();
        MessageCreateAction create = mock(MessageCreateAction.class);
        Message sent = mock(Message.class);
        when(fixture.channel.sendMessage(anyString())).thenReturn(create);
        when(create.setAllowedMentions(anyCollection())).thenReturn(create);
        when(create.complete()).thenReturn(sent);
        when(sent.getIdLong()).thenReturn(78L);
        List<ReminderCandidateStore.ReminderCandidate> candidates = List.of(
                new ReminderCandidateStore.ReminderCandidate(
                        43L, "Georgia", List.of(GameType.QUADWORDS), false));

        assertThat(fixture.gateway.send(12L, DATE, 2, candidates, Set.of())).isEqualTo(78L);

        verify(create).setAllowedMentions(Collections.emptyList());
        verify(create, never()).mentionUsers(anyCollection());
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(fixture.channel).sendMessage(content.capture());
        assertThat(content.getValue()).contains("QuadWords", "Georgia").doesNotContain("<@43>");
    }

    @Test
    void interruptedReminderCompletionFindsExistingPlainTextDeliveryAndDoesNotSendAgain() {
        Fixture fixture = new Fixture();
        Message matching = fixture.reminderMessage(JdaDailyStatusMessageGateway.reminderKey(12L, DATE, 2), 66L);
        fixture.history(List.of(matching));
        List<ReminderCandidateStore.ReminderCandidate> candidates = List.of(
                new ReminderCandidateStore.ReminderCandidate(42L, "Name", List.of(GameType.GRIDWORDS), true));

        assertThat(fixture.gateway.send(12L, DATE, 2, candidates, Set.of(42L))).isEqualTo(66L);

        verify(fixture.channel, never()).sendMessage(anyString());
    }

    @Test
    void reconciliationKeepsSmallestSnowflakeAndDeletesDuplicatePlainTextDelivery() {
        Fixture fixture = new Fixture();
        String key = JdaDailyStatusMessageGateway.reminderKey(12L, DATE, 1);
        Message canonical = fixture.reminderMessage(key, 66L);
        Message duplicate = fixture.reminderMessage(key, 77L);
        net.dv8tion.jda.api.requests.restaction.AuditableRestAction<Void> deletion =
                mock(net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class);
        when(duplicate.delete()).thenReturn(deletion);
        fixture.history(List.of(duplicate, canonical));
        List<ReminderCandidateStore.ReminderCandidate> candidates = List.of(
                new ReminderCandidateStore.ReminderCandidate(42L, "Name", List.of(GameType.GRIDWORDS), true));

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

        void emptyHistory() {
            history(List.of());
        }

        void history(List<Message> messages) {
            MessageHistory history = mock(MessageHistory.class);
            RestAction<List<Message>> request = mock(RestAction.class);
            when(channel.getHistory()).thenReturn(history);
            when(history.retrievePast(100)).thenReturn(request);
            when(request.complete()).thenReturn(messages);
        }

        Message statusMessage(String title, long id) {
            Message message = baseMessage(id);
            when(message.getEmbeds()).thenReturn(List.of(new EmbedBuilder().setTitle(title).build()));
            when(message.getContentRaw()).thenReturn("");
            return message;
        }

        Message reminderMessage(String key, long id) {
            Message message = baseMessage(id);
            when(message.getEmbeds()).thenReturn(List.of());
            when(message.getContentRaw()).thenReturn(
                    "[GridWords](https://gridgames.app/gridwords#" + key + "): Name");
            return message;
        }

        private Message baseMessage(long id) {
            Message message = mock(Message.class);
            when(message.getAuthor()).thenReturn(self);
            when(message.getIdLong()).thenReturn(id);
            return message;
        }
    }
}
