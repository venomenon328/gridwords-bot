package de.venomenon.gridwordsbot.domain.parsing;

/**
 * Opaque, transport-neutral locator for one concrete attachment on its source message.
 *
 * <p>The values deliberately contain no CDN URL or JDA type. A transport adapter resolves the reference.
 */
public record AttachmentReference(long channelId, long messageId, long attachmentId) {

    public AttachmentReference {
        if (channelId <= 0 || messageId <= 0 || attachmentId <= 0) {
            throw new IllegalArgumentException("attachment reference IDs must be positive");
        }
    }
}
