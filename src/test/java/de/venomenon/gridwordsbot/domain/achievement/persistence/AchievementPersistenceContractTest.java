package de.venomenon.gridwordsbot.domain.achievement.persistence;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AchievementPersistenceContractTest {
    private static final Instant NOW = Instant.parse("2026-08-08T07:00:00Z");

    @Test
    void invalidatedAwardRequiresInvalidationTimestamp() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AchievementAwardState.Write(
                AchievementDefinitionVersion.ACHIEVEMENTS_V1,
                AchievementAwardState.Status.INVALIDATED,
                LocalDate.of(2026, 8, 8),
                NOW,
                AchievementEvidence.Kind.GAME_RESULT,
                "game-result:1",
                Optional.empty()));
    }

    @Test
    void synchronizedAnnouncementRequiresConfirmedMessageAndCompletion() {
        AchievementAnnouncement.Registration registration = new AchievementAnnouncement.Registration(
                1, 2, 7, AchievementDefinitionVersion.ACHIEVEMENTS_V1,
                AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH,
                "live:1", "renderer-v1", "a".repeat(64));
        assertThatIllegalArgumentException().isThrownBy(() -> new AchievementAnnouncement.Snapshot(
                1, registration, AchievementAnnouncement.DeliveryState.SYNCHRONIZED,
                Optional.empty(), Optional.empty(), 1, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(NOW), Optional.of(NOW),
                Optional.empty(), Optional.empty(), NOW, NOW));
    }

    @Test
    void awardBusinessKeyDoesNotContainDefinitionVersion() {
        AchievementAwardState.Key key = new AchievementAwardState.Key(
                1, 7, new AchievementKey("participation.1.gridwords"));
        new AchievementAwardState.Write(
                new AchievementDefinitionVersion("achievements-v2"),
                AchievementAwardState.Status.ACTIVE,
                LocalDate.of(2026, 8, 8),
                NOW,
                AchievementEvidence.Kind.GAME_RESULT,
                "game-result:1",
                Optional.empty());
        // The stable award identity intentionally remains guild + participant + key.
        org.assertj.core.api.Assertions.assertThat(key.achievementKey().value()).isEqualTo("participation.1.gridwords");
    }
}
