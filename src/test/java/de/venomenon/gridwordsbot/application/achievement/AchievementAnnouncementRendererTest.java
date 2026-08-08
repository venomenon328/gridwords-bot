package de.venomenon.gridwordsbot.application.achievement;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinition;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AchievementAnnouncementRendererTest {
    private static final AchievementDefinitionCatalog CATALOG = AchievementDefinitionCatalog.achievementsV1();
    private static final Instant NOW = Instant.parse("2026-08-08T07:00:00Z");

    @Test
    void liveUnlockIsSingleMentionSafeMessageWithEveryRequiredFact() {
        AchievementAnnouncementRenderer renderer = new AchievementAnnouncementRenderer(CATALOG,
                key -> key.value().equals("participation.1.gridwords") ? Optional.of("<:badge:1>") : Optional.empty());
        RenderedAchievementAnnouncement rendered = renderer.render(new AchievementAnnouncementRenderInput(
                announcement(AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH),
                List.of(event(CATALOG.definitions().getFirst())), "<@123> @Ada"));

        assertThat(rendered.embeds()).hasSize(1);
        assertThat(rendered.embeds().getFirst().description()).contains("Ada", "1 neue Achievement", "<:badge:1>",
                "GW: Dabei!", "Dein erstes gültiges GridWords-Ergebnis.").doesNotContain("<@", "@Ada");
        assertThat(rendered.publicationKey()).startsWith("achievement-announcement:");
    }

    @Test
    void historicalIntroductionKeepsCatalogOrderAndFitsAllSixtyAchievementsIntoOneMessage() {
        AchievementAnnouncementRenderer renderer = new AchievementAnnouncementRenderer(CATALOG, AchievementEmojiResolver.unicodeOnly());
        List<AchievementEventFact.Snapshot> events = CATALOG.definitions().stream().map(this::event).toList();
        RenderedAchievementAnnouncement rendered = renderer.render(new AchievementAnnouncementRenderInput(
                announcement(AchievementAnnouncement.Type.HISTORICAL_INTRODUCTION), events, "Ada"));
        String text = rendered.embeds().stream().map(RenderedAchievementAnnouncement.Embed::description)
                .reduce("", (left, right) -> left + "\n" + right);

        assertThat(rendered.embeds()).hasSizeBetween(1, RenderedAchievementAnnouncement.MAXIMUM_EMBEDS);
        assertThat(rendered.embeds().stream().mapToInt(embed -> embed.title().length() + embed.description().length()).sum())
                .isLessThanOrEqualTo(RenderedAchievementAnnouncement.MAXIMUM_EMBED_TEXT);
        assertThat(text).contains("startet mit 60 rückwirkend vergebenen Achievements:");
        for (AchievementDefinition definition : CATALOG.definitions()) {
            assertThat(text).contains(definition.fallbackEmoji(), definition.displayName(), definition.description());
        }
        assertThat(text.indexOf(CATALOG.definitions().getFirst().displayName()))
                .isLessThan(text.indexOf(CATALOG.definitions().getLast().displayName()));
        assertThat(text).doesNotContain("GridWords-Achievements", "QuadWords-Achievements", "Allgemeine Achievements");
    }

    private AchievementAnnouncement.Snapshot announcement(AchievementAnnouncement.Type type) {
        AchievementAnnouncement.Registration registration = new AchievementAnnouncement.Registration(
                1, 2, 3, CATALOG.version(), type, "logical:achievement:announcement", "old-renderer", "a".repeat(64));
        return new AchievementAnnouncement.Snapshot(1, registration, AchievementAnnouncement.DeliveryState.CLAIMED,
                Optional.of(UUID.randomUUID()), Optional.of(NOW.plusSeconds(60)), 1, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), NOW, NOW);
    }

    private AchievementEventFact.Snapshot event(AchievementDefinition definition) {
        UUID id = UUID.nameUUIDFromBytes(definition.key().value().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AchievementEventFact.Draft draft = new AchievementEventFact.Draft(id, "event:" + definition.key().value(),
                new AchievementAwardState.Key(1, 3, definition.key()), CATALOG.version(), AchievementEventFact.Type.UNLOCKED,
                LocalDate.of(2026, 8, 7), AchievementEvidence.Kind.GAME_RESULT, "result:" + definition.key().value(),
                AchievementEventFact.ProcessingOrigin.BOOTSTRAP, NOW);
        return new AchievementEventFact.Snapshot(draft, NOW);
    }
}
