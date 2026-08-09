package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.achievement.AchievementCategory;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.AchievementScope;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Read-only transport-neutral profile query for the materialized current Achievement projection. */
public interface AchievementsQueryUseCase {
    Result query(Query query);

    record Query(long guildId, long participantId, GameFilter game) {
        public Query {
            if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
            if (participantId <= 0) throw new IllegalArgumentException("participantId must be positive");
            Objects.requireNonNull(game, "game");
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
            LocalDate earnedOn) {
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(scope, "scope");
            fallbackEmoji = required(fallbackEmoji, "fallbackEmoji");
            displayName = required(displayName, "displayName");
            description = required(description, "description");
            Objects.requireNonNull(earnedOn, "earnedOn");
        }

        private static String required(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
            return value;
        }
    }

    enum GameFilter { ALL, GRIDWORDS, QUADWORDS }
}
