package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.record.RecordScopeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read-only lookup of the current result, deliberately independent from submission history. */
public interface DailyResultDetailsQuery {
    Optional<Details> find(long guildId, long discordUserId, GameType gameType, LocalDate gameDate);

    record Details(long gameResultId, ParsedGameResult result, Optional<String> selectedExcuse,
                   List<CurrentRecord> currentRecords, List<String> activeAchievementKeys) {
        public Details {
            if (gameResultId <= 0) throw new IllegalArgumentException("gameResultId must be positive");
            java.util.Objects.requireNonNull(result, "result");
            selectedExcuse = java.util.Objects.requireNonNull(selectedExcuse, "selectedExcuse");
            currentRecords = List.copyOf(java.util.Objects.requireNonNull(currentRecords, "currentRecords"));
            activeAchievementKeys = List.copyOf(java.util.Objects.requireNonNull(activeAchievementKeys, "activeAchievementKeys"));
        }
    }

    record CurrentRecord(String definitionKey, RecordScopeType scopeType) {
        public CurrentRecord {
            java.util.Objects.requireNonNull(definitionKey, "definitionKey");
            java.util.Objects.requireNonNull(scopeType, "scopeType");
        }
    }
}
