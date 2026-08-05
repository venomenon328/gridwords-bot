package de.venomenon.gridwordsbot.domain.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreakRunReconcilerTest {
    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private final StreakRunReconciler reconciler = new StreakRunReconciler();

    @Test
    void splitProducesUpdatedOriginalAndAddedSecondRun() {
        StreakRun original = run(START, 5, StreakRunStatus.ENDED_BY_RESULT);
        StreakRun shortened = run(START, 2, StreakRunStatus.ENDED_BY_RESULT);
        StreakRun second = run(START.plusDays(3), 2, StreakRunStatus.RUNNING);

        List<StreakRunChange> changes = reconciler.reconcile(new StreakRunAnalysis(List.of(original)),
                new StreakRunAnalysis(List.of(shortened, second)));
        assertEquals(2, changes.size());
        assertTrue(changes.stream().anyMatch(change -> change.type() == StreakRunChange.Type.UPDATED));
        assertTrue(changes.stream().anyMatch(change -> change.type() == StreakRunChange.Type.ADDED));
    }

    @Test
    void joiningAndShiftingRunsProducesExplicitRemovalsAndAdditions() {
        StreakRun first = run(START, 2, StreakRunStatus.ENDED_BY_RESULT);
        StreakRun second = run(START.plusDays(3), 2, StreakRunStatus.ENDED_BY_RESULT);
        StreakRun joined = run(START.minusDays(1), 6, StreakRunStatus.RUNNING);

        List<StreakRunChange> changes = reconciler.reconcile(new StreakRunAnalysis(List.of(first, second)),
                new StreakRunAnalysis(List.of(joined)));
        assertEquals(3, changes.size());
        assertEquals(2, changes.stream().filter(change -> change.type() == StreakRunChange.Type.REMOVED).count());
        assertEquals(1, changes.stream().filter(change -> change.type() == StreakRunChange.Type.ADDED).count());
    }

    private static StreakRun run(LocalDate start, int length, StreakRunStatus status) {
        return new StreakRun(new StreakRunIdentity(StreakRecordMetric.ACTIVITY,
                new RecordScope.Personal(1), start), start.plusDays(length - 1), status);
    }
}
