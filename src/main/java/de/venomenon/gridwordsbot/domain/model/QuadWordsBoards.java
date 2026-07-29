package de.venomenon.gridwordsbot.domain.model;

import java.util.List;
import java.util.Objects;

/** Four QuadWords boards in top-left, top-right, bottom-left, bottom-right order. */
public record QuadWordsBoards(QuadWordsBoard topLeft, QuadWordsBoard topRight, QuadWordsBoard bottomLeft, QuadWordsBoard bottomRight) {
    public QuadWordsBoards {
        Objects.requireNonNull(topLeft); Objects.requireNonNull(topRight); Objects.requireNonNull(bottomLeft); Objects.requireNonNull(bottomRight);
    }
    public List<QuadWordsBoard> ordered() { return List.of(topLeft, topRight, bottomLeft, bottomRight); }
}