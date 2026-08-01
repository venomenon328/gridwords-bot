package de.venomenon.gridwordsbot.domain.reporting;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.Objects;

/** Both independent game statistics for one report participant. */
public record ReportPlayerGameStatistics(long discordUserId, ReportGameStatistics gridWords, ReportGameStatistics quadWords) {
    public ReportPlayerGameStatistics {
        if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
        Objects.requireNonNull(gridWords, "gridWords");
        Objects.requireNonNull(quadWords, "quadWords");
        if (gridWords.gameType() != GameType.GRIDWORDS || quadWords.gameType() != GameType.QUADWORDS) {
            throw new IllegalArgumentException("player statistics must contain GridWords and QuadWords exactly once");
        }
    }

    public ReportGameStatistics statisticsFor(GameType gameType) {
        return gameType == GameType.GRIDWORDS ? gridWords : quadWords;
    }
}
