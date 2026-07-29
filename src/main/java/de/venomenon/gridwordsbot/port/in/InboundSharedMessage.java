package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable, transport-neutral snapshot of one inbound Discord message. */
public record InboundSharedMessage(
        long guildId,
        long channelId,
        long messageId,
        long authorId,
        String authorDisplayName,
        String content,
        List<AttachmentMetadata> attachments,
        Instant receivedAt) {

    public InboundSharedMessage {
        if (guildId <= 0 || channelId <= 0 || messageId <= 0 || authorId <= 0) {
            throw new IllegalArgumentException("Discord IDs must be positive");
        }
        Objects.requireNonNull(authorDisplayName, "authorDisplayName");
        Objects.requireNonNull(content, "content");
        attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
