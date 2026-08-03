package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.status.DailyStatusView;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Message;
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

class JdaDailyStatusComponentsTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);

    @Test
    void createSetsBothSelectorsWithStableIdsAndUserValues() {
        Fixture fixture = new Fixture();
        fixture.emptyHistory();
        MessageCreateAction create = fixture.createAction(99L);

        fixture.gateway.publishOrEdit(12L, Optional.empty(), DailyStatusView.versionOne(status(1)), true);

        List<ActionRow> rows = capturedRows(create);
        assertThat(rows).hasSize(2);
        assertThat(menu(rows.get(0)).getCustomId()).isEqualTo("daily-result:v1:2026-08-03:g:0");
        assertThat(menu(rows.get(1)).getCustomId()).isEqualTo("daily-result:v1:2026-08-03:q:0");
        assertThat(menu(rows.get(0)).getOptions())
                .extracting(option -> option.getLabel() + ":" + option.getValue())
                .containsExactly("Player 01:user:1");
    }

    @Test
    void editReplacesAllFourPagedSelectors() {
        Fixture fixture = new Fixture();
        Message existing = mock(Message.class);
        RestAction<Message> retrieve = mock(RestAction.class);
        MessageEditAction edit = mock(MessageEditAction.class);
        when(fixture.channel.retrieveMessageById(99L)).thenReturn(retrieve);
        when(retrieve.complete()).thenReturn(existing);
        when(existing.getIdLong()).thenReturn(99L);
        when(existing.editMessageEmbeds(anyCollection())).thenReturn(edit);
        when(edit.setComponents(anyCollection())).thenReturn(edit);
        when(edit.setAllowedMentions(anyCollection())).thenReturn(edit);

        fixture.gateway.publishOrEdit(12L, Optional.of(99L), DailyStatusView.versionOne(status(26)), true);

        List<ActionRow> rows = capturedRows(edit);
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(row -> menu(row).getCustomId()).containsExactly(
                "daily-result:v1:2026-08-03:g:0",
                "daily-result:v1:2026-08-03:g:1",
                "daily-result:v1:2026-08-03:q:0",
                "daily-result:v1:2026-08-03:q:1");
        assertThat(rows).extracting(row -> menu(row).getOptions().size()).containsExactly(25, 1, 25, 1);
    }

    @Test
    void recreateAfterExternalDeletionRestoresTheCompleteSelectors() {
        Fixture fixture = new Fixture();
        RestAction<Message> retrieve = mock(RestAction.class);
        ErrorResponseException missing = mock(ErrorResponseException.class);
        when(missing.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE);
        when(fixture.channel.retrieveMessageById(99L)).thenReturn(retrieve);
        when(retrieve.complete()).thenThrow(missing);
        fixture.emptyHistory();
        MessageCreateAction create = fixture.createAction(101L);

        long replacement = fixture.gateway.publishOrEdit(
                12L, Optional.of(99L), DailyStatusView.versionOne(status(1)), false);

        assertThat(replacement).isEqualTo(101L);
        assertThat(capturedRows(create)).hasSize(2);
    }

    private static DailyStatus status(int playerCount) {
        List<DailyStatus.PlayerLine> players = IntStream.rangeClosed(1, playerCount)
                .mapToObj(id -> new DailyStatus.PlayerLine(
                        id,
                        String.format("Player %02d", id),
                        Optional.empty(),
                        Optional.empty(),
                        new StreakSummary(0, 0, 0, 0, 0, 0, 0)))
                .toList();
        return new DailyStatus(DATE, players, 0, 0);
    }

    private static StringSelectMenu menu(ActionRow row) {
        return (StringSelectMenu) row.getComponents().getFirst();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<ActionRow> capturedRows(MessageCreateAction action) {
        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        verify(action).setComponents(captor.capture());
        return ((Collection<?>) captor.getValue()).stream().map(ActionRow.class::cast).toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<ActionRow> capturedRows(MessageEditAction action) {
        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        verify(action).setComponents(captor.capture());
        return ((Collection<?>) captor.getValue()).stream().map(ActionRow.class::cast).toList();
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

        MessageCreateAction createAction(long messageId) {
            MessageCreateAction create = mock(MessageCreateAction.class);
            Message sent = mock(Message.class);
            when(channel.sendMessageEmbeds(anyCollection())).thenReturn(create);
            when(create.setComponents(anyCollection())).thenReturn(create);
            when(create.setAllowedMentions(anyCollection())).thenReturn(create);
            when(create.complete()).thenReturn(sent);
            when(sent.getIdLong()).thenReturn(messageId);
            return create;
        }

        void emptyHistory() {
            MessageHistory history = mock(MessageHistory.class);
            RestAction<List<Message>> request = mock(RestAction.class);
            when(channel.getHistory()).thenReturn(history);
            when(history.retrievePast(100)).thenReturn(request);
            when(request.complete()).thenReturn(List.of());
        }
    }
}
