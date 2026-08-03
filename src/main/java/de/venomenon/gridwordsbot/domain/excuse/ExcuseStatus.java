package de.venomenon.gridwordsbot.domain.excuse;

/** Persisted lifecycle state of the one optional excuse decision for a game result. */
public enum ExcuseStatus {
    NOT_OFFERED,
    AVAILABLE,
    SELECTED,
    DECLINED,
    EXPIRED,
    INVALIDATED
}
