package de.venomenon.gridwordsbot.adapter.discord.common;

import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** JDA-free formatter shared by canonical and interactive QuadWords result views. */
public final class QuadWordsBoardFormatter {
    private static final String SOLVED_ROW = "🟩🟩🟩🟩🟩";
    private static final String EMPTY_ROW = "⬛⬛⬛⬛⬛";
    private static final String PAIR_GAP = "  ";

    private QuadWordsBoardFormatter() {
    }

    public static String format(QuadWordsBoards boards) {
        Objects.requireNonNull(boards, "boards");
        return pair(boards.topLeft(), boards.topRight())
                + "\n\n"
                + pair(boards.bottomLeft(), boards.bottomRight());
    }

    private static String pair(QuadWordsBoard leftBoard, QuadWordsBoard rightBoard) {
        List<String> leftRows = visibleRows(leftBoard);
        List<String> rightRows = visibleRows(rightBoard);
        int pairHeight = Math.max(leftRows.size(), rightRows.size());
        List<String> lines = new ArrayList<>(pairHeight);
        for (int row = 0; row < pairHeight; row++) {
            String left = row < leftRows.size() ? leftRows.get(row) : EMPTY_ROW;
            String right = row < rightRows.size() ? rightRows.get(row) : EMPTY_ROW;
            lines.add(left + PAIR_GAP + right);
        }
        return String.join("\n", lines);
    }

    private static List<String> visibleRows(QuadWordsBoard board) {
        List<String> rows = Objects.requireNonNull(board, "board").rows();
        int solutionRow = rows.indexOf(SOLVED_ROW);
        return solutionRow < 0 ? rows : rows.subList(0, solutionRow + 1);
    }
}
