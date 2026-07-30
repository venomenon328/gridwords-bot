package de.venomenon.gridwordsbot.domain.status;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Transport-neutral, complete projection of one business day's state. */
public record DailyStatus(LocalDate gameDate, List<PlayerLine> players, int sharedComplete, int sharedPerfect) {
    public DailyStatus {
        Objects.requireNonNull(gameDate, "gameDate");
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        if (sharedComplete < 0 || sharedPerfect < 0) throw new IllegalArgumentException("negative shared streak");
    }

    public record PlayerLine(long discordUserId, String displayName, Optional<ParsedGameResult> gridWords,
                             Optional<ParsedGameResult> quadWords, StreakSummary streaks) {
        public PlayerLine {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
            gridWords = Objects.requireNonNull(gridWords, "gridWords");
            quadWords = Objects.requireNonNull(quadWords, "quadWords");
            Objects.requireNonNull(streaks, "streaks");
        }
        public Optional<ParsedGameResult> result(GameType type) {
            return type == GameType.GRIDWORDS ? gridWords : quadWords;
        }
    }
}
