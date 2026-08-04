package de.venomenon.gridwordsbot.domain.excuse;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Fully verified values available to condition evaluation and placeholder rendering. */
public record ExcuseContext(
        GameType gameType,
        Set<ExcuseCondition> conditions,
        Map<ExcusePlaceholder, String> placeholders) {

    public ExcuseContext {
        Objects.requireNonNull(gameType, "gameType");
        conditions = Set.copyOf(Objects.requireNonNull(conditions, "conditions"));
        Objects.requireNonNull(placeholders, "placeholders");
        EnumMap<ExcusePlaceholder, String> normalized = new EnumMap<>(ExcusePlaceholder.class);
        placeholders.forEach((key, value) -> {
            Objects.requireNonNull(key, "placeholder key");
            Objects.requireNonNull(value, "placeholder value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("placeholder values must not be blank");
            }
            normalized.put(key, value);
        });
        normalized.putIfAbsent(ExcusePlaceholder.GAME, gameType == GameType.GRIDWORDS ? "GridWords" : "QuadWords");
        placeholders = Map.copyOf(normalized);
    }

    public static ExcuseContext forGame(GameType gameType) {
        return new ExcuseContext(gameType, Set.of(), Map.of());
    }

    public Optional<String> placeholder(ExcusePlaceholder placeholder) {
        return Optional.ofNullable(placeholders.get(placeholder));
    }
}
