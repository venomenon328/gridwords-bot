package de.venomenon.gridwordsbot.domain.parsing;

import java.util.Locale;

/** Transport-neutral metadata used to decide whether an attachment plausibly is an image. */
public record AttachmentMetadata(String filename, String contentType, long size) {

    public AttachmentMetadata {
        filename = filename == null ? "" : filename;
        contentType = contentType == null ? "" : contentType;
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }

    public boolean isPlausibleImage() {
        if (!contentType.isBlank()) {
            return contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        }
        String lowerCaseFilename = filename.toLowerCase(Locale.ROOT);
        return lowerCaseFilename.endsWith(".png")
                || lowerCaseFilename.endsWith(".jpg")
                || lowerCaseFilename.endsWith(".jpeg")
                || lowerCaseFilename.endsWith(".webp");
    }
}
