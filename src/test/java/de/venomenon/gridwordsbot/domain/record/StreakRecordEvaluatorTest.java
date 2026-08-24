package de.venomenon.gridwordsbot.domain.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StreakRecordEvaluatorTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private final StreakRecordEvaluator evaluator = new StreakRecordEvaluator();

    @Test
    void sameRunCanCrossPersonalBeforeServerAndEachDefinitionOnlyOnce() {
        StreakRun candidate = run(1, START, 31, StreakRunStatus.RUNNING);
        StreakRun personalReference = run(1, START.minusDays(60), 20, StreakRunStatus.ENDED_BY_RESULT);
        StreakRun serverReference = run(2, START.minusDays(50), 27, StreakRunStatus.ENDED_BY_RESULT);

        StreakRecordEvaluationSet first = evaluator.evaluate(candidate,
                new StreakRecordHistorySnapshot(List.of(personalReference, serverReference)), Set.of(),
                RecordProcessingOrigin.LIVE_SUBMISSION);
        assertEquals(2, first.publiclyEligible().size());
        assertTrue(first.notable().stream().allMatch(e -> e.classification() == StreakRecordClassification.CROSSED));

        StreakRecordEvaluation personal = first.evaluations().stream()
                .filter(e -> e.comparisonScope() instanceof RecordScope.Personal).findFirst().orElseThrow();
        StreakRecordEvaluationSet repeated = evaluator.evaluate(candidate,
                new StreakRecordHistorySnapshot(List.of(personalReference, serverReference)),
                Set.of(personal.crossingKey().orElseThrow()), RecordProcessingOrigin.LIVE_SUBMISSION);
        assertEquals(1, repeated.notable().size());
        assertTrue(repeated.notable().getFirst().comparisonScope() instanceof RecordScope.ServerIndividual);
    }

    @Test
    void runningTieIsSilentButCompletedTieIsClassified() {
        StreakRun reference = run(1, START.minusDays(20), 10, StreakRunStatus.ENDED_BY_RESULT);
        StreakRun running = run(1, START, 10, StreakRunStatus.RUNNING);
        StreakRun completed = run(1, START, 10, StreakRunStatus.ENDED_BY_RESULT);
        StreakRecordHistorySnapshot history = new StreakRecordHistorySnapshot(List.of(reference,
                run(2, START.minusDays(40), 8, StreakRunStatus.ENDED_BY_RESULT)));

        assertTrue(evaluator.evaluate(running, history, Set.of(), RecordProcessingOrigin.LIVE_SUBMISSION)
                .notable().isEmpty());
        StreakRecordEvaluation personal = evaluator.evaluate(completed, history, Set.of(),
                        RecordProcessingOrigin.LIVE_SUBMISSION).evaluations().stream()
                .filter(e -> e.comparisonScope() instanceof RecordScope.Personal).findFirst().orElseThrow();
        assertEquals(StreakRecordClassification.TIED, personal.classification());
        assertTrue(personal.publicAnnouncementEligible());
    }

    @Test
    void appliesRelativeNearMissWithoutThreeDayCapAndRejectsFartherMiss() {
        StreakRun reference = run(2, START.minusDays(100), 80, StreakRunStatus.ENDED_BY_RESULT);
        StreakRun basis = run(1, START.minusDays(20), 20, StreakRunStatus.ENDED_BY_RESULT);
        StreakRecordEvaluation near = serverEvaluation(run(1, START, 72, StreakRunStatus.ENDED_BY_RESULT),
                List.of(reference, basis));
        assertEquals(StreakRecordClassification.NEAR_MISS, near.classification());
        assertEquals(8, near.gapToReference().orElseThrow());
        assertTrue(near.publicAnnouncementEligible());

        StreakRecordEvaluation far = serverEvaluation(run(1, START, 71, StreakRunStatus.ENDED_BY_RESULT),
                List.of(reference, basis));
        assertEquals(StreakRecordClassification.NONE, far.classification());
    }

    @Test
    void minimumHistoryAndSilentOriginsOnlyControlPublicEligibility() {
        StreakRun candidate = run(1, START, 12, StreakRunStatus.RUNNING);
        StreakRun reference = run(1, START.minusDays(20), 10, StreakRunStatus.ENDED_BY_RESULT);

        StreakRecordEvaluationSet insufficient = evaluator.evaluate(candidate,
                new StreakRecordHistorySnapshot(List.of(reference)), Set.of(), RecordProcessingOrigin.LIVE_SUBMISSION);
        StreakRecordEvaluation server = insufficient.evaluations().stream()
                .filter(e -> e.comparisonScope() instanceof RecordScope.ServerIndividual).findFirst().orElseThrow();
        assertEquals(StreakRecordClassification.CROSSED, server.classification());
        assertFalse(server.publicAnnouncementEligible());

        StreakRecordEvaluation personalBootstrap = evaluator.evaluate(candidate,
                        new StreakRecordHistorySnapshot(List.of(reference)), Set.of(), RecordProcessingOrigin.BOOTSTRAP)
                .evaluations().stream().filter(e -> e.comparisonScope() instanceof RecordScope.Personal)
                .findFirst().orElseThrow();
        assertEquals(StreakRecordClassification.CROSSED, personalBootstrap.classification());
        assertFalse(personalBootstrap.publicAnnouncementEligible());
    }

    @Test
    void candidateRunIsExcludedFromItsOwnReferenceComparison() {
        StreakRun candidate = run(1, START, 12, StreakRunStatus.ENDED_BY_RESULT);
        StreakRecordEvaluationSet evaluations = evaluator.evaluate(candidate,
                new StreakRecordHistorySnapshot(List.of(candidate)), Set.of(), RecordProcessingOrigin.NORMAL_CORRECTION);
        assertTrue(evaluations.notable().isEmpty());
    }

    @Test
    void consumedHistoricalCrossingSuppressesOnlyFurtherLiveExtensionNotItsCompletion() {
        StreakRun reference = run(1, START.minusDays(20), 7, StreakRunStatus.ENDED_BY_RESULT);
        StreakRun running = run(1, START, 9, StreakRunStatus.RUNNING);
        StreakRecordEvaluation personal = evaluator.evaluate(running, new StreakRecordHistorySnapshot(List.of(reference)),
                        Set.of(), RecordProcessingOrigin.LIVE_SUBMISSION).evaluations().stream()
                .filter(evaluation -> evaluation.comparisonScope() instanceof RecordScope.Personal).findFirst().orElseThrow();
        StreakCrossingKey consumed = personal.crossingKey().orElseThrow();

        StreakRecordEvaluation suppressed = evaluator.evaluate(running, new StreakRecordHistorySnapshot(List.of(reference)),
                        Set.of(consumed), RecordProcessingOrigin.LIVE_SUBMISSION).evaluations().stream()
                .filter(evaluation -> evaluation.comparisonScope() instanceof RecordScope.Personal).findFirst().orElseThrow();
        assertEquals(StreakRecordClassification.NONE, suppressed.classification());

        StreakRecordEvaluation completed = evaluator.evaluate(run(1, START, 9, StreakRunStatus.ENDED_BY_RESULT),
                        new StreakRecordHistorySnapshot(List.of(reference)), Set.of(consumed),
                        RecordProcessingOrigin.LIVE_SUBMISSION).evaluations().stream()
                .filter(evaluation -> evaluation.comparisonScope() instanceof RecordScope.Personal).findFirst().orElseThrow();
        assertEquals(StreakRecordClassification.NEW_RECORD, completed.classification());
        assertTrue(completed.publicAnnouncementEligible());
    }

    @Test
    void evaluatesSharedRunsAgainstTheVersionedRecordsV2Definitions() {
        StreakRun reference = sharedRun(START.minusDays(20), 7, StreakRunStatus.ENDED_BY_RESULT);
        StreakRun candidate = sharedRun(START, 8, StreakRunStatus.ENDED_BY_RESULT);

        StreakRecordEvaluation evaluation = new StreakRecordEvaluator(RecordDefinitionCatalog.recordsV2())
                .evaluate(candidate, new StreakRecordHistorySnapshot(List.of(reference)), Set.of(),
                        RecordProcessingOrigin.NORMAL_CORRECTION)
                .evaluations().getFirst();

        assertEquals(RecordDefinitionVersion.RECORDS_V2, evaluation.definition().definitionVersion());
        assertEquals(StreakRecordClassification.NEW_RECORD, evaluation.classification());
    }

    private StreakRecordEvaluation serverEvaluation(StreakRun candidate, List<StreakRun> history) {
        return evaluator.evaluate(candidate, new StreakRecordHistorySnapshot(history), Set.of(),
                        RecordProcessingOrigin.NORMAL_CORRECTION).evaluations().stream()
                .filter(e -> e.comparisonScope() instanceof RecordScope.ServerIndividual).findFirst().orElseThrow();
    }

    private static StreakRun run(long player, LocalDate start, int length, StreakRunStatus status) {
        return new StreakRun(new StreakRunIdentity(StreakRecordMetric.GRIDWORDS_SOLVED,
                new RecordScope.Personal(player), start), start.plusDays(length - 1), status);
    }

    private static StreakRun sharedRun(LocalDate start, int length, StreakRunStatus status) {
        return new StreakRun(new StreakRunIdentity(StreakRecordMetric.GRIDWORDS_SOLVED,
                new RecordScope.Shared(), start), start.plusDays(length - 1), status);
    }
}
