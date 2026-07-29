package de.venomenon.gridwordsbot.port.out;

/** Adds the success reaction to an original source message after deferred publication succeeds. */
public interface SourceMessageReactionGateway {
    void addAcceptedReaction(long channelId, long sourceMessageId);
}
