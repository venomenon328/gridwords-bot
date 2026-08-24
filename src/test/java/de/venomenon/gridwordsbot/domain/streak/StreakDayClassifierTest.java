package de.venomenon.gridwordsbot.domain.streak;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreakDayClassifierTest {
    private static final LocalDate DAY = LocalDate.of(2026, 8, 1);

    @Test
    void sharesThePositiveSeriesSemanticsForSubmittedSolvedAndMissingResults() {
        StreakDayClassifier classifier = classifier(List.of(
                result(1, GameType.GRIDWORDS, true), result(1, GameType.QUADWORDS, false),
                result(2, GameType.GRIDWORDS, true), result(2, GameType.QUADWORDS, true)));

        assertEquals(StreakDayAssessment.State.MET, classifier.personalActivity(1, DAY, false).state());
        assertEquals(StreakDayAssessment.State.MET, classifier.personalComplete(1, DAY, false).state());
        assertEquals(StreakDayAssessment.State.MET,
                classifier.personalSolved(1, DAY, GameType.GRIDWORDS, false).state());
        assertEquals(StreakDayAssessment.BoundaryReason.RESULT,
                classifier.personalSolved(1, DAY, GameType.QUADWORDS, false).boundaryReason().orElseThrow());
        assertEquals(StreakDayAssessment.BoundaryReason.RESULT,
                classifier.personalPerfect(1, DAY, false).boundaryReason().orElseThrow());
        assertEquals(StreakDayAssessment.State.MET, classifier.sharedComplete(DAY, false).state());
        assertEquals(StreakDayAssessment.State.MET, classifier.sharedPerfect(DAY, false).state());
    }

    @Test
    void distinguishesOpenMissingDayFromLogicalDayClose() {
        StreakDayClassifier classifier = classifier(List.of(result(1, GameType.GRIDWORDS, true)));

        assertEquals(StreakDayAssessment.State.PENDING,
                classifier.personalSolved(1, DAY, GameType.QUADWORDS, false).state());
        assertEquals(StreakDayAssessment.BoundaryReason.DAY_CLOSE,
                classifier.personalSolved(1, DAY, GameType.QUADWORDS, true).boundaryReason().orElseThrow());
        assertEquals(StreakDayAssessment.State.PENDING, classifier.personalComplete(1, DAY, false).state());
        assertEquals(StreakDayAssessment.BoundaryReason.DAY_CLOSE,
                classifier.personalComplete(1, DAY, true).boundaryReason().orElseThrow());
    }

    @Test
    void derivesDroughtsAndDaysWithoutPerfectResultWithoutTreatingMissingAsDrought() {
        StreakDayClassifier classifier = classifier(List.of(
                result(1, GameType.GRIDWORDS, false), result(1, GameType.QUADWORDS, true)));

        assertEquals(StreakDayAssessment.State.MET,
                classifier.personalDrought(1, DAY, GameType.GRIDWORDS, false).state());
        assertEquals(StreakDayAssessment.BoundaryReason.RESULT,
                classifier.personalDrought(1, DAY, GameType.QUADWORDS, false).boundaryReason().orElseThrow());
        assertEquals(StreakDayAssessment.State.MET,
                classifier.personalWithoutPerfectDay(1, DAY, false).state());

        StreakDayClassifier missing = classifier(List.of());
        assertEquals(StreakDayAssessment.State.PENDING,
                missing.personalDrought(1, DAY, GameType.GRIDWORDS, false).state());
        assertEquals(StreakDayAssessment.BoundaryReason.DAY_CLOSE,
                missing.personalDrought(1, DAY, GameType.GRIDWORDS, true).boundaryReason().orElseThrow());
        assertEquals(StreakDayAssessment.State.PENDING,
                missing.personalWithoutPerfectDay(1, DAY, false).state());
        assertEquals(StreakDayAssessment.State.MET,
                missing.personalWithoutPerfectDay(1, DAY, true).state());
    }

    @Test
    void treatsMissingParticipationAsACalendarBoundary() {
        List<GameParticipationPeriod> periods = List.of(
                new GameParticipationPeriod(1, GameType.GRIDWORDS, DAY, null),
                new GameParticipationPeriod(1, GameType.QUADWORDS, DAY, null));
        StreakDayClassifier classifier = new StreakDayClassifier(List.of(), periods);

        assertEquals(StreakDayAssessment.BoundaryReason.PARTICIPATION,
                classifier.personalSolved(2, DAY, GameType.GRIDWORDS, true).boundaryReason().orElseThrow());
        StreakDayClassifier noSharedParticipants = new StreakDayClassifier(List.of(), List.of());
        assertEquals(StreakDayAssessment.BoundaryReason.PARTICIPATION,
                noSharedParticipants.sharedSolved(DAY, GameType.GRIDWORDS, true).boundaryReason().orElseThrow());
    }

    private static StreakDayClassifier classifier(List<StreakGameResult> results) {
        List<GameParticipationPeriod> periods = new ArrayList<>();
        for (long player : List.of(1L, 2L)) {
            for (GameType game : GameType.values()) {
                periods.add(new GameParticipationPeriod(player, game, DAY, null));
            }
        }
        return new StreakDayClassifier(results, periods);
    }

    private static StreakGameResult result(long player, GameType game, boolean solved) {
        return new StreakGameResult(player, DAY, game, solved);
    }
}
