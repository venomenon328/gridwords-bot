package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.reporting.ReportHighlightFacts;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.RecordEventStore;
import de.venomenon.gridwordsbot.port.out.ReportHighlightQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** PostgreSQL-backed, read-only report projection over the existing Award and Record facts. */
public final class PostgresReportHighlightQuery implements ReportHighlightQuery {
    private final AchievementAwardStateStore awards;
    private final RecordEventStore events;

    public PostgresReportHighlightQuery(AchievementAwardStateStore awards, RecordEventStore events) {
        this.awards = Objects.requireNonNull(awards, "awards");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public ReportHighlightFacts find(long guildId, ReportPeriod period, Set<Long> participantIds) {
        if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(participantIds, "participantIds");
        if (participantIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("participantIds must be positive");
        }
        Map<Long, Integer> awardCounts = new LinkedHashMap<>();
        awards.findActiveForPeriod(guildId, participantIds, period.startDate(), period.endDate())
                .forEach(award -> awardCounts.merge(award.key().participantId(), 1, Integer::sum));
        List<RecordEventSnapshot> recordEvents = events.findForReportPeriod(guildId, period);
        return new ReportHighlightFacts(awardCounts, recordEvents);
    }
}
