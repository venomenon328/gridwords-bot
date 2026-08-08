package de.venomenon.gridwordsbot.adapter.discord.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.application.achievement.RenderedAchievementAnnouncement;
import java.util.Collection;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JdaAchievementAnnouncementMessageGatewayTest {
    @Test
    void createsMentionSafeMessageWithStableNonceAndNoVisibleRecoveryMetadata() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        MessageCreateAction create = mock(MessageCreateAction.class);
        Message sent = mock(Message.class);
        when(jda.getTextChannelById(2L)).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(Collection.class))).thenReturn(create);
        when(create.setNonce(any())).thenReturn(create);
        when(create.setAllowedMentions(any())).thenReturn(create);
        when(create.complete()).thenReturn(sent);
        when(sent.getIdLong()).thenReturn(99L);
        RenderedAchievementAnnouncement announcement = announcement();

        assertThat(new JdaAchievementAnnouncementMessageGateway(jda).create(2L, announcement)).isEqualTo(99L);

        ArgumentCaptor<Collection<MessageEmbed>> embeds = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<String> nonce = ArgumentCaptor.forClass(String.class);
        verify(channel).sendMessageEmbeds(embeds.capture());
        verify(create).setNonce(nonce.capture());
        verify(create).setAllowedMentions(List.of());
        assertThat(embeds.getValue()).singleElement().satisfies(embed ->
                assertThat(embed.getDescription())
                        .isEqualTo(announcement.embeds().getFirst().description())
                        .doesNotContain("gridwords.invalid", "achievement-announcement"));
        assertThat(nonce.getValue()).startsWith("aa:").doesNotContain("achievement-announcement")
                .hasSizeLessThanOrEqualTo(Message.MAX_NONCE_LENGTH);
    }

    @Test
    void discoversOnlyOwnMessageByStableNonceAndUsesLowestSnowflakeOrder() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        MessageHistory history = mock(MessageHistory.class);
        RestAction<List<Message>> request = mock(RestAction.class);
        SelfUser self = mock(SelfUser.class);
        RenderedAchievementAnnouncement announcement = announcement();
        Message late = mock(Message.class);
        Message early = mock(Message.class);
        when(late.getAuthor()).thenReturn(self); when(early.getAuthor()).thenReturn(self);
        when(late.getNonce()).thenReturn(JdaAchievementAnnouncementMessageGateway.nonce(announcement.publicationKey()));
        when(early.getNonce()).thenReturn(JdaAchievementAnnouncementMessageGateway.nonce(announcement.publicationKey()));
        when(late.getIdLong()).thenReturn(90L); when(early.getIdLong()).thenReturn(30L);
        when(jda.getTextChannelById(2L)).thenReturn(channel); when(jda.getSelfUser()).thenReturn(self);
        when(channel.getHistory()).thenReturn(history); when(history.retrievePast(100)).thenReturn(request);
        when(request.complete()).thenReturn(List.of(late, early));

        assertThat(new JdaAchievementAnnouncementMessageGateway(jda)
                .discoverCreatedMessages(2L, announcement.publicationKey(), announcement)).containsExactly(30L, 90L);
    }

    @Test
    void identicalVisibleContentWithoutPublicationIdentityIsNotReused() {
        JDA jda = mock(JDA.class);
        TextChannel channel = mock(TextChannel.class);
        MessageHistory history = mock(MessageHistory.class);
        RestAction<List<Message>> request = mock(RestAction.class);
        SelfUser self = mock(SelfUser.class);
        RenderedAchievementAnnouncement announcement = announcement();
        Message unrelated = mock(Message.class);
        MessageEmbed embed = mock(MessageEmbed.class);
        when(unrelated.getAuthor()).thenReturn(self);
        when(unrelated.getNonce()).thenReturn(null);
        when(unrelated.getEmbeds()).thenReturn(List.of(embed));
        when(embed.getTitle()).thenReturn(announcement.embeds().getFirst().title());
        when(embed.getDescription()).thenReturn(announcement.embeds().getFirst().description());
        when(jda.getTextChannelById(2L)).thenReturn(channel); when(jda.getSelfUser()).thenReturn(self);
        when(channel.getHistory()).thenReturn(history); when(history.retrievePast(100)).thenReturn(request);
        when(request.complete()).thenReturn(List.of(unrelated));

        assertThat(new JdaAchievementAnnouncementMessageGateway(jda)
                .discoverCreatedMessages(2L, announcement.publicationKey(), announcement)).isEmpty();
    }

    private static RenderedAchievementAnnouncement announcement() {
        return new RenderedAchievementAnnouncement("achievement-announcement:" + "a".repeat(64), "b".repeat(64),
                List.of(new RenderedAchievementAnnouncement.Embed("🏅 Achievements", "Ada hat etwas freigeschaltet.")));
    }
}
