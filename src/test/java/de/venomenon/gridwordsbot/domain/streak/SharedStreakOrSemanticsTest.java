package de.venomenon.gridwordsbot.domain.streak;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharedStreakOrSemanticsTest {
    private static final LocalDate DAY = LocalDate.of(2026, 8, 12);

    @Test
    void solvedSeriesIsMetWhenOnePlayerSolvesDespiteAnotherPlayersFailureOrMissingResult() {
        assertThat(classifier(
                List.of(result(1, GameType.GRIDWORDS, true), result(2, GameType.GRIDWORDS, false)),
                bothGames(1), bothGames(2)).sharedSolved(DAY, GameType.GRIDWORDS, false))
                .isEqualTo(StreakDayAssessment.met());
        assertThat(classifier(List.of(result(1, GameType.GRIDWORDS, true)), bothGames(1), bothGames(2))
                .sharedSolved(DAY, GameType.GRIDWORDS, false))
                .isEqualTo(StreakDayAssessment.met());
    }

    @Test
    void solvedSeriesRemainsPendingUntilEveryPlayerIsBlockedThenUsesTheCorrectBoundary() {
        assertThat(classifier(List.of(result(1, GameType.GRIDWORDS, false)), bothGames(1), bothGames(2))
                .sharedSolved(DAY, GameType.GRIDWORDS, false))
                .isEqualTo(StreakDayAssessment.pending());
        assertThat(classifier(
                List.of(result(1, GameType.GRIDWORDS, false), result(2, GameType.GRIDWORDS, false)),
                bothGames(1), bothGames(2)).sharedSolved(DAY, GameType.GRIDWORDS, false))
                .isEqualTo(StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.RESULT));
        assertThat(classifier(List.of(result(1, GameType.GRIDWORDS, false)), bothGames(1), bothGames(2))
                .sharedSolved(DAY, GameType.GRIDWORDS, true))
                .isEqualTo(StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.DAY_CLOSE));
    }

    @Test
    void oneBothGamesParticipantCanContinueEverySharedSeries() {
        StreakDayClassifier classifier = classifier(List.of(
                result(1, GameType.GRIDWORDS, true), result(1, GameType.QUADWORDS, true)), bothGames(1));

        assertThat(classifier.sharedSolved(DAY, GameType.GRIDWORDS, false)).isEqualTo(StreakDayAssessment.met());
        assertThat(classifier.sharedSolved(DAY, GameType.QUADWORDS, false)).isEqualTo(StreakDayAssessment.met());
        assertThat(classifier.sharedComplete(DAY, false)).isEqualTo(StreakDayAssessment.met());
        assertThat(classifier.sharedPerfect(DAY, false)).isEqualTo(StreakDayAssessment.met());
    }

    @Test
    void completeCountsAnyBothGamesSubmissionIncludingFailuresButNeverCombinesSingleGamePlayers() {
        assertThat(classifier(List.of(
                result(1, GameType.GRIDWORDS, false), result(1, GameType.QUADWORDS, false)),
                bothGames(1), bothGames(2)).sharedComplete(DAY, false))
                .isEqualTo(StreakDayAssessment.met());
        assertThat(classifier(List.of(
                result(1, GameType.GRIDWORDS, true), result(2, GameType.QUADWORDS, true)),
                game(1, GameType.GRIDWORDS), game(2, GameType.QUADWORDS)).sharedComplete(DAY, true))
                .isEqualTo(StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.PARTICIPATION));
    }

    @Test
    void perfectSeriesAllowsOnePerfectPlayerAndOnlyEndsWhenAllRelevantPlayersAreBlocked() {
        assertThat(classifier(List.of(
                result(1, GameType.GRIDWORDS, true), result(1, GameType.QUADWORDS, true),
                result(2, GameType.GRIDWORDS, false)), bothGames(1), bothGames(2))
                .sharedPerfect(DAY, false)).isEqualTo(StreakDayAssessment.met());
        assertThat(classifier(List.of(
                result(1, GameType.GRIDWORDS, false), result(2, GameType.QUADWORDS, false)),
                bothGames(1), bothGames(2)).sharedPerfect(DAY, false))
                .isEqualTo(StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.RESULT));
        assertThat(classifier(List.of(result(1, GameType.GRIDWORDS, false)), bothGames(1), bothGames(2))
                .sharedPerfect(DAY, true))
                .isEqualTo(StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.DAY_CLOSE));
    }

    @Test
    void noRelevantPlayersCreatesAParticipationBoundary() {
        StreakDayClassifier classifier = classifier(List.of(), List.of());

        assertThat(classifier.sharedSolved(DAY, GameType.GRIDWORDS, true))
                .isEqualTo(StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.PARTICIPATION));
        assertThat(classifier.sharedComplete(DAY, true))
                .isEqualTo(StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.PARTICIPATION));
        assertThat(classifier.sharedPerfect(DAY, true))
                .isEqualTo(StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.PARTICIPATION));
    }

    private static StreakDayClassifier classifier(
            List<StreakGameResult> results, List<GameParticipationPeriod>... periods) {
        List<GameParticipationPeriod> allPeriods = new ArrayList<>();
        for (List<GameParticipationPeriod> playerPeriods : periods) {
            allPeriods.addAll(playerPeriods);
        }
        return new StreakDayClassifier(results, allPeriods);
    }

    private static List<GameParticipationPeriod> bothGames(long playerId) {
        return List.of(game(playerId, GameType.GRIDWORDS).getFirst(), game(playerId, GameType.QUADWORDS).getFirst());
    }

    private static List<GameParticipationPeriod> game(long playerId, GameType game) {
        return List.of(new GameParticipationPeriod(playerId, game, DAY, null));
    }

    private static StreakGameResult result(long playerId, GameType game, boolean solved) {
        return new StreakGameResult(playerId, DAY, game, solved);
    }
}
