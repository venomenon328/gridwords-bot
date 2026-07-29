package de.venomenon.gridwordsbot.adapter.discord.inbound;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JdaDiscordReactionGateway implements DiscordReactionGateway {

    private static final Logger log = LoggerFactory.getLogger(JdaDiscordReactionGateway.class);

    @Override
    public void addReaction(Message message, String emoji) {
        message.addReaction(Emoji.fromUnicode(emoji)).queue(
                ignored -> { },
                failure -> log.warn(
                        "Discord reaction delivery failed: guildId={}, channelId={}, messageId={}, reaction={}",
                        message.getGuild().getIdLong(), message.getChannelIdLong(), message.getIdLong(), emoji, failure));
    }
}
