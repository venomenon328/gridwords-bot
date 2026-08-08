package de.venomenon.gridwordsbot.application.achievement;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinition;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.port.in.AchievementCatalogQueryUseCase;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Reads the full catalog and annotates it solely from the materialized current award projection. */
public final class AchievementCatalogQueryService implements AchievementCatalogQueryUseCase {
    private final AchievementAwardStateStore awards;
    private final AchievementDefinitionCatalog catalog;

    public AchievementCatalogQueryService(AchievementAwardStateStore awards, AchievementDefinitionCatalog catalog) {
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
                .map(definition -> entry(definition, current.get(definition.key())))
                .toList());
    }

    private static Entry entry(AchievementDefinition definition, AchievementAwardState.Snapshot state) {
        boolean achieved = state != null && state.write().status() == AchievementAwardState.Status.ACTIVE;
        return new Entry(
                definition.key(), definition.category(), definition.scope(), definition.fallbackEmoji(),
                definition.displayName(), definition.description(), achieved);
    }
}
