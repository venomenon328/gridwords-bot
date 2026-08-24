package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable fachliche Version eines Rekorddefinitionskatalogs. */
public record RecordDefinitionVersion(String value) {
    private static final Pattern VALID_VALUE = Pattern.compile("[a-z][a-z0-9-]*");

    public static final RecordDefinitionVersion RECORDS_V1 = new RecordDefinitionVersion("records-v1");
    public static final RecordDefinitionVersion RECORDS_V2 = new RecordDefinitionVersion("records-v2");

    public RecordDefinitionVersion {
        Objects.requireNonNull(value, "value");
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("value must be a stable lowercase definition version");
        }
    }
}
