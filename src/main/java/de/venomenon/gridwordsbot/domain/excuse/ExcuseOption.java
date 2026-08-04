package de.venomenon.gridwordsbot.domain.excuse;

import java.util.Objects;

/** One rendered option that is safe to persist and later select by round and position. */
public record ExcuseOption(
        ExcuseRound round,
        int position,
        String templateId,
        ExcuseStyle style,
        ExcuseTopic topic,
        String renderedText) {

    public ExcuseOption {
        Objects.requireNonNull(round, "round");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(renderedText, "renderedText");
        if (position < 1 || position > 3) {
            throw new IllegalArgumentException("position must be between 1 and 3");
        }
        if (templateId.isBlank() || renderedText.isBlank()) {
            throw new IllegalArgumentException("templateId and renderedText must not be blank");
        }
    }
}
