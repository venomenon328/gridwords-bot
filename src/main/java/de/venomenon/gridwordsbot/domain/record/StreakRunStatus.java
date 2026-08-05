package de.venomenon.gridwordsbot.domain.record;

public enum StreakRunStatus {
    RUNNING,
    ENDED_BY_RESULT,
    ENDED_BY_DAY_CLOSE,
    ENDED_BY_PARTICIPATION;

    public boolean completed() {
        return this != RUNNING;
    }
}
