package de.venomenon.gridwordsbot.application.achievement;

import java.util.List;
import java.util.Objects;

/** One logical Discord message; its bounded embeds must never be split into another message. */
public record RenderedAchievementAnnouncement(String publicationKey, String contentFingerprint,
                                               List<Embed> embeds) {
    public static final int MAXIMUM_EMBEDS = 10;
    /** Keep deliberate headroom below Discord's 6,000-character combined embed-text limit. */
    public static final int MAXIMUM_EMBED_TEXT = 5_850;
    public RenderedAchievementAnnouncement {
        Objects.requireNonNull(publicationKey, "publicationKey");
        Objects.requireNonNull(contentFingerprint, "contentFingerprint");
        embeds = List.copyOf(Objects.requireNonNull(embeds, "embeds"));
        if (embeds.isEmpty() || embeds.size() > MAXIMUM_EMBEDS) throw new IllegalArgumentException("invalid embed count");
        int text = embeds.stream().mapToInt(embed -> embed.title().length() + embed.description().length()).sum();
        if (text > MAXIMUM_EMBED_TEXT) throw new IllegalArgumentException("combined embed text exceeds Discord limit");
    }

    public record Embed(String title, String description) {
        public Embed {
            title = required(title, 256, "title");
            description = required(description, 4_096, "description");
        }
        private static String required(String value, int maximum, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank() || value.length() > maximum) throw new IllegalArgumentException(name + " is invalid");
            return value;
        }
    }
}
