package de.venomenon.gridwordsbot.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class QuadWordsBoardTest {
    private static final String BLANK = "\u2b1c";
    private static final String YELLOW = "\ud83d\udfe8";
    private static final String GREEN = "\ud83d\udfe9";

    @Test
    void preservesTheFourNamedBoardsInCanonicalOrderAndRoundTripsRows() {
        QuadWordsBoard topLeft = board(BLANK);
        QuadWordsBoard topRight = board(YELLOW);
        QuadWordsBoard bottomLeft = board(GREEN);
        QuadWordsBoard bottomRight = board(BLANK + YELLOW + GREEN + BLANK + YELLOW);

        QuadWordsBoards boards = QuadWordsBoards.fromOrdered(
                List.of(topLeft, topRight, bottomLeft, bottomRight));

        assertThat(boards.ordered()).containsExactly(topLeft, topRight, bottomLeft, bottomRight);
        assertThat(QuadWordsBoard.fromCanonicalText(bottomRight.canonicalText())).isEqualTo(bottomRight);
    }

    @Test
    void rejectsMalformedRowsSymbolsAndBoardCounts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new QuadWordsBoard(List.of(BLANK.repeat(4))));
        assertThatIllegalArgumentException().isThrownBy(() -> new QuadWordsBoard(List.of(BLANK + "X" + BLANK.repeat(3))));
        assertThatIllegalArgumentException().isThrownBy(() -> QuadWordsBoards.fromOrdered(List.of(
                board(BLANK), board(YELLOW), board(GREEN))));
    }

    @Test
    void requiresEveryQuadWordsBoardToMatchTheOutcomeRowCount() {
        QuadWordsBoards mismatched = new QuadWordsBoards(board(BLANK), board(YELLOW), board(GREEN),
                new QuadWordsBoard(List.of((BLANK + YELLOW + GREEN + BLANK + YELLOW).repeat(1))));

        assertThatIllegalArgumentException().isThrownBy(() -> new ParsedGameResult(
                GameType.QUADWORDS,
                LocalDate.of(2026, 7, 30),
                new ShareOutcome.Solved(2, 9),
                Duration.ZERO,
                OptionalInt.empty(),
                Optional.empty(),
                Optional.of(mismatched)));
    }

    private static QuadWordsBoard board(String pattern) {
        String row = pattern.codePointCount(0, pattern.length()) == 1 ? pattern.repeat(5) : pattern;
        return new QuadWordsBoard(List.of(row, row));
    }
}
