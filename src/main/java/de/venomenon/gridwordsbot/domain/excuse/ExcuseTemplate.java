package de.venomenon.gridwordsbot.domain.excuse;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** One complete editorial sentence and its deterministic applicability metadata. */
public record ExcuseTemplate(
        String id,
        ExcuseStyle style,
        Set<GameType> games,
        ExcuseTopic topic,
        int specificity,
        int weight,
        Set<ExcuseCondition> requiresAll,
        Set<ExcuseCondition> excludesAny,
        String text,
        boolean selectable) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    public ExcuseTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(games, "games");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(requiresAll, "requiresAll");
        Objects.requireNonNull(excludesAny, "excludesAny");
        Objects.requireNonNull(text, "text");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("template id has an unsupported format: " + id);
        }
        if (games.isEmpty()) {
            throw new IllegalArgumentException("template must support at least one game");
        }
        games = Set.copyOf(EnumSet.copyOf(games));
        if (specificity < 0 || specificity > 1_000) {
            throw new IllegalArgumentException("specificity must be between 0 and 1000");
        }
        if (weight <= 0 || weight > 1_000_000) {
            throw new IllegalArgumentException("weight must be between 1 and 1000000");
        }
        requiresAll = Set.copyOf(requiresAll);
        excludesAny = Set.copyOf(excludesAny);
        if (requiresAll.stream().anyMatch(excludesAny::contains)) {
            throw new IllegalArgumentException("a condition cannot be both required and excluded");
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("template text must not be blank");
        }
    }

    public boolean supports(ExcuseContext context) {
        return games.contains(context.gameType())
                && context.conditions().containsAll(requiresAll)
                && excludesAny.stream().noneMatch(context.conditions()::contains);
    }

    public boolean isContextSpecific() {
        return specificity > 0;
    }

    public boolean isGeneral() {
        return games.containsAll(EnumSet.allOf(GameType.class))
                && requiresAll.isEmpty()
                && excludesAny.isEmpty();
    }
}
