package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseDailyResult;
import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Reads already committed valid results for one game-day. Implementations must restrict the result
 * set to the supplied historically effective participants and exclude the requesting player.
 */
public interface ExcuseDailyResultQuery {
    List<ExcuseDailyResult> findPriorValidResults(
            long requestingPlayerId, GameType gameType, LocalDate gameDate, Set<Long> participantIds);
}
