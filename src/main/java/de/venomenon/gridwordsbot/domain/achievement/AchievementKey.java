package de.venomenon.gridwordsbot.domain.achievement;

import java.util.Objects;
import java.util.regex.Pattern;

/** Dauerhafte fachliche Identität einer Achievement-Definition. */
public record AchievementKey(String value) implements Comparable<AchievementKey> {
    private static final Pattern VALID_VALUE = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    public AchievementKey {
        Objects.requireNonNull(value, "value");
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("value must be a stable lowercase achievement key");
        }
    }

    @Override
    public int compareTo(AchievementKey other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
