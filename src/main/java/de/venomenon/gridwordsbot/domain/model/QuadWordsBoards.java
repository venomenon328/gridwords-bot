package de.venomenon.gridwordsbot.domain.model;

import java.util.List;
import java.util.Objects;

/** Four QuadWords boards in top-left, top-right, bottom-left, bottom-right order. */
public record QuadWordsBoards(
        QuadWordsBoard topLeft,
        QuadWordsBoard topRight,
        QuadWordsBoard bottomLeft,
        QuadWordsBoard bottomRight) {

    public QuadWordsBoards {
        Objects.requireNonNull(topLeft, "topLeft");
        Objects.requireNonNull(topRight, "topRight");
        Objects.requireNonNull(bottomLeft, "bottomLeft");
        Objects.requireNonNull(bottomRight, "bottomRight");
    }

    public List<QuadWordsBoard> ordered() {
        return List.of(topLeft, topRight, bottomLeft, bottomRight);
    }

    /** Recreates the named board order from the canonical persistence order. */
    public static QuadWordsBoards fromOrdered(List<QuadWordsBoard> boards) {
        boards = List.copyOf(Objects.requireNonNull(boards, "boards"));
        if (boards.size() != 4) throw new IllegalArgumentException("QuadWords requires exactly four boards");
        return new QuadWordsBoards(boards.get(0), boards.get(1), boards.get(2), boards.get(3));
    }
}
