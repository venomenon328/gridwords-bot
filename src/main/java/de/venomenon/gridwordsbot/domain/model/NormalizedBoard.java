package de.venomenon.gridwordsbot.domain.model;

import java.util.List;
import java.util.Objects;

/** A structurally valid GridWords board in its canonical Unicode representation. */
public record NormalizedBoard(List<String> rows) {

    private static final String ALLOWED_CELLS = "⬜🟨🟩";

    public NormalizedBoard {
        Objects.requireNonNull(rows, "rows must not be null");
        rows = List.copyOf(rows);
        if (rows.isEmpty() || rows.size() > 6) {
            throw new IllegalArgumentException("a board must contain between 1 and 6 rows");
        }
        for (String row : rows) {
            if (row == null || row.codePointCount(0, row.length()) != 5) {
                throw new IllegalArgumentException("each board row must contain exactly 5 cells");
            }
            for (int index = 0; index < row.length();) {
                int codePoint = row.codePointAt(index);
                if (ALLOWED_CELLS.indexOf(codePoint) < 0) {
                    throw new IllegalArgumentException("board rows must use canonical cell symbols");
                }
                index += Character.charCount(codePoint);
            }
        }
    }

    public String canonicalText() {
        return String.join("\n", rows);
    }

    /** Rehydrates only the canonical line-based representation written by {@link #canonicalText()}. */
    public static NormalizedBoard fromCanonicalText(String text) {
        Objects.requireNonNull(text, "text");
        return new NormalizedBoard(List.of(text.split("\\n", -1)));
    }
}
