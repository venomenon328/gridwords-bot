package de.venomenon.gridwordsbot.port.out;
import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage; import java.util.OptionalLong;
/** Discord-independent boundary for canonical result publication. */
public interface CanonicalMessageGateway { long create(long channelId,CanonicalResultMessage message); void edit(long channelId,long messageId,CanonicalResultMessage message); OptionalLong findByPublicationKey(long channelId,String publicationKey); class UnknownMessageException extends RuntimeException { public UnknownMessageException(){super("canonical message is missing");} } }
