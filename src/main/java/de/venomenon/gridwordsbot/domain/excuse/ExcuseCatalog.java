package de.venomenon.gridwordsbot.domain.excuse;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** A versioned immutable set of editorial templates. */
public record ExcuseCatalog(String version, List<ExcuseTemplate> templates) {

    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*");

    public ExcuseCatalog {
        Objects.requireNonNull(version, "version");
        templates = List.copyOf(Objects.requireNonNull(templates, "templates"));
        if (!VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException("catalog version has an unsupported format");
        }
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("catalog must contain at least one template");
        }
    }
}
