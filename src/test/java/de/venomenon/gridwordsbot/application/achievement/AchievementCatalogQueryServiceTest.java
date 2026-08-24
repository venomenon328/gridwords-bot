package de.venomenon.gridwordsbot.application.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.achievement.AchievementCategory;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinition;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementScope;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.port.in.AchievementCatalogQueryUseCase;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AchievementCatalogQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private static final AchievementDefinitionCatalog CATALOG = AchievementDefinitionCatalog.achievementsV2();

    @Test
    void defaultQueryReturnsEveryCatalogEntryInCatalogOrderWithOnlyActiveAwardsChecked() {
        AchievementAwardStateStore awards = mock(AchievementAwardStateStore.class);
        var first = CATALOG.definitions().get(0);
        var second = CATALOG.definitions().get(1);
        when(awards.findAll(11, 22)).thenReturn(List.of(
                snapshot(first, AchievementAwardState.Status.ACTIVE),
                snapshot(second, AchievementAwardState.Status.INVALIDATED)));

        var result = service(awards).query(new AchievementCatalogQueryUseCase.Query(11, 22));

        assertThat(result.entries()).hasSize(62);
        assertThat(result.entries()).extracting(entry -> entry.key())
                .containsExactlyElementsOf(CATALOG.definitions().stream().map(AchievementDefinition::key).toList());
        assertThat(result.entries().get(0).achieved()).isTrue();
        assertThat(result.entries().get(1).achieved()).isFalse();
        assertThat(result.entries().stream().skip(2)).allMatch(entry -> !entry.achieved());
        verify(awards).findAll(11, 22);
    }

    @Test
    void gameFiltersUseExactTypedScopesAndGlobalAppearsOnlyInAll() {
        AchievementAwardStateStore awards = mock(AchievementAwardStateStore.class);
        when(awards.findAll(11, 22)).thenReturn(CATALOG.definitions().stream()
                .map(definition -> snapshot(definition, AchievementAwardState.Status.ACTIVE)).toList());
        AchievementCatalogQueryService service = service(awards);

        var all = service.query(query(AchievementCatalogQueryUseCase.GameFilter.ALL));
        var grid = service.query(query(AchievementCatalogQueryUseCase.GameFilter.GRIDWORDS));
        var quad = service.query(query(AchievementCatalogQueryUseCase.GameFilter.QUADWORDS));
        var cross = service.query(query(AchievementCatalogQueryUseCase.GameFilter.CROSS_GAME));

        assertThat(all.entries()).extracting(entry -> entry.scope()).contains(AchievementScope.GLOBAL);
        assertThat(grid.entries()).isNotEmpty().allSatisfy(entry -> assertThat(entry.scope()).isEqualTo(AchievementScope.GRIDWORDS));
        assertThat(quad.entries()).isNotEmpty().allSatisfy(entry -> assertThat(entry.scope()).isEqualTo(AchievementScope.QUADWORDS));
        assertThat(cross.entries()).isNotEmpty().allSatisfy(entry -> assertThat(entry.scope()).isEqualTo(AchievementScope.CROSS_GAME));
        assertThat(grid.entries()).extracting(entry -> entry.scope()).doesNotContain(AchievementScope.GLOBAL);
        assertThat(quad.entries()).extracting(entry -> entry.scope()).doesNotContain(AchievementScope.GLOBAL);
        assertThat(cross.entries()).extracting(entry -> entry.scope()).doesNotContain(AchievementScope.GLOBAL);
    }

    @Test
    void categoryFiltersMapExactlyToCatalogCategories() {
        AchievementAwardStateStore awards = mock(AchievementAwardStateStore.class);
        when(awards.findAll(11, 22)).thenReturn(List.of());
        AchievementCatalogQueryService service = service(awards);

        assertCategory(service, AchievementCatalogQueryUseCase.CategoryFilter.EXPERIENCE, AchievementCategory.EXPERIENCE);
        assertCategory(service, AchievementCatalogQueryUseCase.CategoryFilter.RELIABILITY, AchievementCategory.RELIABILITY);
        assertCategory(service, AchievementCatalogQueryUseCase.CategoryFilter.PERFORMANCE, AchievementCategory.PERFORMANCE);
        assertCategory(service, AchievementCatalogQueryUseCase.CategoryFilter.SPECIAL, AchievementCategory.SPECIAL);
    }

    @Test
    void statusOpenIncludesMissingAndInvalidatedWhileAchievedIncludesOnlyActive() {
        AchievementAwardStateStore awards = mock(AchievementAwardStateStore.class);
        AchievementDefinition active = CATALOG.definitions().get(0);
        AchievementDefinition invalidated = CATALOG.definitions().get(1);
        when(awards.findAll(11, 22)).thenReturn(List.of(
                snapshot(active, AchievementAwardState.Status.ACTIVE),
                snapshot(invalidated, AchievementAwardState.Status.INVALIDATED)));
        AchievementCatalogQueryService service = service(awards);

        var achieved = service.query(new AchievementCatalogQueryUseCase.Query(
                11, 22,
                AchievementCatalogQueryUseCase.GameFilter.ALL,
                AchievementCatalogQueryUseCase.CategoryFilter.ALL,
                AchievementCatalogQueryUseCase.StatusFilter.ACHIEVED));
        var open = service.query(new AchievementCatalogQueryUseCase.Query(
                11, 22,
                AchievementCatalogQueryUseCase.GameFilter.ALL,
                AchievementCatalogQueryUseCase.CategoryFilter.ALL,
                AchievementCatalogQueryUseCase.StatusFilter.OPEN));

        assertThat(achieved.entries()).extracting(entry -> entry.key()).containsExactly(active.key());
        assertThat(open.entries()).hasSize(CATALOG.definitions().size() - 1);
        assertThat(open.entries()).extracting(entry -> entry.key())
                .contains(invalidated.key())
                .doesNotContain(active.key());
    }

    @Test
    void combinedFiltersPreserveCatalogOrder() {
        AchievementAwardStateStore awards = mock(AchievementAwardStateStore.class);
        AchievementDefinition active = CATALOG.definitions().stream()
                .filter(definition -> definition.scope() == AchievementScope.GRIDWORDS)
                .filter(definition -> definition.category() == AchievementCategory.EXPERIENCE)
                .findFirst().orElseThrow();
        when(awards.findAll(11, 22)).thenReturn(List.of(snapshot(active, AchievementAwardState.Status.ACTIVE)));
        AchievementCatalogQueryService service = service(awards);

        var result = service.query(new AchievementCatalogQueryUseCase.Query(
                11, 22,
                AchievementCatalogQueryUseCase.GameFilter.GRIDWORDS,
                AchievementCatalogQueryUseCase.CategoryFilter.EXPERIENCE,
                AchievementCatalogQueryUseCase.StatusFilter.OPEN));
        var expected = CATALOG.definitions().stream()
                .filter(definition -> definition.scope() == AchievementScope.GRIDWORDS)
                .filter(definition -> definition.category() == AchievementCategory.EXPERIENCE)
                .filter(definition -> !definition.key().equals(active.key()))
                .map(AchievementDefinition::key)
                .toList();

        assertThat(expected).isNotEmpty();
        assertThat(result.entries()).extracting(entry -> entry.key()).containsExactlyElementsOf(expected);
    }

    private static void assertCategory(
            AchievementCatalogQueryService service,
            AchievementCatalogQueryUseCase.CategoryFilter filter,
            AchievementCategory expected) {
        var result = service.query(new AchievementCatalogQueryUseCase.Query(
                11, 22,
                AchievementCatalogQueryUseCase.GameFilter.ALL,
                filter,
                AchievementCatalogQueryUseCase.StatusFilter.ALL));
        assertThat(result.entries()).isNotEmpty().allSatisfy(entry -> assertThat(entry.category()).isEqualTo(expected));
        assertThat(result.entries()).hasSize((int) CATALOG.definitions().stream()
                .filter(definition -> definition.category() == expected).count());
    }

    private static AchievementCatalogQueryUseCase.Query query(AchievementCatalogQueryUseCase.GameFilter game) {
        return new AchievementCatalogQueryUseCase.Query(
                11, 22, game,
                AchievementCatalogQueryUseCase.CategoryFilter.ALL,
                AchievementCatalogQueryUseCase.StatusFilter.ALL);
    }

    private static AchievementCatalogQueryService service(AchievementAwardStateStore awards) {
        return new AchievementCatalogQueryService(awards, CATALOG);
    }

    private static AchievementAwardState.Snapshot snapshot(
            AchievementDefinition definition,
            AchievementAwardState.Status status) {
        Optional<Instant> invalidatedAt = status == AchievementAwardState.Status.INVALIDATED
                ? Optional.of(NOW) : Optional.empty();
        var write = new AchievementAwardState.Write(
                CATALOG.version(), status, LocalDate.of(2026, 8, 8), NOW,
                AchievementEvidence.Kind.GAME_RESULT, "result:" + definition.key().value(), invalidatedAt);
        return new AchievementAwardState.Snapshot(
                new AchievementAwardState.Key(11, 22, definition.key()), write,
                AchievementAwardState.LockVersion.initial(), NOW, NOW);
    }
}
