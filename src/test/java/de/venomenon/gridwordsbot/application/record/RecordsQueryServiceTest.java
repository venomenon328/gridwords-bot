package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

class RecordsQueryServiceTest {
    private static final long GUILD = 1L;
    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");
    private final RecordStateReadService states = mock(RecordStateReadService.class);
    private final RecordBootstrapReadService bootstrap = mock(RecordBootstrapReadService.class);
    private final PlayerStore players = mock(PlayerStore.class);
    private final RecordDefinitionCatalog catalog = RecordDefinitionCatalog.recordsV1();
    private final RecordsQueryService service = new RecordsQueryService(states, bootstrap, catalog, players);

    @BeforeEach
    void ready() {
        when(bootstrap.readiness(new RecordBootstrapKey(GUILD, catalog.version())))
                .thenReturn(RecordBootstrapReadiness.READY);
        when(states.list(GUILD, catalog.version())).thenReturn(List.of());
        when(players.findAllPlayers()).thenReturn(List.of());
    }

    @Test
    void bootstrapNotReadyReturnsNoPartialState() {
        when(bootstrap.readiness(new RecordBootstrapKey(GUILD, catalog.version())))
                .thenReturn(RecordBootstrapReadiness.IN_PROGRESS);

        assertThat(service.query(query(7, Optional.empty(), RecordsQueryUseCase.GameFilter.ALL,
                RecordsQueryUseCase.ScopeFilter.ALL, RecordsQueryUseCase.CategoryFilter.ALL)))
                .isInstanceOf(RecordsQueryUseCase.Unavailable.class);

        verify(states, never()).list(any(Long.class), any());
        verify(players, never()).findAllPlayers();
    }

    @Test
    void gridwordsPersonalResultsUseCallerAndKeepEmptyDefinitionsVisible() {
        RecordsQueryUseCase.Ready result = ready(service.query(query(7, Optional.empty(),
                RecordsQueryUseCase.GameFilter.GRIDWORDS,
                RecordsQueryUseCase.ScopeFilter.PERSONAL,
                RecordsQueryUseCase.CategoryFilter.RESULTS)));

        assertThat(result.entries()).hasSize(3).allSatisfy(entry -> {
            assertThat(entry.game()).contains(GameType.GRIDWORDS);
            assertThat(entry.category()).isEqualTo(RecordsQueryUseCase.Category.RESULTS);
            assertThat(entry.scope()).isEqualTo(RecordsQueryUseCase.Scope.PERSONAL);
            assertThat(entry.value()).isEmpty();
            assertThat(entry.source()).isEmpty();
        });
    }

