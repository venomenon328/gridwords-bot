package de.venomenon.gridwordsbot.adapter.discord.achievement;

import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.domain.achievement.AchievementCategory;
import de.venomenon.gridwordsbot.port.in.AchievementCatalogQueryUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Dense one-interaction catalog rendering: every Achievement with only achieved/not-achieved status. */
public final class AchievementCatalogEmbedRenderer {
    private static final int EMBED_DESCRIPTION_LIMIT = 4_000;
    private static final int MESSAGE_EMBED_TEXT_LIMIT = 6_000;
    private static final int MAXIMUM_EMBEDS = 10;
    private final AchievementEmojiResolver emojis;

    public AchievementCatalogEmbedRenderer(AchievementEmojiResolver emojis) {
        this.emojis = Objects.requireNonNull(emojis, "emojis");
    }

    public List<MessageEmbed> render(AchievementCatalogQueryUseCase.Result result) {
        Objects.requireNonNull(result, "result");
        List<String> bodies = bodies(result.entries());
        if (bodies.isEmpty()) throw new IllegalArgumentException("achievement catalog must not be empty");
        if (bodies.size() > MAXIMUM_EMBEDS) throw new IllegalArgumentException("achievement catalog exceeds Discord embed count");

        List<MessageEmbed> embeds = new ArrayList<>();
        int totalText = 0;
        for (int index = 0; index < bodies.size(); index++) {
            String title = index == 0 ? "🏅 Achievement-Liste" : "🏅 Achievement-Liste · Fortsetzung";
            totalText += title.length() + bodies.get(index).length();
            embeds.add(new EmbedBuilder().setTitle(title).setDescription(bodies.get(index)).build());
        }
        if (totalText > MESSAGE_EMBED_TEXT_LIMIT) {
            throw new IllegalArgumentException("achievement catalog exceeds Discord message embed text limit");
        }
        return List.copyOf(embeds);
    }

    private List<String> bodies(List<AchievementCatalogQueryUseCase.Entry> entries) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (AchievementCategory category : AchievementCategory.values()) {
            List<AchievementCatalogQueryUseCase.Entry> categoryEntries = entries.stream()
                    .filter(entry -> entry.category() == category)
                    .toList();
            if (categoryEntries.isEmpty()) continue;
            boolean first = true;
            for (AchievementCatalogQueryUseCase.Entry entry : categoryEntries) {
                String item = renderEntry(entry);
                String block = first ? heading(category) + "\n\n" + item : item;
                if (!current.isEmpty() && current.length() + 2 + block.length() > EMBED_DESCRIPTION_LIMIT) {
                    result.add(current.toString());
                    current.setLength(0);
                    block = heading(category) + " · Fortsetzung\n\n" + item;
                }
                if (block.length() > EMBED_DESCRIPTION_LIMIT) {
                    throw new IllegalArgumentException("single Achievement catalog entry exceeds Discord embed limit");
                }
                if (!current.isEmpty()) current.append("\n\n");
                current.append(block);
                first = false;
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return List.copyOf(result);
    }

    private String renderEntry(AchievementCatalogQueryUseCase.Entry entry) {
        String status = entry.achieved() ? "✅" : "❌";
        String emoji = emojis.resolve(entry.key()).filter(value -> !value.isBlank()).orElse(entry.fallbackEmoji());
        return status + " " + emoji + " **" + entry.displayName() + "**\n" + entry.description();
    }

    private static String heading(AchievementCategory category) {
        return switch (category) {
            case EXPERIENCE -> "__Erfahrung__";
            case RELIABILITY -> "__Zuverlässigkeit__";
            case PERFORMANCE -> "__Leistung__";
            case SPECIAL -> "__Besonderes__";
        };
    }
}
