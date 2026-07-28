package de.venomenon.gridwordsbot.domain.parsing;

import java.util.List;
import java.util.Objects;

/** The small, immutable parser input copied at the Discord transport boundary later on. */
public record ShareParseInput(String content, List<AttachmentMetadata> attachments) {

    public ShareParseInput {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(attachments, "attachments must not be null");
        attachments = List.copyOf(attachments);
    }
}
