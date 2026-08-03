package de.venomenon.gridwordsbot.adapter.discord.common;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuadWordsBoardFormatterTest {
    @Test
    void usesTheLaterSolutionAsPairHeightAndPadsTheShorterBoardDark() {
        String topLeft = "⬜🟨🟩⬜🟨";
        String topRight = "🟨🟩⬜🟨🟩";
        String bottomLeft = "🟩⬜🟨🟩⬜";
        String bottomRight = "🟨⬜🟩⬜🟨";
        QuadWordsBoards boards = new QuadWordsBoards(
                solvedBoard(topLeft, 7), solvedBoard(topRight, 9),
                solvedBoard(bottomLeft, 4), solvedBoard(bottomRight, 6));

        String[] pairs = QuadWordsBoardFormatter.format(boards).split("\n\n");
        String[] topPair = pairs[0].split("\n", -1);
        String[] bottomPair = pairs[1].split("\n", -1);

        assertThat(pairs).hasSize(2);
        assertThat(topPair).hasSize(9);
        assertThat(bottomPair).hasSize(6);
        assertThat(topPair[6]).isEqualTo("🟩🟩🟩🟩🟩  " + topRight);
        assertThat(topPair[7]).isEqualTo("⬛⬛⬛⬛⬛  " + topRight);
        assertThat(bottomPair[3]).isEqualTo("🟩🟩🟩🟩🟩  " + bottomRight);
        assertThat(bottomPair[4]).isEqualTo("⬛⬛⬛⬛⬛  " + bottomRight);
    }

    @Test
    void keepsAllRowsOfAnUnsolvedBoard() {
        String unsolved = "⬜🟨🟩⬜🟨";
        QuadWordsBoards boards = new QuadWordsBoards(
                new QuadWordsBoard(Collections.nCopies(9, unsolved)), solvedBoard(unsolved, 2),
                solvedBoard(unsolved, 2), solvedBoard(unsolved, 2));

        String[] topPair = QuadWordsBoardFormatter.format(boards).split("\n\n")[0].split("\n", -1);

        assertThat(topPair).hasSize(9);
        assertThat(topPair[8]).isEqualTo(unsolved + "  ⬛⬛⬛⬛⬛");
    }

    private static QuadWordsBoard solvedBoard(String activeRow, int solutionHeight) {
        List<String> rows = new ArrayList<>(Collections.nCopies(9, "🟨⬜🟨⬜🟨"));
        for (int index = 0; index < solutionHeight - 1; index++) {
            rows.set(index, activeRow);
        }
        rows.set(solutionHeight - 1, "🟩🟩🟩🟩🟩");
        return new QuadWordsBoard(rows);
    }
}
