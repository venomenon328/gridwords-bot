package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import java.util.List;
import java.util.OptionalLong;

/** Discord-independent boundary for canonical result publication. */
public interface CanonicalMessageGateway {

    long create(long channelId, CanonicalResultMessage message);

    void edit(long channelId, long messageId, CanonicalResultMessage message);

    OptionalLong findByPublicationKey(long channelId, String publicationKey);

    /** Returns every bot message carrying the stable publication key, so recovery can remove duplicates. */
    default List<Long> findAllByPublicationKey(long channelId, String publicationKey) {
        OptionalLong found = findByPublicationKey(channelId, publicationKey);
        return found.isPresent() ? List.of(found.getAsLong()) : List.of();
    }

    /** Removes a duplicate canonical bot message. The application never uses this for source messages. */
    void delete(long channelId, long messageId);

    class UnknownMessageException extends RuntimeException {
        public UnknownMessageException() {
            super("canonical message is missing");
        }
    }
}