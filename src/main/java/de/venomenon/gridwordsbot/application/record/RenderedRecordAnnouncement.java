package de.venomenon.gridwordsbot.application.record;

import java.util.List;
import java.util.Objects;

/** Deterministic page group for one persisted logical record announcement. */
public record RenderedRecordAnnouncement(String publicationKey, String contentFingerprint,
                                        List<RenderedRecordAnnouncementPage> pages) {
    public RenderedRecordAnnouncement {
        Objects.requireNonNull(publicationKey, "publicationKey");
        Objects.requireNonNull(contentFingerprint, "contentFingerprint");
        pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
        if (pages.isEmpty()) throw new IllegalArgumentException("pages must not be empty");
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).position() != i) throw new IllegalArgumentException("page positions must be contiguous");
        }
    }
}
