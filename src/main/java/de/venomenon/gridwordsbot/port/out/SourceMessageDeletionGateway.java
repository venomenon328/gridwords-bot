package de.venomenon.gridwordsbot.port.out;

/** Deletes an exact user source message after canonical publication has been durably confirmed. */
public interface SourceMessageDeletionGateway {

    DeletionResult deleteSourceMessage(long channelId, long sourceMessageId);

    enum DeletionResult {
        DELETED,
        ALREADY_MISSING,
        RETRYABLE_FAILURE,
        PERMANENT_FAILURE
    }
}
