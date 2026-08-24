package de.venomenon.gridwordsbot.domain.achievement;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable fachliche Version eines Achievement-Definitionskatalogs. */
public record AchievementDefinitionVersion(String value) {
    private static final Pattern VALID_VALUE = Pattern.compile("[a-z][a-z0-9-]*");

    public static final AchievementDefinitionVersion ACHIEVEMENTS_V1 =
            new AchievementDefinitionVersion("achievements-v1");
    public static final AchievementDefinitionVersion ACHIEVEMENTS_V2 =
            new AchievementDefinitionVersion("achievements-v2");

    public AchievementDefinitionVersion {
        Objects.requireNonNull(value, "value");
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("value must be a stable lowercase definition version");
        }
    }
}
