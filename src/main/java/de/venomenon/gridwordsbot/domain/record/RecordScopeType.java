package de.venomenon.gridwordsbot.domain.record;

public enum RecordScopeType {
    PERSONAL("personal"),
    SERVER_INDIVIDUAL("server-individual"),
    SHARED("shared");

    private final String slug;

    RecordScopeType(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }
}
