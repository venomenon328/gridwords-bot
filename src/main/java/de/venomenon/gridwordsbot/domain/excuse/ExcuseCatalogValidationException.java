package de.venomenon.gridwordsbot.domain.excuse;

import java.util.List;

public final class ExcuseCatalogValidationException extends IllegalArgumentException {

    private final List<String> errors;

    public ExcuseCatalogValidationException(List<String> errors) {
        super("invalid excuse catalog: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
