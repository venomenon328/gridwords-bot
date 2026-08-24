package de.venomenon.gridwordsbot.adapter.discord.achievement;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinition;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.port.in.AchievementsQueryUseCase;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AchievementsOverviewEmbedRendererTest {
    private static final AchievementDefinitionCatalog CATALOG = AchievementDefinitionCatalog.achievementsV2();
    private static final LocalDate EARNED_ON = LocalDate.of(2026, 7, 18);

    @Test
    void groupsInFixedCategoryOrderUsesCustomEmojiAndRendersEarnedDateWithinPaginationLimits() {
        AchievementEmojiResolver emojis = key -> key.equals(CATALOG.definitions().getFirst().key())
                ? Optional.of("<:custom:123>") : Optional.empty();
        AchievementsOverviewEmbedRenderer renderer = new AchievementsOverviewEmbedRenderer(emojis);
        List<AchievementsQueryUseCase.Entry> entries = CATALOG.definitions().stream().map(this::entry).toList();

        var pages = renderer.render(new AchievementsQueryUseCase.Result(entries), "<@123> @Ada");
        String text = pages.stream().map(embed -> embed.getDescription()).reduce("", (left, right) -> left + "\n" + right);

        assertThat(pages).hasSizeGreaterThan(1);
        assertThat(pages).allSatisfy(page -> assertThat(page.getDescription().length()).isLessThanOrEqualTo(3_800));
        assertThat(text).contains("<:custom:123>", CATALOG.definitions().get(1).fallbackEmoji());
        assertThat(text).contains("Freigeschaltet am 18.07.2026");
        assertThat(text).doesNotContain("<@123>", "@Ada");
        assertThat(text.indexOf("__Erfahrung__")).isLessThan(text.indexOf("__Zuverlässigkeit__"));
        assertThat(text.indexOf("__Zuverlässigkeit__")).isLessThan(text.indexOf("__Leistung__"));
        assertThat(text.indexOf("__Leistung__")).isLessThan(text.indexOf("__Besonderes__"));
        for (AchievementDefinition definition : CATALOG.definitions()) {
            assertThat(text).contains(definition.displayName(), definition.description());
        }
    }

    @Test
    void emptyProfileIsCompactAndMentionSafe() {
        var pages = new AchievementsOverviewEmbedRenderer(AchievementEmojiResolver.unicodeOnly())
                .render(new AchievementsQueryUseCase.Result(List.of()), "<@7> @Ada");

        assertThat(pages).singleElement().satisfies(embed -> {
            assertThat(embed.getTitle()).isEqualTo("🏅 Achievements");
            assertThat(embed.getDescription()).contains("noch keine Achievements").doesNotContain("<@7>", "@Ada");
        });
    }

    private AchievementsQueryUseCase.Entry entry(AchievementDefinition definition) {
        return new AchievementsQueryUseCase.Entry(
                definition.key(), definition.category(), definition.scope(), definition.fallbackEmoji(),
                definition.displayName(), definition.description(), EARNED_ON);
    }
}
