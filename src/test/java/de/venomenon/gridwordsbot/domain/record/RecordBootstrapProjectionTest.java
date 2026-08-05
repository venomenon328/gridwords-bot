package de.venomenon.gridwordsbot.domain.record;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordBootstrapProjectionTest {
    private static final LocalDate START = LocalDate.of(2026, 7, 20);

    @Test
    void emptyCanonicalHistoryProducesNoStates() {
        assertThat(project(new RecordHistorySnapshot(List.of(), List.of()))).isEmpty();
    }

    @Test
    void mixedHistoryProjectsEveryResultAndStreakDefinitionAcrossAllScopes() {
        List<RecordHistorySnapshot.Result> results = new ArrayList<>();
        for (int day = 0; day < 10; day++) {
            for (long player : List.of(7L, 8L)) {
                boolean solved = day >= 3;
                results.add(result(100 + day * 10 + player, player, GameType.GRIDWORDS, day, solved));
                results.add(result(200 + day * 10 + player, player, GameType.QUADWORDS, day, solved));
            }
        }
        List<GameParticipationPeriod> participation = List.of(
                new GameParticipationPeriod(7, GameType.GRIDWORDS, START, START.plusDays(9)),
                new GameParticipationPeriod(7, GameType.QUADWORDS, START, START.plusDays(9)),
                new GameParticipationPeriod(8, GameType.GRIDWORDS, START, null),
                new GameParticipationPeriod(8, GameType.QUADWORDS, START, null));

        List<RecordBootstrapProjection.Candidate> candidates = project(new RecordHistorySnapshot(results, participation));

        assertThat(candidates).isNotEmpty();
        assertThat(candidates).extracting(candidate -> candidate.key().definitionKey())
                .containsAll(RecordDefinitionCatalog.recordsV1().definitions().stream()
                        .map(RecordDefinition::key).toList());
        assertThat(candidates).extracting(candidate -> candidate.key().scope().type())
                .contains(RecordScopeType.PERSONAL, RecordScopeType.SERVER_INDIVIDUAL, RecordScopeType.SHARED);
        assertThat(candidates).anySatisfy(candidate -> {
            assertThat(candidate.key().scope()).isEqualTo(new RecordScope.Personal(7));
        });
        assertThat(candidates).filteredOn(candidate -> candidate.write().source() instanceof RecordSourceReference.StreakRun)
                .allSatisfy(candidate -> assertThat(candidate.write().value()).isInstanceOf(StreakRecordValue.class));
    }

    @Test
    void equalResultValuesUseEarliestAcceptanceThenLowestResultIdAsCanonicalSource() {
        Instant earliest = Instant.parse("2026-07-20T08:00:00Z");
        List<RecordHistorySnapshot.Result> results = List.of(
                new RecordHistorySnapshot.Result(20, 0, 7, GameType.GRIDWORDS, START,
                        new ShareOutcome.Solved(2, 6), Duration.ofSeconds(40), earliest),
                new RecordHistorySnapshot.Result(10, 0, 8, GameType.GRIDWORDS, START,
                        new ShareOutcome.Solved(2, 6), Duration.ofSeconds(40), earliest));
        RecordBootstrapProjection.Candidate server = project(new RecordHistorySnapshot(results, List.of()))
                .stream().filter(candidate -> candidate.key().definitionKey()
                        .equals(new RecordDefinitionKey("result.gridwords.fewest-attempts.server-individual")))
                .findFirst().orElseThrow();

        assertThat(((RecordSourceReference.GameResult) server.write().source()).resultId()).isEqualTo(10);
    }

    private static List<RecordBootstrapProjection.Candidate> project(RecordHistorySnapshot history) {
        return new RecordBootstrapProjection(RecordDefinitionCatalog.recordsV1(), new StreakRunAnalyzer()).project(
                1, history, new StreakRunAnalysisWindow(START, START.plusDays(9), false));
    }

    private static RecordHistorySnapshot.Result result(
            long id, long player, GameType game, int day, boolean solved) {
        int maxAttempts = game == GameType.GRIDWORDS ? 6 : 9;
        return new RecordHistorySnapshot.Result(id, 0, player, game, START.plusDays(day),
                solved ? new ShareOutcome.Solved(2, maxAttempts) : new ShareOutcome.Unsolved(maxAttempts),
                Duration.ofSeconds(40 + day), Instant.parse("2026-07-20T08:00:00Z").plusSeconds(id));
    }
}
