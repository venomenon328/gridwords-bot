package de.venomenon.gridwordsbot.adapter.discord.achievement;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.port.in.AchievementCatalogQueryUseCase;
import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

class AchievementCatalogEmbedRendererTest {
    @Test
    void rendersAllSixtyWithOnlyCheckOrCrossStatusInsideOneDiscordInteractionBudget() {
        var definitions = AchievementDefinitionCatalog.achievementsV1().definitions();
        List<AchievementCatalogQueryUseCase.Entry> entries = java.util.stream.IntStream.range(0, definitions.size())
                .mapToObj(index -> {
                    var definition = definitions.get(index);
                    return new AchievementCatalogQueryUseCase.Entry(
                            definition.key(), definition.category(), definition.scope(), definition.fallbackEmoji(),
                            definition.displayName(), definition.description(), index % 2 == 0);
                })
                .toList();

        List<MessageEmbed> embeds = new AchievementCatalogEmbedRenderer(AchievementEmojiResolver.unicodeOnly())
                .render(new AchievementCatalogQueryUseCase.Result(entries));
        String text = embeds.stream()
                .map(embed -> java.util.Optional.ofNullable(embed.getDescription()).orElse(""))
                .reduce("", (left, right) -> left + "\n" + right);
        int totalText = embeds.stream().mapToInt(embed ->
                java.util.Optional.ofNullable(embed.getTitle()).orElse("").length()
                        + java.util.Optional.ofNullable(embed.getDescription()).orElse("").length()).sum();

        assertThat(embeds).hasSizeBetween(1, 10);
        assertThat(totalText).isLessThanOrEqualTo(6_000);
        assertThat(text).contains("✅", "❌", "__Erfahrung__", "__Zuverlässigkeit__", "__Leistung__", "__Besonderes__");
        for (var definition : definitions) {
            assertThat(text).contains(definition.displayName(), definition.description());
        }
        assertThat(text).doesNotContain("von 10", "Fortschritt", "Tage geschafft", "%");
    }

    @Test
    void rendersEmptyFilterResultAsNeutralState() {
        List<MessageEmbed> embeds = new AchievementCatalogEmbedRenderer(AchievementEmojiResolver.unicodeOnly())
                .render(new AchievementCatalogQueryUseCase.Result(List.of()));

        assertThat(embeds).singleElement().satisfies(embed -> {
            assertThat(embed.getTitle()).isEqualTo("🏅 Achievement-Liste");
            assertThat(embed.getDescription()).isEqualTo("Keine Achievements entsprechen den gewählten Filtern.");
        });
    }
}
