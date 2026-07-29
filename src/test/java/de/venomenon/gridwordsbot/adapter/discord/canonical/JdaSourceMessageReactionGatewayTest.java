package de.venomenon.gridwordsbot.adapter.discord.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.requests.RestAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JdaSourceMessageReactionGatewayTest {

    @Test
    void addsTheAcceptedReactionToARecoveredSourceMessage() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        RestAction<Message> lookup = mock(RestAction.class);
        Message source = mock(Message.class);
        RestAction<Void> reaction = mock(RestAction.class);
        when(jda.getTextChannelById(12L)).thenReturn(channel);
        when(channel.retrieveMessageById(10L)).thenReturn(lookup);
        when(lookup.complete()).thenReturn(source);
        when(source.addReaction(any(Emoji.class))).thenReturn(reaction);
        ArgumentCaptor<Emoji> emoji = ArgumentCaptor.forClass(Emoji.class);

        new JdaSourceMessageReactionGateway(jda).addAcceptedReaction(12L, 10L);

        verify(source).addReaction(emoji.capture());
        verify(reaction).queue(any(), any());
        assertThat(emoji.getValue().getName()).isEqualTo("✅");
    }
}