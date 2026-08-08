package de.venomenon.gridwordsbot.adapter.discord.achievement;

import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.domain.achievement.AchievementCategory;
import de.venomenon.gridwordsbot.port.in.AchievementsQueryUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** JDA-only presentation of the transport-neutral materialized Achievement profile. */
public final class AchievementsOverviewEmbedRenderer {
    private static final int PAGE_DESCRIPTION_LIMIT = 3_800;
    private final AchievementEmojiResolver emojis;

    public AchievementsOverviewEmbedRenderer(AchievementEmojiResolver emojis) {
        this.emojis = Objects.requireNonNull(emojis, "emojis");
    }

    public List<MessageEmbed> render(AchievementsQueryUseCase.Result result, String participantDisplay) {
        Objects.requireNonNull(result, "result");
        String display = neutralize(participantDisplay);
        if (result.entries().isEmpty()) {
            return List.of(new EmbedBuilder()
                    .setTitle("🏅 Achievements")
                    .setDescription(display + " hat noch keine Achievements in dieser Ansicht freigeschaltet.")
                    .build());
        }

        List<String> bodies = pageBodies(result.entries());
        List<MessageEmbed> pages = new ArrayList<>();
        for (int index = 0; index < bodies.size(); index++) {
            String title = bodies.size() == 1
                    ? "🏅 Achievements"
                    : "🏅 Achievements · Seite " + (index + 1) + "/" + bodies.size();
            pages.add(new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(bodies.get(index))
                    .setFooter("Profil: " + display)
                    .build());
        }
        return List.copyOf(pages);
    }

    private List<String> pageBodies(List<AchievementsQueryUseCase.Entry> entries) {
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (AchievementCategory category : AchievementCategory.values()) {
            List<AchievementsQueryUseCase.Entry> categoryEntries = entries.stream()
                    .filter(entry -> entry.category() == category)
                    .toList();
            if (categoryEntries.isEmpty()) continue;
            boolean first = true;
            for (AchievementsQueryUseCase.Entry entry : categoryEntries) {
                String item = renderEntry(entry);
                String block = first ? heading(category) + "\n\n" + item : item;
                if (!current.isEmpty() && current.length() + 2 + block.length() > PAGE_DESCRIPTION_LIMIT) {
                    pages.add(current.toString());
                    current.setLength(0);
                    block = (first ? heading(category) : heading(category) + " · Fortsetzung") + "\n\n" + item;
                }
                if (block.length() > PAGE_DESCRIPTION_LIMIT) {
                    throw new IllegalArgumentException("single Achievement entry exceeds Discord page limit");
                }
                if (!current.isEmpty()) current.append("\n\n");
                current.append(block);
                first = false;
            }
        }
        if (!current.isEmpty()) pages.add(current.toString());
        return List.copyOf(pages);
    }

    private String renderEntry(AchievementsQueryUseCase.Entry entry) {
        String emoji = emojis.resolve(entry.key()).filter(value -> !value.isBlank()).orElse(entry.fallbackEmoji());
        return emoji + " **" + entry.displayName() + "**\n" + entry.description();
    }

    private static String heading(AchievementCategory category) {
        return switch (category) {
            case EXPERIENCE -> "__Erfahrung__";
            case RELIABILITY -> "__Zuverlässigkeit__";
            case PERFORMANCE -> "__Leistung__";
            case SPECIAL -> "__Besonderes__";
        };
    }

    static String neutralize(String text) {
        String safe = Objects.requireNonNullElse(text, "Ehemaliger Spieler")
                .replace('@', ' ').replace('<', ' ').replace('>', ' ').replace('&', ' ')
                .replaceAll("\\s+", " ").trim();
        if (safe.isBlank()) return "Ehemaliger Spieler";
        return safe.length() <= 80 ? safe : safe.substring(0, 80);
    }
}
