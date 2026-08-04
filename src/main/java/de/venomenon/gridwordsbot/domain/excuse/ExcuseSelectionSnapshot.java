package de.venomenon.gridwordsbot.domain.excuse;

import java.time.Instant;
import java.util.Objects;

/** Immutable metadata and exact rendered text retained for a selected option. */
public record ExcuseSelectionSnapshot(
        ExcuseRound round,
        int position,
        String templateId,
        ExcuseStyle style,
        ExcuseTopic topic,
        String renderedText,
        Instant selectedAt) {

    public ExcuseSelectionSnapshot {
        Objects.requireNonNull(round, "round");
        if (position < 1 || position > 3) {
            throw new IllegalArgumentException("position must be between 1 and 3");
        }
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId must not be blank");
        }
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(topic, "topic");
        if (renderedText == null || renderedText.isBlank()) {
            throw new IllegalArgumentException("renderedText must not be blank");
        }
        Objects.requireNonNull(selectedAt, "selectedAt");
    }
}
