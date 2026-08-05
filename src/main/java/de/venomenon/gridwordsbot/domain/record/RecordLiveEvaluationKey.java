package de.venomenon.gridwordsbot.domain.record;

/** Stable identity of one record evaluation for one persisted canonical result version. */
public record RecordLiveEvaluationKey(long guildId, long gameResultId, long gameResultVersion) {
    public RecordLiveEvaluationKey {
        if (guildId <= 0 || gameResultId <= 0) {
            throw new IllegalArgumentException("guildId and gameResultId must be positive");
        }
        if (gameResultVersion < 0) {
            throw new IllegalArgumentException("gameResultVersion must not be negative");
        }
    }
}
