package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stabiler, persistierbarer Schlüssel einer einzelnen Rekorddefinition. */
public record RecordDefinitionKey(String value) implements Comparable<RecordDefinitionKey> {
    private static final Pattern VALID_VALUE = Pattern.compile("[a-z0-9]+(?:[.-][a-z0-9]+)*");

    public RecordDefinitionKey {
        Objects.requireNonNull(value, "value");
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("value must be a stable lowercase definition key");
        }
    }

    @Override
    public int compareTo(RecordDefinitionKey other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
