package de.venomenon.gridwordsbot.domain.excuse;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Selection constraints supplied by the interaction lifecycle and repetition protection. */
public record ExcuseSelectionRequest(
        ExcuseRound round,
        Optional<ExcuseStyle> requiredStyle,
        Set<String> excludedTemplateIds,
        Set<ExcuseTopic> discouragedTopics) {

    public ExcuseSelectionRequest {
        Objects.requireNonNull(round, "round");
        Objects.requireNonNull(requiredStyle, "requiredStyle");
        excludedTemplateIds = Set.copyOf(Objects.requireNonNull(excludedTemplateIds, "excludedTemplateIds"));
        discouragedTopics = Set.copyOf(Objects.requireNonNull(discouragedTopics, "discouragedTopics"));
        if (excludedTemplateIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("excluded template ids must not be blank");
        }
        if (round == ExcuseRound.INITIAL && requiredStyle.isPresent()) {
            throw new IllegalArgumentException("initial selection cannot require one style");
        }
        if (round == ExcuseRound.STYLE_REROLL && requiredStyle.isEmpty()) {
            throw new IllegalArgumentException("style reroll requires a style");
        }
    }

    public static ExcuseSelectionRequest initial(Set<String> excludedIds, Set<ExcuseTopic> discouragedTopics) {
        return new ExcuseSelectionRequest(ExcuseRound.INITIAL, Optional.empty(), excludedIds, discouragedTopics);
    }

    public static ExcuseSelectionRequest styleReroll(ExcuseStyle style, Set<String> excludedIds) {
        return new ExcuseSelectionRequest(
                ExcuseRound.STYLE_REROLL, Optional.of(Objects.requireNonNull(style)), excludedIds, Set.of());
    }
}
