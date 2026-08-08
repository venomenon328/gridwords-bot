package de.venomenon.gridwordsbot.application.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
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
    private static final AchievementDefinitionCatalog CATALOG = AchievementDefinitionCatalog.achievementsV1();

    @Test
    void returnsEveryCatalogEntryInCatalogOrderWithOnlyActiveAwardsChecked() {
        AchievementAwardStateStore awards = mock(AchievementAwardStateStore.class);
        var first = CATALOG.definitions().get(0);
        var second = CATALOG.definitions().get(1);
        when(awards.findAll(11, 22)).thenReturn(List.of(
                snapshot(first.key(), AchievementAwardState.Status.ACTIVE, Optional.empty()),
                snapshot(second.key(), AchievementAwardState.Status.INVALIDATED, Optional.of(NOW))));

        var result = new AchievementCatalogQueryService(awards, CATALOG)
                .query(new AchievementCatalogQueryUseCase.Query(11, 22));

        assertThat(result.entries()).hasSize(60);
        assertThat(result.entries()).extracting(entry -> entry.key())
                .containsExactlyElementsOf(CATALOG.definitions().stream().map(definition -> definition.key()).toList());
        assertThat(result.entries().get(0).achieved()).isTrue();
        assertThat(result.entries().get(1).achieved()).isFalse();
        assertThat(result.entries().stream().skip(2)).allMatch(entry -> !entry.achieved());
        verify(awards).findAll(11, 22);
    }

    private static AchievementAwardState.Snapshot snapshot(
            de.venomenon.gridwordsbot.domain.achievement.AchievementKey key,
            AchievementAwardState.Status status,
            Optional<Instant> invalidatedAt) {
        var write = new AchievementAwardState.Write(
                CATALOG.version(), status, LocalDate.of(2026, 8, 8), NOW,
                AchievementEvidence.Kind.GAME_RESULT, "result:" + key.value(), invalidatedAt);
        return new AchievementAwardState.Snapshot(
                new AchievementAwardState.Key(11, 22, key), write,
                AchievementAwardState.LockVersion.initial(), NOW, NOW);
    }
}
