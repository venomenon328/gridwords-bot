package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.reporting.ReportGameResult;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import java.util.List;
import java.util.Set;

/** Read boundary for valid persisted game results needed by one report period. */
public interface ReportGameResultQuery {
    List<ReportGameResult> findResults(ReportPeriod period, Set<Long> participantIds);
}
