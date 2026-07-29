package de.venomenon.gridwordsbot.port.out;

/** Adds the success reaction to an original source message after startup recovery. */
public interface SourceMessageReactionGateway {
    void addAcceptedReaction(long channelId, long sourceMessageId);
}
