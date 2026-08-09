package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportNoOp;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportParticipantSection;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportResult;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportSharedSection;
import de.venomenon.gridwordsbot.domain.reporting.ReportDayAndStreakProjection;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantBasis;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantDayAndStreakSnapshot;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportPlayerGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.domain.reporting.ReportHighlightFacts;
import de.venomenon.gridwordsbot.port.out.ReportHighlightQuery;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;

/** Assembles existing participant, game-statistics, and day-and-streak projections into one report result. */
public final class PeriodicReportUseCase {
    private final ReportParticipantProjector participantProjector;
    private final ReportGameStatisticsProjector gameStatisticsProjector;
    private final ReportDayAndStreakProjector dayAndStreakProjector;
    private final ReportHighlightQuery highlights;

    public PeriodicReportUseCase(
            ReportParticipantProjector participantProjector,
            ReportGameStatisticsProjector gameStatisticsProjector,
            ReportDayAndStreakProjector dayAndStreakProjector) {
        this(participantProjector, gameStatisticsProjector, dayAndStreakProjector,
                (guildId, period, participantIds) -> ReportHighlightFacts.empty());
    }

    public PeriodicReportUseCase(
            ReportParticipantProjector participantProjector,
            ReportGameStatisticsProjector gameStatisticsProjector,
            ReportDayAndStreakProjector dayAndStreakProjector,
            ReportHighlightQuery highlights) {
        this.participantProjector = Objects.requireNonNull(participantProjector, "participantProjector");
        this.gameStatisticsProjector = Objects.requireNonNull(gameStatisticsProjector, "gameStatisticsProjector");
        this.dayAndStreakProjector = Objects.requireNonNull(dayAndStreakProjector, "dayAndStreakProjector");
        this.highlights = Objects.requireNonNull(highlights, "highlights");
    }

    public PeriodicReportResult generate(ReportType reportType, ReportPeriod period) {
        return generate(reportType, period, 0L);
    }

    /** Generates a report with its Guild-scoped, already materialized highlight facts. */
    public PeriodicReportResult generate(long guildId, ReportType reportType, ReportPeriod period) {
        if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
        return generate(reportType, period, guildId);
    }

    private PeriodicReportResult generate(ReportType reportType, ReportPeriod period, long guildId) {
        Objects.requireNonNull(reportType, "reportType");
        Objects.requireNonNull(period, "period");
        ReportParticipantBasis basis = Objects.requireNonNull(participantProjector.project(period), "participant basis");
        if (!basis.period().equals(period)) {
            throw new IllegalStateException("participant basis period must match the requested report period");
        }
        if (basis.participants().isEmpty()) return new PeriodicReportNoOp(reportType, period);

        Set<Long> participantIds = participantIds(basis.participants());
        Map<Long, ReportPlayerGameStatistics> statisticsByParticipant = sectionsByParticipantId(
                gameStatisticsProjector.project(basis), participantIds, ReportPlayerGameStatistics::discordUserId, "game statistics");
        ReportDayAndStreakProjection dayAndStreaks = dayAndStreakProjector.project(basis);
        Map<Long, ReportParticipantDayAndStreakSnapshot> dayAndStreaksByParticipant = sectionsByParticipantId(
                dayAndStreaks.participants(), participantIds,
                ReportParticipantDayAndStreakSnapshot::discordUserId, "day and streak snapshots");

        List<PeriodicReportParticipantSection> participants = basis.participants().stream()
                .map(participant -> personalSection(participant, statisticsByParticipant, dayAndStreaksByParticipant))
                .toList();
        ReportHighlightFacts reportHighlights = guildId == 0
                ? ReportHighlightFacts.empty()
                : highlights.find(guildId, period, participantIds);
        return new PeriodicReport(reportType, period, participants,
                new PeriodicReportSharedSection(dayAndStreaks.sharedDayCounts(), dayAndStreaks.sharedStreaks()), reportHighlights);
    }


    private static PeriodicReportParticipantSection personalSection(
            ReportParticipant participant,
            Map<Long, ReportPlayerGameStatistics> statisticsByParticipant,
            Map<Long, ReportParticipantDayAndStreakSnapshot> dayAndStreaksByParticipant) {
        long participantId = participant.discordUserId();
        ReportParticipantDayAndStreakSnapshot snapshot = dayAndStreaksByParticipant.get(participantId);
        return new PeriodicReportParticipantSection(
                participant,
                statisticsByParticipant.get(participantId),
                snapshot.dayCounts(),
                snapshot.streaks());
    }

    private static Set<Long> participantIds(List<ReportParticipant> participants) {
        Set<Long> participantIds = new LinkedHashSet<>();
        for (ReportParticipant participant : participants) {
            if (!participantIds.add(participant.discordUserId())) {
                throw new IllegalStateException("participant basis contains duplicate Discord user ids");
            }
        }
        return participantIds;
    }

    private static <T> Map<Long, T> sectionsByParticipantId(
            List<T> sections, Set<Long> participantIds, ToLongFunction<T> participantId, String sectionName) {
        Map<Long, T> indexedSections = new LinkedHashMap<>();
        for (T section : sections) {
            long discordUserId = participantId.applyAsLong(Objects.requireNonNull(section, sectionName));
            if (!participantIds.contains(discordUserId)) {
                throw new IllegalStateException(sectionName + " contain a foreign Discord user id");
            }
            if (indexedSections.putIfAbsent(discordUserId, section) != null) {
                throw new IllegalStateException(sectionName + " contain duplicate Discord user ids");
            }
        }
        if (indexedSections.size() != participantIds.size()) {
            throw new IllegalStateException(sectionName + " are missing participants");
        }
        return indexedSections;
    }
}
