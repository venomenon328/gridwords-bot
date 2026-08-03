package de.venomenon.gridwordsbot.domain.excuse;

/** Additional verified context that may specialize an excuse without independently offering one. */
public enum ExcuseFact implements ExcuseCondition {
    FOUR_BOARDS_PRESENT,
    BOARDLESS_SUBMISSION,
    UNIQUE_WORST_BOARD,
    SIGNIFICANT_WORST_BOARD_GAP,
    THREE_BOARDS_SOLVED_ONE_UNSOLVED,
    ALL_BOARDS_SOLVED,
    BOARDS_SIMILAR,
    TOP_LEFT_WORST,
    TOP_RIGHT_WORST,
    BOTTOM_LEFT_WORST,
    BOTTOM_RIGHT_WORST;

    @Override
    public String key() {
        return name();
    }
}
