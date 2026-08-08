package de.venomenon.gridwordsbot.domain.achievement;

import java.util.Objects;

/** Vollständige transportneutrale Definition eines einzelnen Achievements. */
public record AchievementDefinition(
        AchievementKey key,
        AchievementDefinitionVersion definitionVersion,
        AchievementCategory category,
        AchievementScope scope,
        String fallbackEmoji,
        String displayName,
        String description,
        AchievementRule rule) {

    public AchievementDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(definitionVersion, "definitionVersion");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(scope, "scope");
        fallbackEmoji = requireText(fallbackEmoji, "fallbackEmoji");
        displayName = requireText(displayName, "displayName");
        description = requireText(description, "description");
        Objects.requireNonNull(rule, "rule");

        if (scope != rule.scope()) {
            throw new IllegalArgumentException("definition scope does not match rule scope");
        }
        validateDisplayNamePrefix(scope, displayName);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static void validateDisplayNamePrefix(AchievementScope scope, String displayName) {
        String requiredPrefix = switch (scope) {
            case GRIDWORDS -> "GW:";
            case QUADWORDS -> "QW:";
            case CROSS_GAME -> "GW+QW:";
            case GLOBAL -> null;
        };
        if (requiredPrefix != null && !displayName.startsWith(requiredPrefix)) {
            throw new IllegalArgumentException("displayName must start with " + requiredPrefix + " for scope " + scope);
        }
        if (scope == AchievementScope.GLOBAL
                && (displayName.startsWith("GW:") || displayName.startsWith("QW:") || displayName.startsWith("GW+QW:"))) {
            throw new IllegalArgumentException("global displayName must not use a game scope prefix");
        }
    }
}
