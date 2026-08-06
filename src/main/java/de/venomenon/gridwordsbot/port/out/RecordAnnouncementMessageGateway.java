package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.application.record.RenderedRecordAnnouncementPage;
import java.util.List;

/** Discord-independent outbound boundary for ordered record-announcement pages. */
public interface RecordAnnouncementMessageGateway {
    long create(long channelId, RenderedRecordAnnouncementPage page);
    void edit(long channelId, long messageId, RenderedRecordAnnouncementPage page);
    void delete(long channelId, long messageId);
    List<PublishedPage> findByPublicationKey(long channelId, String publicationKey);

    record PublishedPage(long messageId, int position) {
        public PublishedPage {
            if (messageId <= 0 || position < 0) throw new IllegalArgumentException("invalid published page");
        }
    }

    sealed class MessageGatewayException extends RuntimeException permits RetryableMessageException,
            PermanentMessageException, MissingMessageException {
        MessageGatewayException(String message, Throwable cause) { super(message, cause); }
    }
    final class RetryableMessageException extends MessageGatewayException {
        public RetryableMessageException(String message, Throwable cause) { super(message, cause); }
    }
    final class PermanentMessageException extends MessageGatewayException {
        public PermanentMessageException(String message, Throwable cause) { super(message, cause); }
    }
    final class MissingMessageException extends MessageGatewayException {
        public MissingMessageException(String message) { super(message, null); }
    }
}
