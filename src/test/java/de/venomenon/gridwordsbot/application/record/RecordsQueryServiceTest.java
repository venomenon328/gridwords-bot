package de.venomenon.gridwordsbot.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
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
        verify(states, never()).list(anyLong(), any());
        verify(players, never()).findAllPlayers();
        verifyNoPlayerWrites();
    }

    @Test
    void bootstrapNotReadyReturnsInitializationStateInsteadOfPartialOrEmptyRecords() {
        when(bootstrap.readiness(new RecordBootstrapKey(GUILD, catalog.version())))
                .thenReturn(RecordBootstrapReadiness.IN_PROGRESS);

        assertThat(service.query(query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.ALL)))
                .isInstanceOf(RecordsQueryUseCase.Unavailable.class);

        verify(states, never()).list(anyLong(), any());
        verify(players, never()).findAllPlayers();
    }

    @Test
    void readyWithoutStatesReturnsNeutralEmptyResult() {
        RecordsQueryUseCase.Ready result = ready(service.query(
                query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.ALL)));

        assertThat(result.entries()).isEmpty();
        verify(states).list(GUILD, catalog.version());
        verify(players).findAllPlayers();
        verifyNoPlayerWrites();
    }

    @Test
    void defaultViewUsesCallerPersonalScopeAndAlsoContainsGlobalScopes() {
        StreakRecordValue sharedValue = streak(8, LocalDate.of(2026, 7, 30));
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(
                state("result.gridwords.fastest-solution.personal", new RecordScope.Personal(7), Optional.of(7L),
                        new DurationRecordValue(Duration.ofSeconds(74)),
                        new RecordSourceReference.GameResult(1, 1, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6))),
                state("result.gridwords.fastest-solution.server-individual", new RecordScope.ServerIndividual(),
                        Optional.of(99L), new DurationRecordValue(Duration.ofSeconds(70)),
                        new RecordSourceReference.GameResult(2, 0, 99, GameType.GRIDWORDS, LocalDate.of(2026, 8, 5))),
                state("streak.complete.shared", new RecordScope.Shared(), Optional.empty(), sharedValue,
                        new RecordSourceReference.StreakRun(StreakRecordMetric.COMPLETE,
                                new RecordSourceReference.StreakRunOwner.Shared(), sharedValue.startDate()))));
        when(players.findAllPlayers()).thenReturn(List.of(player(7, "Tobias", true), player(99, "Georgia", false)));

        RecordsQueryUseCase.Ready result = ready(service.query(
                query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.ALL)));

        assertThat(result.entries()).extracting(RecordsQueryUseCase.Entry::scope)
                .containsExactlyInAnyOrder(
                        RecordsQueryUseCase.Scope.PERSONAL,
                        RecordsQueryUseCase.Scope.SERVER_INDIVIDUAL,
                        RecordsQueryUseCase.Scope.SHARED);
        assertThat(present(result, "result.gridwords.fastest-solution.personal").holderDisplay()).contains("Tobias");
        assertThat(present(result, "result.gridwords.fastest-solution.server-individual").holderDisplay())
                .contains("Georgia");
        assertThat(present(result, "streak.complete.shared").holderDisplay()).isEmpty();
        verifyNoPlayerWrites();
    }

    @Test
    void administratorTargetChangesOnlyPersonalScopeAndKeepsGlobalStateIncludingTieBreakContext() {
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(
                state("result.gridwords.fastest-solution.personal", new RecordScope.Personal(99), Optional.of(99L),
                        new DurationRecordValue(Duration.ofSeconds(74)),
                        new RecordSourceReference.GameResult(1, 1, 99, GameType.GRIDWORDS, LocalDate.of(2026, 8, 6))),
                state("result.gridwords.fewest-attempts.server-individual", new RecordScope.ServerIndividual(),
                        Optional.of(7L), new AttemptsDurationRecordValue(2, Duration.ofSeconds(90)),
                        new RecordSourceReference.GameResult(2, 0, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 5)))));
        when(players.findAllPlayers()).thenReturn(List.of(player(7, "Tobias", true), player(99, "Georgia", false)));

        RecordsQueryUseCase.Ready result = ready(service.query(
                query(7, Optional.of(99L), true, RecordsQueryUseCase.GameFilter.ALL)));

        assertThat(present(result, "result.gridwords.fastest-solution.personal").holderDisplay()).contains("Georgia");
        RecordsQueryUseCase.Entry server = present(result, "result.gridwords.fewest-attempts.server-individual");
        assertThat(server.holderDisplay()).contains("Tobias");
        assertThat(server.value()).contains(new AttemptsDurationRecordValue(2, Duration.ofSeconds(90)));
    }

    @Test
    void gameFilterKeepsGameIndependentSeriesButExcludesOtherGameSpecificDefinitions() {
        StreakRecordValue generic = streak(8, LocalDate.of(2026, 7, 30));
        StreakRecordValue grid = streak(7, LocalDate.of(2026, 7, 31));
        StreakRecordValue quad = streak(6, LocalDate.of(2026, 8, 1));
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(
                personalStreak("streak.activity.personal", 7, StreakRecordMetric.ACTIVITY, generic),
                personalStreak("streak.gridwords-solved.personal", 7, StreakRecordMetric.GRIDWORDS_SOLVED, grid),
                personalStreak("streak.quadwords-solved.personal", 7, StreakRecordMetric.QUADWORDS_SOLVED, quad)));

        RecordsQueryUseCase.Ready gridResult = ready(service.query(
                query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.GRIDWORDS)));
        RecordsQueryUseCase.Ready quadResult = ready(service.query(
                query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.QUADWORDS)));

        assertThat(gridResult.entries()).extracting(RecordsQueryUseCase.Entry::metricSlug)
                .contains("activity", "gridwords-solved")
                .doesNotContain("quadwords-solved");
        assertThat(quadResult.entries()).extracting(RecordsQueryUseCase.Entry::metricSlug)
                .contains("activity", "quadwords-solved")
                .doesNotContain("gridwords-solved");
    }

    @Test
    void positiveAndNegativeSeriesKeepIntervalsAndRunningState() {
        StreakRecordValue solved = streak(7, LocalDate.of(2026, 7, 31));
        StreakRecordValue drought = streak(3, LocalDate.of(2026, 8, 4));
        StreakRecordValue imperfect = streak(8, LocalDate.of(2026, 7, 30));
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(
                personalStreak("streak.gridwords-solved.personal", 7, StreakRecordMetric.GRIDWORDS_SOLVED, solved),
                personalStreak("streak.gridwords-drought.personal", 7, StreakRecordMetric.GRIDWORDS_DROUGHT, drought),
                state("streak.without-perfect-day.personal", new RecordScope.Personal(7), Optional.of(7L), imperfect,
                        new RecordSourceReference.StreakRun(StreakRecordMetric.WITHOUT_PERFECT_DAY,
                                new RecordSourceReference.StreakRunOwner.Player(7), imperfect.startDate()), true)));

        RecordsQueryUseCase.Ready result = ready(service.query(
                query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.GRIDWORDS)));

        assertThat(present(result, "streak.gridwords-solved.personal").value()).contains(solved);
        assertThat(present(result, "streak.gridwords-drought.personal").value()).contains(drought);
        RecordsQueryUseCase.Entry running = present(result, "streak.without-perfect-day.personal");
        assertThat(running.value()).contains(imperfect);
        assertThat(running.running()).isTrue();
    }

    @Test
    void inactiveHolderRemainsVisibleAndUnknownServerHolderUsesNeutralFallback() {
        when(states.list(GUILD, catalog.version())).thenReturn(List.of(
                state("result.quadwords.fastest-solution.server-individual", new RecordScope.ServerIndividual(),
                        Optional.of(99L), new DurationRecordValue(Duration.ofSeconds(80)),
                        new RecordSourceReference.GameResult(3, 0, 99, GameType.QUADWORDS, LocalDate.of(2026, 8, 5))),
                state("result.quadwords.slowest-successful-solution.server-individual", new RecordScope.ServerIndividual(),
                        Optional.of(123L), new DurationRecordValue(Duration.ofSeconds(600)),
                        new RecordSourceReference.GameResult(4, 0, 123, GameType.QUADWORDS, LocalDate.of(2026, 8, 4)))));
        when(players.findAllPlayers()).thenReturn(List.of(player(99, "Georgia", false)));

        RecordsQueryUseCase.Ready result = ready(service.query(
                query(7, Optional.empty(), false, RecordsQueryUseCase.GameFilter.QUADWORDS)));

        assertThat(present(result, "result.quadwords.fastest-solution.server-individual").holderDisplay())
                .contains("Georgia");
        assertThat(present(result, "result.quadwords.slowest-successful-solution.server-individual").holderDisplay())
                .contains("Ehemaliger Spieler");
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

    private static StreakRecordValue streak(int length, LocalDate start) {
        return new StreakRecordValue(length, start, start.plusDays(length - 1L));
    }

    private static RecordStateSnapshot personalStreak(
            String key, long playerId, StreakRecordMetric metric, StreakRecordValue value) {
        return state(key, new RecordScope.Personal(playerId), Optional.of(playerId), value,
                new RecordSourceReference.StreakRun(
                        metric, new RecordSourceReference.StreakRunOwner.Player(playerId), value.startDate()));
    }

    private static RecordStateSnapshot state(
            String definitionKey, RecordScope scope, Optional<Long> holder,
            de.venomenon.gridwordsbot.domain.record.RecordValue value, RecordSourceReference source) {
        return state(definitionKey, scope, holder, value, source, false);
    }

    private static RecordStateSnapshot state(
            String definitionKey, RecordScope scope, Optional<Long> holder,
            de.venomenon.gridwordsbot.domain.record.RecordValue value, RecordSourceReference source, boolean running) {
        return new RecordStateSnapshot(
                new RecordStateKey(GUILD, new RecordDefinitionKey(definitionKey), RecordDefinitionVersion.RECORDS_V1, scope),
                holder, value, source, running, RecordLockVersion.initial(), NOW, NOW);
    }

    private static RecordsQueryUseCase.Query query(
            long requester, Optional<Long> target, boolean administrator, RecordsQueryUseCase.GameFilter game) {
        return new RecordsQueryUseCase.Query(GUILD, requester, target, administrator, game);
    }

    private static RecordsQueryUseCase.Entry present(RecordsQueryUseCase.Ready result, String key) {
        return result.entries().stream().filter(entry -> entry.definitionKey().equals(key)).findFirst().orElseThrow();
    }

    private static RecordsQueryUseCase.Ready ready(RecordsQueryUseCase.Result result) {
        assertThat(result).isInstanceOf(RecordsQueryUseCase.Ready.class);
        return (RecordsQueryUseCase.Ready) result;
    }
}
