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
    void unauthorizedForeignTargetIsRejectedBeforeAnyReadModelOrPlayerAccess() {
        assertThat(service.query(query(7, Optional.of(99L), false, RecordsQueryUseCase.GameFilter.ALL)))
                .isInstanceOf(RecordsQueryUseCase.Forbidden.class);

        verify(bootstrap, never()).readiness(any());
        verify(states, never()).list(any(Long.class), any());
        verify(players, never()).findAllPlayers();
        verifyNoPlayerWrites();
    }

    @Test
    void bootstrapNotReadyReturnsInitializationStateInsteadOfPartialOrEmptyRecords() {
        when(bootstrap.readiness(new RecordBootstrapKey(GUILD, catalog.version())))
                .thenReturn(RecordBootstrapReadiness.IN_PROGRESS);

        assertThat(service.query(query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.ALL)))
                .isInstanceOf(RecordsQueryUseCase.Unavailable.class);

        verify(states, never()).list(any(Long.class), any());
        verify(players, never()).findAllPlayers();
    }

    @Test
    void defaultViewUsesCallerPersonalScopeAndAlsoContainsGlobalScopes() {
        RecordStateSnapshot personal = resultState(
                "result.gridwords.fastest-solution.personal", new RecordScope.Personal(7), Optional.of(7L),
                new DurationRecordValue(Duration.ofSeconds(74)),
                new RecordSourceReference.GameResult(1, 1, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6)));
        RecordStateSnapshot server = resultState(
                "result.gridwords.fastest-solution.server-individual", new RecordScope.ServerIndividual(), Optional.of(99L),
                new DurationRecordValue(Duration.ofSeconds(70)),
                new RecordSourceReference.GameResult(2, 0, 99, GameType.GRIDWORDS, LocalDate.of(2026, 8, 5)));
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(personal, server));
        when(players.findAllPlayers()).thenReturn(List.of(player(7, "Tobias", true), player(99, "Georgia", false)));

        RecordsQueryUseCase.Ready result = ready(service.query(
                query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.ALL)));

        assertThat(result.entries()).extracting(RecordsQueryUseCase.Entry::scope)
                .contains(RecordsQueryUseCase.Scope.PERSONAL,
                        RecordsQueryUseCase.Scope.SERVER_INDIVIDUAL, RecordsQueryUseCase.Scope.SHARED);
        assertThat(result.entries().stream()
                .filter(entry -> entry.definitionKey().equals("result.gridwords.fastest-solution.personal"))
                .findFirst().orElseThrow().holderDisplay()).contains("Tobias");
        assertThat(result.entries().stream()
                .filter(entry -> entry.definitionKey().equals("result.gridwords.fastest-solution.server-individual"))
                .findFirst().orElseThrow().holderDisplay()).contains("Georgia");
        verifyNoPlayerWrites();
    }

    @Test
    void administratorTargetChangesOnlyPersonalScopeAndKeepsGlobalState() {
        RecordStateSnapshot targetPersonal = resultState(
                "result.gridwords.fastest-solution.personal", new RecordScope.Personal(99), Optional.of(99L),
                new DurationRecordValue(Duration.ofSeconds(74)),
                new RecordSourceReference.GameResult(1, 1, 99, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6)));
        RecordStateSnapshot server = resultState(
                "result.gridwords.fastest-solution.server-individual", new RecordScope.ServerIndividual(), Optional.of(7L),
                new DurationRecordValue(Duration.ofSeconds(60)),
                new RecordSourceReference.GameResult(2, 0, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 5)));
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(targetPersonal, server));
        when(players.findAllPlayers()).thenReturn(List.of(player(7, "Tobias", true), player(99, "Georgia", false)));

        RecordsQueryUseCase.Ready result = ready(service.query(
                query(7, Optional.of(99L), true, RecordsQueryUseCase.GameFilter.ALL)));

        assertThat(result.entries().stream()
                .filter(entry -> entry.definitionKey().equals("result.gridwords.fastest-solution.personal"))
                .findFirst().orElseThrow().holderDisplay()).contains("Georgia");
        assertThat(result.entries().stream()
                .filter(entry -> entry.definitionKey().equals("result.gridwords.fastest-solution.server-individual"))
                .findFirst().orElseThrow().holderDisplay()).contains("Tobias");
    }

    @Test
    void gameFilterKeepsGenericSeriesButExcludesOtherGameSpecificDefinitions() {
        RecordsQueryUseCase.Ready grid = ready(service.query(
                query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.GRIDWORDS)));

        assertThat(grid.entries()).anySatisfy(entry -> {
            assertThat(entry.metricSlug()).isEqualTo("activity");
            assertThat(entry.game()).isEmpty();
        });
        assertThat(grid.entries()).anySatisfy(entry -> assertThat(entry.metricSlug()).isEqualTo("gridwords-solved"));
        assertThat(grid.entries()).noneSatisfy(entry -> assertThat(entry.metricSlug()).isEqualTo("quadwords-solved"));
        assertThat(grid.entries()).noneSatisfy(entry ->
                assertThat(entry.game()).contains(GameType.QUADWORDS));
    }

    @Test
    void sharedStateHasNoIndividualHolderAndUnknownServerHolderUsesNeutralFallback() {
        StreakRecordValue value = new StreakRecordValue(5, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5));
        RecordStateSnapshot shared = resultState(
                "streak.gridwords-solved.shared", new RecordScope.Shared(), Optional.empty(), value,
                new RecordSourceReference.StreakRun(StreakRecordMetric.GRIDWORDS_SOLVED,
                        new RecordSourceReference.StreakRunOwner.Shared(), value.startDate()));
        RecordStateSnapshot unknownServer = resultState(
                "result.quadwords.fastest-solution.server-individual", new RecordScope.ServerIndividual(), Optional.of(123L),
                new DurationRecordValue(Duration.ofSeconds(80)),
                new RecordSourceReference.GameResult(3, 0, 123, GameType.QUADWORDS, LocalDate.of(2026, 8, 5)));
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(shared, unknownServer));

        RecordsQueryUseCase.Ready result = ready(service.query(
                query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.ALL)));

        assertThat(result.entries().stream()
                .filter(entry -> entry.definitionKey().equals("streak.gridwords-solved.shared"))
                .findFirst().orElseThrow().holderDisplay()).isEmpty();
        assertThat(result.entries().stream()
                .filter(entry -> entry.definitionKey().equals("result.quadwords.fastest-solution.server-individual"))
                .findFirst().orElseThrow().holderDisplay()).contains("Ehemaliger Spieler");
    }

    private void verifyNoPlayerWrites() {
        verify(players, never()).upsert(any());
        verify(players, never()).synchronizeProfile(any());
        verify(players, never()).activate(any());
        verify(players, never()).deactivate(any());
        verify(players, never()).activateGames(any());
        verify(players, never()).deactivateGames(any());
    }

    private static PlayerStore.StoredPlayer player(long id, String display, boolean active) {
        return new PlayerStore.StoredPlayer(id, display, active, false, false, NOW, NOW);
    }

    private static RecordStateSnapshot resultState(
            String definitionKey, RecordScope scope, Optional<Long> holder,
            de.venomenon.gridwordsbot.domain.record.RecordValue value, RecordSourceReference source) {
        return new RecordStateSnapshot(
                new RecordStateKey(GUILD, new RecordDefinitionKey(definitionKey), RecordDefinitionVersion.RECORDS_V1, scope),
                holder, value, source, false, RecordLockVersion.initial(), NOW, NOW);
    }

    private static RecordsQueryUseCase.Query query(
            long requester, Optional<Long> target, boolean administrator, RecordsQueryUseCase.GameFilter game) {
        return new RecordsQueryUseCase.Query(GUILD, requester, target, administrator, game);
    }

    private static RecordsQueryUseCase.Ready ready(RecordsQueryUseCase.Result result) {
        assertThat(result).isInstanceOf(RecordsQueryUseCase.Ready.class);
        return (RecordsQueryUseCase.Ready) result;
    }
}
