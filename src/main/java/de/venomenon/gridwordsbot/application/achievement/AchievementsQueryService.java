package de.venomenon.gridwordsbot.application.achievement;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinition;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.AchievementScope;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.port.in.AchievementsQueryUseCase;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Reads only the materialized current award state and current catalog metadata. */
public final class AchievementsQueryService implements AchievementsQueryUseCase {
    private final AchievementAwardStateStore awards;
    private final AchievementDefinitionCatalog catalog;

    public AchievementsQueryService(AchievementAwardStateStore awards, AchievementDefinitionCatalog catalog) {
        this.awards = Objects.requireNonNull(awards, "awards");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public Result query(Query query) {
        Objects.requireNonNull(query, "query");
        Map<AchievementKey, AchievementAwardState.Snapshot> current = new LinkedHashMap<>();
        for (AchievementAwardState.Snapshot state : awards.findAll(query.guildId(), query.participantId())) {
            AchievementKey key = state.key().achievementKey();
            if (catalog.find(key).isEmpty()) {
                throw new IllegalStateException("persisted achievement state uses unknown catalog key: " + key.value());
            }
            if (current.putIfAbsent(key, state) != null) {
                throw new IllegalStateException("duplicate materialized achievement state: " + key.value());
            }
        }

        return new Result(catalog.definitions().stream()
                .filter(definition -> scopeMatches(definition.scope(), query.game()))
                .filter(definition -> {
                    AchievementAwardState.Snapshot state = current.get(definition.key());
                    return state != null && state.write().status() == AchievementAwardState.Status.ACTIVE;
                })
                .map(AchievementsQueryService::entry)
                .toList());
    }

    private static Entry entry(AchievementDefinition definition) {
        return new Entry(
                definition.key(),
                definition.category(),
                definition.scope(),
                definition.fallbackEmoji(),
                definition.displayName(),
                definition.description());
    }

    private static boolean scopeMatches(AchievementScope scope, GameFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case GRIDWORDS -> scope == AchievementScope.GRIDWORDS;
            case QUADWORDS -> scope == AchievementScope.QUADWORDS;
        };
    }
}
