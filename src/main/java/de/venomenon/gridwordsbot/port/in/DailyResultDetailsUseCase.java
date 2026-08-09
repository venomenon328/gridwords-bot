package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/** Transport-neutral, read-only detail query for a player offered by a daily status selector. */
public interface DailyResultDetailsUseCase {
    Result get(Request request);
    record Request(long guildId, long channelId, long messageId, LocalDate gameDate, GameType gameType, int pageIndex, long targetDiscordUserId) {
        public Request { if (guildId <= 0 || channelId <= 0 || messageId <= 0 || targetDiscordUserId <= 0 || pageIndex < 0) throw new IllegalArgumentException("invalid detail request identifier"); Objects.requireNonNull(gameDate, "gameDate"); Objects.requireNonNull(gameType, "gameType"); }
    }
    sealed interface Result permits Found, Missing, Rejected { }
    record Found(String playerDisplayName, ParsedGameResult result, Optional<String> selectedExcuse,
                 List<CurrentRecord> currentRecords, List<Achievement> achievements) implements Result {
        public Found { Objects.requireNonNull(playerDisplayName, "playerDisplayName"); Objects.requireNonNull(result, "result"); selectedExcuse = Objects.requireNonNull(selectedExcuse, "selectedExcuse"); currentRecords = List.copyOf(Objects.requireNonNull(currentRecords, "currentRecords")); achievements = List.copyOf(Objects.requireNonNull(achievements, "achievements")); }
        public Found(String playerDisplayName, ParsedGameResult result) { this(playerDisplayName, result, Optional.empty(), List.of(), List.of()); }
    }
    record CurrentRecord(String metric, String scope) { public CurrentRecord { Objects.requireNonNull(metric, "metric"); Objects.requireNonNull(scope, "scope"); } }
    record Achievement(String emoji, String displayName) { public Achievement { Objects.requireNonNull(emoji, "emoji"); Objects.requireNonNull(displayName, "displayName"); } }
    record Missing(String playerDisplayName, GameType gameType, LocalDate gameDate) implements Result { public Missing { Objects.requireNonNull(playerDisplayName, "playerDisplayName"); Objects.requireNonNull(gameType, "gameType"); Objects.requireNonNull(gameDate, "gameDate"); } }
    record Rejected(Reason reason) implements Result { public Rejected { Objects.requireNonNull(reason, "reason"); } }
    enum Reason { STATUS_NOT_CURRENT, TARGET_NOT_PARTICIPATING, PAGE_NOT_OFFERED, TARGET_NOT_ON_PAGE }
}
