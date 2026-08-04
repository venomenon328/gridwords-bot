package de.venomenon.gridwordsbot.domain.excuse;

import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Explicit, transport-neutral input to the excuse offer policy. */
public record ExcuseEligibilityRequest(
        long playerId,
        ParsedGameResult result,
        Instant receivedAt,
        DailyGameParticipation participation,
        List<ExcuseDailyResult> priorResults,
        boolean exclusivePositivePriorityEvent) {

    public ExcuseEligibilityRequest {
        if (playerId <= 0) {
            throw new IllegalArgumentException("playerId must be positive");
        }
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(participation, "participation");
        priorResults = List.copyOf(Objects.requireNonNull(priorResults, "priorResults"));
        if (!participation.gameDate().equals(result.gameDate())) {
            throw new IllegalArgumentException("participation must be for the result game date");
        }
        if (priorResults.stream().anyMatch(candidate -> candidate.gameType() != result.gameType()
                || !candidate.gameDate().equals(result.gameDate()))) {
            throw new IllegalArgumentException("prior results must match the result game and date");
        }
    }
}
