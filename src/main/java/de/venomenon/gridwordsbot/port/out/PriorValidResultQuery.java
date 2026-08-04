package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseDailyResult;
import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Reads already committed valid results for one game-day before the result currently being stored.
 * Implementations restrict the result set to historically effective participants and exclude the
 * requesting player.
 */
public interface PriorValidResultQuery {
    List<ExcuseDailyResult> findPriorValidResults(
            long requestingPlayerId, GameType gameType, LocalDate gameDate, Set<Long> participantIds);
}
