package de.venomenon.gridwordsbot.adapter.catalog;

import java.util.List;

public final class ExcuseCatalogLoadException extends IllegalArgumentException {

    private final List<String> errors;

    public ExcuseCatalogLoadException(List<String> errors) {
        super("could not load excuse catalog: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public ExcuseCatalogLoadException(String message, Throwable cause) {
        super(message, cause);
        this.errors = List.of(message);
    }

    public List<String> errors() {
        return errors;
    }
}
