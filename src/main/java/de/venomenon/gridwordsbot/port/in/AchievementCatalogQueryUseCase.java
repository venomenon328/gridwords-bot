package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.achievement.AchievementCategory;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.AchievementScope;
import java.util.List;
import java.util.Objects;

/** Read-only personal catalog view of available Achievements and their current active status. */
public interface AchievementCatalogQueryUseCase {
    Result query(Query query);

    record Query(
            long guildId,
            long participantId,
            GameFilter game,
            CategoryFilter category,
            StatusFilter status) {
        public Query {
            if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
            if (participantId <= 0) throw new IllegalArgumentException("participantId must be positive");
            Objects.requireNonNull(game, "game");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(status, "status");
        }

        public Query(long guildId, long participantId) {
            this(guildId, participantId, GameFilter.ALL, CategoryFilter.ALL, StatusFilter.ALL);
        }
    }

    record Result(List<Entry> entries) {
        public Result {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    record Entry(
            AchievementKey key,
            AchievementCategory category,
            AchievementScope scope,
            String fallbackEmoji,
            String displayName,
            String description,
            boolean achieved) {
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(scope, "scope");
            fallbackEmoji = required(fallbackEmoji, "fallbackEmoji");
            displayName = required(displayName, "displayName");
            description = required(description, "description");
        }

        private static String required(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
            return value;
        }
    }

    enum GameFilter { ALL, GRIDWORDS, QUADWORDS, CROSS_GAME }
    enum CategoryFilter { ALL, EXPERIENCE, RELIABILITY, PERFORMANCE, SPECIAL }
    enum StatusFilter { ALL, ACHIEVED, OPEN }
}
