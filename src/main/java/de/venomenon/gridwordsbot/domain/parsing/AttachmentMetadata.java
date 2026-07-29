package de.venomenon.gridwordsbot.domain.parsing;

import java.util.Locale;

/** Immutable transport-neutral attachment metadata and an adapter-owned retrieval reference. */
public record AttachmentMetadata(String filename, String contentType, long size, String reference) {
    public AttachmentMetadata(String filename, String contentType, long size) { this(filename, contentType, size, ""); }
    public AttachmentMetadata {
        filename = filename == null ? "" : filename; contentType = contentType == null ? "" : contentType; reference = reference == null ? "" : reference;
        if (size < 0) throw new IllegalArgumentException("size must not be negative");
    }
    public boolean isPlausibleImage() {
        if (!contentType.isBlank()) return contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        String name = filename.toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
    }
}