    @Test
    void explicitUserSelectsTheirPersonalStateAndKeepsInactiveProfileDisplay() {
        RecordStateSnapshot personal = resultState(
                "result.gridwords.fastest-solution.personal",
                new RecordScope.Personal(99),
                Optional.of(99L),
                new DurationRecordValue(Duration.ofSeconds(74)),
                new RecordSourceReference.GameResult(1, 1, 99, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6)));
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(personal));
        when(players.findAllPlayers()).thenReturn(List.of(
                new PlayerStore.StoredPlayer(99, "Georgia", false, false, false, NOW, NOW)));

        RecordsQueryUseCase.Ready result = ready(service.query(query(7, Optional.of(99L),
                RecordsQueryUseCase.GameFilter.GRIDWORDS,
                RecordsQueryUseCase.ScopeFilter.PERSONAL,
                RecordsQueryUseCase.CategoryFilter.RESULTS)));

        RecordsQueryUseCase.Entry fastest = result.entries().stream()
                .filter(entry -> entry.metricSlug().equals("fastest-solution")).findFirst().orElseThrow();
        assertThat(fastest.holderDisplay()).contains("Georgia");
        assertThat(fastest.value()).contains(new DurationRecordValue(Duration.ofSeconds(74)));
    }

    @Test
    void gameFilterExcludesGenericSeriesAndSharedScopeNeverInventsAHolder() {
        StreakRecordValue value = new StreakRecordValue(5, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5));
        RecordStateSnapshot server = resultState(
                "streak.gridwords-solved.server-individual",
                new RecordScope.ServerIndividual(), Optional.of(99L), value,
                new RecordSourceReference.StreakRun(StreakRecordMetric.GRIDWORDS_SOLVED,
                        new RecordSourceReference.StreakRunOwner.Player(99), value.startDate()));
        RecordStateSnapshot shared = resultState(
                "streak.gridwords-solved.shared",
                new RecordScope.Shared(), Optional.empty(), value,
                new RecordSourceReference.StreakRun(StreakRecordMetric.GRIDWORDS_SOLVED,
                        new RecordSourceReference.StreakRunOwner.Shared(), value.startDate()));
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(server, shared));
        when(players.findAllPlayers()).thenReturn(List.of(
                new PlayerStore.StoredPlayer(99, "Georgia", false, false, false, NOW, NOW)));

        RecordsQueryUseCase.Ready result = ready(service.query(query(7, Optional.empty(),
                RecordsQueryUseCase.GameFilter.GRIDWORDS,
                RecordsQueryUseCase.ScopeFilter.ALL,
                RecordsQueryUseCase.CategoryFilter.SERIES)));

        assertThat(result.entries()).allSatisfy(entry ->
                assertThat(entry.metricSlug()).isIn("gridwords-solved", "gridwords-drought"));
        assertThat(result.entries().stream()
                .filter(entry -> entry.definitionKey().equals("streak.gridwords-solved.server-individual"))
                .findFirst().orElseThrow().holderDisplay()).contains("Georgia");
        assertThat(result.entries().stream()
                .filter(entry -> entry.definitionKey().equals("streak.gridwords-solved.shared"))
                .findFirst().orElseThrow().holderDisplay()).isEmpty();
    }

    @Test
    void missingHolderProfileUsesNeutralFallback() {
        RecordStateSnapshot server = resultState(
                "result.quadwords.fastest-solution.server-individual",
                new RecordScope.ServerIndividual(), Optional.of(123L),
                new DurationRecordValue(Duration.ofSeconds(80)),
                new RecordSourceReference.GameResult(2, 0, 123, GameType.QUADWORDS, LocalDate.of(2026, 8, 5)));
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(server));

        RecordsQueryUseCase.Ready result = ready(service.query(query(7, Optional.empty(),
                RecordsQueryUseCase.GameFilter.QUADWORDS,
                RecordsQueryUseCase.ScopeFilter.SERVER_INDIVIDUAL,
                RecordsQueryUseCase.CategoryFilter.RESULTS)));

        assertThat(result.entries().stream().filter(entry -> entry.value().isPresent()).findFirst().orElseThrow()
                .holderDisplay()).contains("Ehemaliger Spieler");
    }

    private static RecordStateSnapshot resultState(
            String definitionKey, RecordScope scope, Optional<Long> holder,
            de.venomenon.gridwordsbot.domain.record.RecordValue value, RecordSourceReference source) {
        return new RecordStateSnapshot(
                new RecordStateKey(GUILD, new RecordDefinitionKey(definitionKey), RecordDefinitionVersion.RECORDS_V1, scope),
                holder, value, source, false, RecordLockVersion.initial(), NOW, NOW);
    }

    private static RecordsQueryUseCase.Query query(
            long requester, Optional<Long> personal, RecordsQueryUseCase.GameFilter game,
            RecordsQueryUseCase.ScopeFilter scope, RecordsQueryUseCase.CategoryFilter category) {
        return new RecordsQueryUseCase.Query(GUILD, requester, personal, game, scope, category);
    }

    private static RecordsQueryUseCase.Ready ready(RecordsQueryUseCase.Result result) {
        assertThat(result).isInstanceOf(RecordsQueryUseCase.Ready.class);
        return (RecordsQueryUseCase.Ready) result;
    }
}
