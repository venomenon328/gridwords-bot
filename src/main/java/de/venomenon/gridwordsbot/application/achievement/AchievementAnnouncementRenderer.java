package de.venomenon.gridwordsbot.application.achievement;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinition;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Pure, bounded renderer for the single-message Achievement V1 contract. */
public final class AchievementAnnouncementRenderer {
    public static final String VERSION = "achievements-v1-discord-1";
    private static final int DESCRIPTION_LIMIT = 3_900;
    private final AchievementDefinitionCatalog catalog;
    private final AchievementEmojiResolver emojis;

    public AchievementAnnouncementRenderer(AchievementDefinitionCatalog catalog, AchievementEmojiResolver emojis) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.emojis = Objects.requireNonNull(emojis, "emojis");
    }

    public RenderedAchievementAnnouncement render(AchievementAnnouncementRenderInput input) {
        Objects.requireNonNull(input, "input");
        AchievementAnnouncement.Snapshot announcement = input.announcement();
        List<AchievementEventFact.Snapshot> ordered = input.events().stream()
                .sorted(Comparator.comparing(event -> catalogIndex(event.fact().awardKey().achievementKey().value())))
                .toList();
        if (ordered.isEmpty()) throw new IllegalArgumentException("achievement announcement requires events");
        String display = neutralize(input.participantDisplayName());
        String publicationKey = publicationKey(announcement.registration().idempotencyKey());
        String header = switch (announcement.registration().type()) {
            case LIVE_UNLOCK_BATCH -> display + " hat " + ordered.size() + " neue Achievement"
                    + (ordered.size() == 1 ? " freigeschaltet:" : "s freigeschaltet:");
            case HISTORICAL_INTRODUCTION -> display + " startet mit " + ordered.size()
                    + " r\u00fcckwirkend vergebenen Achievements:";
        };
        List<String> lines = ordered.stream().map(this::line).toList();
        List<String> bodies = bodies(header, lines);
        String title = announcement.registration().type() == AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH
                ? "\ud83c\udfc5 Neue Achievements" : "\ud83c\udfc5 Achievements";
        List<RenderedAchievementAnnouncement.Embed> embeds = bodies.stream()
                .map(body -> new RenderedAchievementAnnouncement.Embed(title, body)).toList();
        String fingerprint = sha256(embeds.stream().map(embed -> embed.title() + "\n" + embed.description())
                .reduce("", (left, right) -> left + "\n---\n" + right));
        return new RenderedAchievementAnnouncement(publicationKey, fingerprint, embeds);
    }

    public static String publicationKey(String idempotencyKey) {
        return "achievement-announcement:" + sha256(Objects.requireNonNull(idempotencyKey, "idempotencyKey"));
    }

    static String neutralize(String text) {
        String safe = Objects.requireNonNullElse(text, "Ehemaliger Spieler")
                .replace('@', ' ').replace('<', ' ').replace('>', ' ').replace('&', ' ')
                .replaceAll("\\s+", " ").trim();
        if (safe.isBlank()) return "Ehemaliger Spieler";
        return safe.length() <= 80 ? safe : safe.substring(0, 80);
    }

    private String line(AchievementEventFact.Snapshot event) {
        AchievementDefinition definition = catalog.find(event.fact().awardKey().achievementKey())
                .orElseThrow(() -> new IllegalArgumentException("unknown achievement key in event"));
        String emoji = emojis.resolve(definition.key()).filter(value -> !value.isBlank()).orElse(definition.fallbackEmoji());
        return emoji + " **" + definition.displayName() + "**\n" + definition.description();
    }

    private int catalogIndex(String key) {
        List<AchievementDefinition> definitions = catalog.definitions();
        for (int index = 0; index < definitions.size(); index++) if (definitions.get(index).key().value().equals(key)) return index;
        throw new IllegalArgumentException("unknown achievement key in event");
    }

    private static List<String> bodies(String header, List<String> lines) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder(header);
        for (String line : lines) {
            int addition = line.length() + 2;
            if (current.length() + addition > DESCRIPTION_LIMIT) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append("\n\n");
            current.append(line);
        }
        if (!current.isEmpty()) result.add(current.toString());
        return List.copyOf(result);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
