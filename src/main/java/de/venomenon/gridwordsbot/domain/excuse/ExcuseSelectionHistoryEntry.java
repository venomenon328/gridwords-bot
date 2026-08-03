package de.venomenon.gridwordsbot.domain.excuse;

import java.time.Instant;
import java.util.Objects;

/** A still-valid selected template, used for the cross-game repetition guard. */
public record ExcuseSelectionHistoryEntry(String templateId, ExcuseTopic topic, Instant selectedAt) {

    public ExcuseSelectionHistoryEntry {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId must not be blank");
        }
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(selectedAt, "selectedAt");
    }
}
