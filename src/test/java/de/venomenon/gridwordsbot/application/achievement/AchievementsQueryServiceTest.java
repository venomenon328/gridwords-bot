package de.venomenon.gridwordsbot.application.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinition;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.AchievementScope;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.port.in.AchievementsQueryUseCase;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AchievementsQueryServiceTest {
    private static final AchievementDefinitionCatalog CATALOG = AchievementDefinitionCatalog.achievementsV1();
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

    @Test
    void allViewUsesCatalogOrderAcrossAllScopesAndHidesInvalidatedAwards() {
        AchievementAwardStateStore store = mock(AchievementAwardStateStore.class);
        AchievementDefinition grid = definition(AchievementScope.GRIDWORDS);
        AchievementDefinition quad = definition(AchievementScope.QUADWORDS);
        AchievementDefinition cross = definition(AchievementScope.CROSS_GAME);
        AchievementDefinition global = definition(AchievementScope.GLOBAL);
        AchievementDefinition invalidated = CATALOG.definitions().stream()
                .filter(definition -> definition.scope() == AchievementScope.GRIDWORDS && !definition.key().equals(grid.key()))
                .findFirst().orElseThrow();
        when(store.findAll(1, 7)).thenReturn(List.of(
                active(global), active(cross), invalidated(invalidated), active(quad), active(grid)));

        var result = new AchievementsQueryService(store, CATALOG)
                .query(new AchievementsQueryUseCase.Query(1, 7, AchievementsQueryUseCase.GameFilter.ALL));

        assertThat(result.entries()).extracting(entry -> entry.key())
                .containsExactlyElementsOf(CATALOG.definitions().stream()
                        .filter(definition -> List.of(grid.key(), quad.key(), cross.key(), global.key()).contains(definition.key()))
                        .map(AchievementDefinition::key).toList());
        assertThat(result.entries()).extracting(entry -> entry.scope())
                .contains(AchievementScope.GRIDWORDS, AchievementScope.QUADWORDS, AchievementScope.CROSS_GAME, AchievementScope.GLOBAL);
        assertThat(result.entries()).extracting(entry -> entry.key()).doesNotContain(invalidated.key());
        verify(store).findAll(1, 7);
    }

    @Test
    void singleGameFiltersExcludeCrossGameAndGlobalScopes() {
        AchievementAwardStateStore store = mock(AchievementAwardStateStore.class);
        List<AchievementAwardState.Snapshot> states = CATALOG.definitions().stream()
                .map(this::active)
                .toList();
        when(store.findAll(1, 7)).thenReturn(states);
        AchievementsQueryService service = new AchievementsQueryService(store, CATALOG);

        var grid = service.query(new AchievementsQueryUseCase.Query(1, 7, AchievementsQueryUseCase.GameFilter.GRIDWORDS));
        var quad = service.query(new AchievementsQueryUseCase.Query(1, 7, AchievementsQueryUseCase.GameFilter.QUADWORDS));

        assertThat(grid.entries()).isNotEmpty().allSatisfy(entry -> assertThat(entry.scope()).isEqualTo(AchievementScope.GRIDWORDS));
        assertThat(quad.entries()).isNotEmpty().allSatisfy(entry -> assertThat(entry.scope()).isEqualTo(AchievementScope.QUADWORDS));
    }

    @Test
    void unknownPersistedKeyIsATechnicalInvariantViolation() {
        AchievementAwardStateStore store = mock(AchievementAwardStateStore.class);
        AchievementKey unknown = new AchievementKey("unknown.persisted.achievement");
        when(store.findAll(1, 7)).thenReturn(List.of(state(unknown, AchievementAwardState.Status.ACTIVE)));

        assertThatThrownBy(() -> new AchievementsQueryService(store, CATALOG)
                .query(new AchievementsQueryUseCase.Query(1, 7, AchievementsQueryUseCase.GameFilter.ALL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown catalog key");
    }

    @Test
    void missingAwardStateProducesAnEmptyProfileWithoutOtherReads() {
        AchievementAwardStateStore store = mock(AchievementAwardStateStore.class);
        when(store.findAll(1, 7)).thenReturn(List.of());

        var result = new AchievementsQueryService(store, CATALOG)
                .query(new AchievementsQueryUseCase.Query(1, 7, AchievementsQueryUseCase.GameFilter.ALL));

        assertThat(result.entries()).isEmpty();
        verify(store).findAll(1, 7);
    }

    private AchievementDefinition definition(AchievementScope scope) {
        return CATALOG.definitions().stream().filter(definition -> definition.scope() == scope).findFirst().orElseThrow();
    }

    private AchievementAwardState.Snapshot active(AchievementDefinition definition) {
        return state(definition.key(), AchievementAwardState.Status.ACTIVE);
    }

    private AchievementAwardState.Snapshot invalidated(AchievementDefinition definition) {
        return state(definition.key(), AchievementAwardState.Status.INVALIDATED);
    }

    private AchievementAwardState.Snapshot state(AchievementKey key, AchievementAwardState.Status status) {
        AchievementAwardState.Write write = new AchievementAwardState.Write(
                CATALOG.version(), status, LocalDate.of(2026, 8, 7), NOW,
                AchievementEvidence.Kind.GAME_RESULT, "result:" + key.value(),
                status == AchievementAwardState.Status.INVALIDATED ? Optional.of(NOW) : Optional.empty());
        return new AchievementAwardState.Snapshot(
                new AchievementAwardState.Key(1, 7, key), write,
                AchievementAwardState.LockVersion.initial(), NOW, NOW);
    }
}
