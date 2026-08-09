package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.reporting.ReportHighlightFacts;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import java.util.Set;

/** Read-only boundary for already materialized Award and Record facts used in one report. */
public interface ReportHighlightQuery {
    ReportHighlightFacts find(long guildId, ReportPeriod period, Set<Long> participantIds);
}
