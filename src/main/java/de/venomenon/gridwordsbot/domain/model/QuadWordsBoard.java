package de.venomenon.gridwordsbot.domain.model;

import java.util.List;
import java.util.Objects;

/** One normalized QuadWords board with one to nine five-cell rows. */
public record QuadWordsBoard(List<String> rows) {
    private static final String ALLOWED = "⬜🟨🟩";
    public QuadWordsBoard {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (rows.isEmpty() || rows.size() > 9) throw new IllegalArgumentException("rows must contain one to nine entries");
        for (String row : rows) {
            if (row == null || row.codePointCount(0, row.length()) != 5) throw new IllegalArgumentException("each row needs five cells");
            for (int offset = 0; offset < row.length();) {
                int cell = row.codePointAt(offset);
                if (ALLOWED.indexOf(cell) < 0) throw new IllegalArgumentException("unsupported cell symbol");
                offset += Character.charCount(cell);
            }
        }
    }
}