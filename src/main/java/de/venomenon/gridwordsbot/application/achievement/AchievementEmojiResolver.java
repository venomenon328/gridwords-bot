package de.venomenon.gridwordsbot.application.achievement;

import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import java.util.Optional;

/** Presentation-only hook for optional Discord custom emojis. */
@FunctionalInterface
public interface AchievementEmojiResolver {
    Optional<String> resolve(AchievementKey key);

    static AchievementEmojiResolver unicodeOnly() {
        return ignored -> Optional.empty();
    }
}
