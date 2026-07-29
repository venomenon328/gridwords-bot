package de.venomenon.gridwordsbot.adapter.discord.canonical;

import de.venomenon.gridwordsbot.port.out.SourceMessageReactionGateway;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Adds the normal acceptance reaction when startup recovery completes a persisted submission. */
public final class JdaSourceMessageReactionGateway implements SourceMessageReactionGateway {

    private static final Logger log = LoggerFactory.getLogger(JdaSourceMessageReactionGateway.class);

    private final JDA jda;

    public JdaSourceMessageReactionGateway(JDA jda) {
        this.jda = jda;
    }

    @Override
    public void addAcceptedReaction(long channelId, long sourceMessageId) {
        var channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }
        try {
            channel.retrieveMessageById(sourceMessageId)
                    .complete()
                    .addReaction(Emoji.fromUnicode("\u2705"))
                    .queue(
                            ignored -> { },
                            failure -> log.warn(
                                    "Recovered acceptance reaction failed: channelId={}, messageId={}",
                                    channelId,
                                    sourceMessageId,
                                    failure));
        } catch (RuntimeException exception) {
            log.warn(
                    "Recovered acceptance reaction lookup failed: channelId={}, messageId={}",
                    channelId,
                    sourceMessageId,
                    exception);
        }
    }
}