package de.venomenon.gridwordsbot.domain.excuse;

/** A reason that can independently make a result eligible for an excuse. */
public enum ExcuseReason implements ExcuseCondition {
    NOT_SOLVED,
    VERY_LATE_SUBMISSION,
    GRIDWORDS_LAST_ATTEMPT,
    GRIDWORDS_VERY_SLOW,
    QUADWORDS_VERY_SLOW,
    QUADWORDS_SINGLE_BOARD_COLLAPSE,
    CLEAR_CURRENT_DAILY_OUTLIER;

    @Override
    public String key() {
        return name();
    }
}
