package de.venomenon.gridwordsbot.domain.reporting;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import java.util.List;
import java.util.Objects;

/** Historical inputs bounded by a report period's inclusive streak cutoff. */
public record ReportStreakHistory(List<GameParticipationPeriod> participationPeriods, List<ReportGameResult> results) {
    public ReportStreakHistory {
        participationPeriods = List.copyOf(Objects.requireNonNull(participationPeriods, "participationPeriods"));
        results = List.copyOf(Objects.requireNonNull(results, "results"));
    }
}
