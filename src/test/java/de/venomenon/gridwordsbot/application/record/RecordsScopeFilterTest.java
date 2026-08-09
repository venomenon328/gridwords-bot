package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.DurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordLockVersion;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.StreakRecordMetric;
import de.venomenon.gridwordsbot.domain.record.StreakRecordValue;
import de.venomenon.gridwordsbot.port.in.RecordsQueryUseCase;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordsScopeFilterTest {
    private static final long GUILD = 1L;
    private static final long PLAYER = 7L;
    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

    private final RecordStateReadService states = mock(RecordStateReadService.class);
    private final RecordBootstrapReadService bootstrap = mock(RecordBootstrapReadService.class);
    private final PlayerStore players = mock(PlayerStore.class);
    private final RecordDefinitionCatalog catalog = RecordDefinitionCatalog.recordsV1();
    private final RecordsQueryService service = new RecordsQueryService(states, bootstrap, catalog, players);

    @BeforeEach
    void setUp() {
        when(bootstrap.readiness(new RecordBootstrapKey(GUILD, catalog.version())))
                .thenReturn(RecordBootstrapReadiness.READY);
        when(players.findAllPlayers()).thenReturn(List.of(new PlayerStore.StoredPlayer(
                PLAYER, "Player", true, false, false, NOW, NOW)));

        StreakRecordValue shared = new StreakRecordValue(
                8, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 8));
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(
                state(
                        "result.gridwords.fastest-solution.personal",
                        new RecordScope.Personal(PLAYER),
                        Optional.of(PLAYER),
                        new DurationRecordValue(Duration.ofSeconds(80)),
                        new RecordSourceReference.GameResult(
                                11, 0, PLAYER, GameType.GRIDWORDS, LocalDate.of(2026, 8, 8))),
                state(
                        "result.gridwords.fastest-solution.server-individual",
                        new RecordScope.ServerIndividual(),
                        Optional.of(PLAYER),
                        new DurationRecordValue(Duration.ofSeconds(75)),
                        new RecordSourceReference.GameResult(
                                12, 0, PLAYER, GameType.GRIDWORDS, LocalDate.of(2026, 8, 8))),
                state(
                        "streak.complete.shared",
                        new RecordScope.Shared(),
                        Optional.empty(),
                        shared,
                        new RecordSourceReference.StreakRun(
                                StreakRecordMetric.COMPLETE,
                                new RecordSourceReference.StreakRunOwner.Shared(),
                                shared.startDate()))));
    }

    @Test
    void scopeFilterReturnsOnlyRequestedScopeAndCombinesWithExistingFilters() {
        RecordsQueryUseCase.Ready personal = ready(service.query(query(
                RecordsQueryUseCase.GameFilter.GRIDWORDS,
                RecordsQueryUseCase.CategoryFilter.RESULTS,
                RecordsQueryUseCase.ScopeFilter.PERSONAL)));
        RecordsQueryUseCase.Ready server = ready(service.query(query(
                RecordsQueryUseCase.GameFilter.GRIDWORDS,
                RecordsQueryUseCase.CategoryFilter.RESULTS,
                RecordsQueryUseCase.ScopeFilter.SERVER_INDIVIDUAL)));
        RecordsQueryUseCase.Ready shared = ready(service.query(query(
                RecordsQueryUseCase.GameFilter.ALL,
                RecordsQueryUseCase.CategoryFilter.SERIES,
                RecordsQueryUseCase.ScopeFilter.SHARED)));

        assertThat(personal.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.scope()).isEqualTo(RecordsQueryUseCase.Scope.PERSONAL);
            assertThat(entry.definitionKey()).isEqualTo("result.gridwords.fastest-solution.personal");
        });
        assertThat(server.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.scope()).isEqualTo(RecordsQueryUseCase.Scope.SERVER_INDIVIDUAL);
            assertThat(entry.definitionKey()).isEqualTo("result.gridwords.fastest-solution.server-individual");
        });
        assertThat(shared.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.scope()).isEqualTo(RecordsQueryUseCase.Scope.SHARED);
            assertThat(entry.definitionKey()).isEqualTo("streak.complete.shared");
        });
    }

    @Test
    void foreignTargetRemainsForbiddenEvenWhenNonPersonalScopeWasRequested() {
        RecordsQueryUseCase.Result result = service.query(new RecordsQueryUseCase.Query(
                GUILD,
                PLAYER,
                Optional.of(99L),
                false,
                RecordsQueryUseCase.GameFilter.ALL,
                RecordsQueryUseCase.CategoryFilter.ALL,
                RecordsQueryUseCase.ScopeFilter.SERVER_INDIVIDUAL));

        assertThat(result).isInstanceOf(RecordsQueryUseCase.Forbidden.class);
        verify(bootstrap, never()).readiness(any());
        verify(states, never()).list(anyLong(), any());
        verify(players, never()).findAllPlayers();
    }

    private RecordsQueryUseCase.Query query(
            RecordsQueryUseCase.GameFilter game,
            RecordsQueryUseCase.CategoryFilter category,
            RecordsQueryUseCase.ScopeFilter scope) {
        return new RecordsQueryUseCase.Query(
                GUILD, PLAYER, Optional.empty(), false, game, category, scope);
    }

    private static RecordsQueryUseCase.Ready ready(RecordsQueryUseCase.Result result) {
        assertThat(result).isInstanceOf(RecordsQueryUseCase.Ready.class);
        return (RecordsQueryUseCase.Ready) result;
    }

    private static RecordStateSnapshot state(
            String definitionKey,
            RecordScope scope,
            Optional<Long> holder,
            de.venomenon.gridwordsbot.domain.record.RecordValue value,
            RecordSourceReference source) {
        return new RecordStateSnapshot(
                new RecordStateKey(
                        GUILD,
                        new RecordDefinitionKey(definitionKey),
                        RecordDefinitionVersion.RECORDS_V1,
                        scope),
                holder,
                value,
                source,
                false,
                RecordLockVersion.initial(),
                NOW,
                NOW);
    }
}
