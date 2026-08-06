package de.venomenon.gridwordsbot.application.record;

import java.util.Objects;

/** One bounded, transport-neutral Discord embed page. */
public record RenderedRecordAnnouncementPage(int position, String title, String description, String footer) {
    public RenderedRecordAnnouncementPage {
        if (position < 0) throw new IllegalArgumentException("position must not be negative");
        title = required(title, 256, "title");
        description = required(description, 4_096, "description");
        footer = required(footer, 2_048, "footer");
    }

    private static String required(String value, int limit, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > limit) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }
}
