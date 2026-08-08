package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.application.achievement.RenderedAchievementAnnouncement;
import java.util.List;

/** Discord-independent boundary for one logical Achievement announcement message. */
public interface AchievementAnnouncementMessageGateway {
    long create(long channelId, RenderedAchievementAnnouncement announcement);
    boolean exists(long channelId, long messageId);
    List<Long> discoverCreatedMessages(long channelId, String publicationKey, RenderedAchievementAnnouncement expected);
    void delete(long channelId, long messageId);

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
