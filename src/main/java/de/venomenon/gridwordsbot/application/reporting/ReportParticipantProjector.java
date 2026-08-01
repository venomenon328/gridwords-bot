package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantBasis;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.port.out.ReportParticipantQuery;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projects report participants and possible days from historical participation facts only. */
public final class ReportParticipantProjector {
    private final ReportParticipantQuery participants;

    public ReportParticipantProjector(ReportParticipantQuery participants) {
        this.participants = participants;
    }

    public ReportParticipantBasis project(ReportPeriod period) {
        List<ReportParticipantQuery.ParticipantProfile> profiles = participants.findParticipantsTouching(period).stream()
                .sorted(Comparator.comparing(ReportParticipantQuery.ParticipantProfile::firstParticipationStart)
                        .thenComparingLong(ReportParticipantQuery.ParticipantProfile::discordUserId))
                .toList();
        Map<LocalDate, Set<Long>> activeParticipantIdsByDay = everyDay(period);
        List<ReportParticipant> projectedParticipants = profiles.stream()
                .map(profile -> projectParticipant(profile, period, activeParticipantIdsByDay))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::orElseThrow)
                .toList();
        Set<LocalDate> sharedPossibleDays = activeParticipantIdsByDay.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new ReportParticipantBasis(period, projectedParticipants, activeParticipantIdsByDay, sharedPossibleDays);
    }

    private static java.util.Optional<ReportParticipant> projectParticipant(
            ReportParticipantQuery.ParticipantProfile profile,
            ReportPeriod period,
            Map<LocalDate, Set<Long>> activeParticipantIdsByDay) {
        Set<LocalDate> participationDays = new LinkedHashSet<>();
        profile.participationPeriods().forEach(participationPeriod -> addParticipationDays(
                participationPeriod, period, participationDays, activeParticipantIdsByDay, profile.discordUserId()));
        if (participationDays.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(new ReportParticipant(
                profile.discordUserId(), profile.displayName(), profile.firstParticipationStart(), participationDays.stream().sorted().toList()));
    }

    private static void addParticipationDays(
            ParticipationPeriod participationPeriod,
            ReportPeriod period,
            Set<LocalDate> participationDays,
            Map<LocalDate, Set<Long>> activeParticipantIdsByDay,
            long participantId) {
        LocalDate firstDay = participationPeriod.activeFrom().isAfter(period.startDate())
                ? participationPeriod.activeFrom() : period.startDate();
        LocalDate lastDay = participationPeriod.inactiveFrom() == null || participationPeriod.inactiveFrom().isAfter(period.endDate())
                ? period.endDate() : participationPeriod.inactiveFrom().minusDays(1);
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            participationDays.add(day);
            activeParticipantIdsByDay.get(day).add(participantId);
        }
    }

    private static Map<LocalDate, Set<Long>> everyDay(ReportPeriod period) {
        Map<LocalDate, Set<Long>> dailyParticipants = new LinkedHashMap<>();
        for (LocalDate day = period.startDate(); !day.isAfter(period.endDate()); day = day.plusDays(1)) {
            dailyParticipants.put(day, new LinkedHashSet<>());
        }
        return dailyParticipants;
    }
}
