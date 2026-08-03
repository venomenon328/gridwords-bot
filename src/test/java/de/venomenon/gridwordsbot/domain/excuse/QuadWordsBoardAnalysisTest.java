package de.venomenon.gridwordsbot.domain.excuse;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuadWordsBoardAnalysisTest {

    @Test
    void recognizesThreeSolvedBoardsAndTheSingleUnsolvedWorstBoard() {
        QuadWordsBoardAnalysis analysis = QuadWordsBoardAnalysis.analyze(
                boards(board(3, 2), board(3, 3), board(3, 1), board(3, 0)), 8, 3);

        assertThat(analysis.singleBoardCollapse()).isTrue();
        assertThat(analysis.uniqueWorstBoard()).contains(QuadWordsBoardPosition.BOTTOM_RIGHT);
        assertThat(analysis.facts()).contains(
                ExcuseFact.FOUR_BOARDS_PRESENT,
                ExcuseFact.THREE_BOARDS_SOLVED_ONE_UNSOLVED,
                ExcuseFact.UNIQUE_WORST_BOARD,
                ExcuseFact.BOTTOM_RIGHT_WORST);
    }

    @Test
    void recognizesAnEightAttemptWorstBoardWithExactlyThreeAttemptsGap() {
        QuadWordsBoardAnalysis analysis = QuadWordsBoardAnalysis.analyze(
                boards(board(8, 2), board(8, 4), board(8, 5), board(8, 8)), 8, 3);

        assertThat(analysis.singleBoardCollapse()).isTrue();
        assertThat(analysis.significantWorstBoardGap()).isTrue();
        assertThat(analysis.uniqueWorstBoard()).contains(QuadWordsBoardPosition.BOTTOM_RIGHT);
    }

    @Test
    void rejectsATooSmallGapTiedWorstBoardAndWorstAttemptSeven() {
        assertThat(QuadWordsBoardAnalysis.analyze(boards(board(8, 3), board(8, 5), board(8, 6), board(8, 8)), 8, 3)
                .singleBoardCollapse()).isFalse();
        assertThat(QuadWordsBoardAnalysis.analyze(boards(board(8, 3), board(8, 4), board(8, 8), board(8, 8)), 8, 3)
                .singleBoardCollapse()).isFalse();
        assertThat(QuadWordsBoardAnalysis.analyze(boards(board(7, 3), board(7, 4), board(7, 5), board(7, 7)), 8, 3)
                .singleBoardCollapse()).isFalse();
    }

    @Test
    void doesNotTreatTwoOrFourUnsolvedBoardsAsASingleBoardCollapse() {
        assertThat(QuadWordsBoardAnalysis.analyze(boards(board(9, 3), board(9, 5), board(9, 0), board(9, 0)), 8, 3)
                .singleBoardCollapse()).isFalse();
        assertThat(QuadWordsBoardAnalysis.analyze(boards(board(9, 0), board(9, 0), board(9, 0), board(9, 0)), 8, 3)
                .singleBoardCollapse()).isFalse();
    }

    @Test
    void usesTheFirstAllGreenRowAsTheSolutionAttempt() {
        QuadWordsBoardAnalysis analysis = QuadWordsBoardAnalysis.analyze(
                boards(boardWithTwoGreenRows(), board(4, 2), board(4, 3), board(4, 4)), 4, 1);

        assertThat(analysis.solutionAttempts().getFirst()).hasValue(2);
    }

    @Test
    void boardlessResultExposesNoBoardContext() {
        QuadWordsBoardAnalysis analysis = QuadWordsBoardAnalysis.boardless();

        assertThat(analysis.boardsPresent()).isFalse();
        assertThat(analysis.facts()).containsExactly(ExcuseFact.BOARDLESS_SUBMISSION);
        assertThat(analysis.uniqueWorstBoard()).isEmpty();
    }

    private static QuadWordsBoards boards(QuadWordsBoard topLeft, QuadWordsBoard topRight,
            QuadWordsBoard bottomLeft, QuadWordsBoard bottomRight) {
        return new QuadWordsBoards(topLeft, topRight, bottomLeft, bottomRight);
    }

    private static QuadWordsBoard board(int rows, int solvedAttempt) {
        return new QuadWordsBoard(java.util.stream.IntStream.rangeClosed(1, rows)
                .mapToObj(attempt -> attempt == solvedAttempt ? green() : white()).toList());
    }

    private static QuadWordsBoard boardWithTwoGreenRows() {
        return new QuadWordsBoard(List.of(white(), green(), green(), white()));
    }

    private static String green() {
        return "🟩".repeat(5);
    }

    private static String white() {
        return "⬜".repeat(5);
    }
}
