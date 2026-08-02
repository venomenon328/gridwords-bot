package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import java.time.LocalDate;
import java.util.Optional;

/** Read-only lookup of the current result, deliberately independent from submission history. */
public interface DailyResultDetailsQuery { Optional<ParsedGameResult> find(long discordUserId, GameType gameType, LocalDate gameDate); }