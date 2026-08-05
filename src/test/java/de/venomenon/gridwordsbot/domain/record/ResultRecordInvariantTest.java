package de.venomenon.gridwordsbot.domain.record;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResultRecordInvariantTest {

    @Test
    void historyRequiresDistinctStableResultIds() {
        ResultRecordObservation observation = observation(1, 7);
        ResultRecordObservation correctionVersion = new ResultRecordObservation(
                1,
                2,
                7,
                GameType.GRIDWORDS,
                LocalDate.of(2026, 8, 1),
                Instant.parse("2026-08-01T12:00:01Z"),
                new ShareOutcome.Solved(2, 6),
                Duration.ofSeconds(100));

        assertThatIllegalArgumentException().isThrownBy(() ->
                new ResultRecordHistorySnapshot(List.of(observation, correctionVersion)))
                .withMessageContaining("stable result IDs");
    }

    @Test
    void personalStateRequiresTheScopedPlayerAndResultStatesRejectSharedScope() {
        ResultRecordObservation observation = observation(1, 7);
        RecordDefinition<?> definition = definition(RecordScopeType.PERSONAL);

        assertThatIllegalArgumentException().isThrownBy(() -> new ResultRecordStateSnapshot(
                definition.key(),
                definition.definitionVersion(),
                new RecordScope.Personal(8),
                observation));
        assertThatIllegalArgumentException().isThrownBy(() -> new ResultRecordStateSnapshot(
                definition.key(),
                definition.definitionVersion(),
                new RecordScope.Shared(),
                observation));
    }

    @Test
    void evaluationActionEnforcesPreviousAndResultingStateSemantics() {
        ResultRecordObservation previousSource = observation(1, 7);
        ResultRecordObservation betterSource = new ResultRecordObservation(
                2,
                0,
                7,
                GameType.GRIDWORDS,
                LocalDate.of(2026, 8, 2),
                Instant.parse("2026-08-02T12:00:00Z"),
                new ShareOutcome.Solved(1, 6),
                Duration.ofSeconds(100));
        RecordDefinition<?> definition = definition(RecordScopeType.PERSONAL);
        RecordScope scope = new RecordScope.Personal(7);
        ResultRecordStateSnapshot previous = new ResultRecordStateSnapshot(
                definition.key(), definition.definitionVersion(), scope, previousSource);
        ResultRecordStateSnapshot better = new ResultRecordStateSnapshot(
                definition.key(), definition.definitionVersion(), scope, betterSource);

        assertThatIllegalArgumentException().isThrownBy(() -> new ResultRecordEvaluation(
                definition,
                scope,
                ResultRecordEvaluationAction.INITIALIZED,
                Optional.of(previous),
                better,
                false));
        assertThatIllegalArgumentException().isThrownBy(() -> new ResultRecordEvaluation(
                definition,
                scope,
                ResultRecordEvaluationAction.UNCHANGED,
                Optional.of(previous),
                better,
                false));
        assertThatIllegalArgumentException().isThrownBy(() -> new ResultRecordEvaluation(
                definition,
                scope,
                ResultRecordEvaluationAction.IMPROVED,
                Optional.of(better),
                previous,
                true));
    }

    private static ResultRecordObservation observation(long resultId, long playerId) {
        return new ResultRecordObservation(
                resultId,
                0,
                playerId,
                GameType.GRIDWORDS,
                LocalDate.of(2026, 8, 1),
                Instant.parse("2026-08-01T12:00:00Z"),
                new ShareOutcome.Solved(2, 6),
                Duration.ofSeconds(100));
    }

    private static RecordDefinition<?> definition(RecordScopeType scopeType) {
        return RecordDefinitionCatalog.recordsV1().definitions().stream()
                .filter(candidate -> candidate.metric() == ResultRecordMetric.FEWEST_ATTEMPTS)
                .filter(candidate -> candidate.game().equals(Optional.of(GameType.GRIDWORDS)))
                .filter(candidate -> candidate.scopeType() == scopeType)
                .findFirst()
                .orElseThrow();
    }
}
