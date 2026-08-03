package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
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
        Map<LocalDate, Set<Long>> gridWordsParticipantIdsByDay = everyDay(period);
        Map<LocalDate, Set<Long>> quadWordsParticipantIdsByDay = everyDay(period);
        List<ReportParticipant> projectedParticipants = profiles.stream()
                .map(profile -> projectParticipant(
                        profile, period, gridWordsParticipantIdsByDay, quadWordsParticipantIdsByDay))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::orElseThrow)
                .toList();
        Map<LocalDate, Set<Long>> unionParticipantIdsByDay = everyDay(period);
        Map<LocalDate, Set<Long>> bothGamesParticipantIdsByDay = everyDay(period);
        for (LocalDate day : unionParticipantIdsByDay.keySet()) {
            unionParticipantIdsByDay.get(day).addAll(gridWordsParticipantIdsByDay.get(day));
            unionParticipantIdsByDay.get(day).addAll(quadWordsParticipantIdsByDay.get(day));
            bothGamesParticipantIdsByDay.get(day).addAll(gridWordsParticipantIdsByDay.get(day));
            bothGamesParticipantIdsByDay.get(day).retainAll(quadWordsParticipantIdsByDay.get(day));
        }
        return new ReportParticipantBasis(
                period,
                projectedParticipants,
                unionParticipantIdsByDay,
                gridWordsParticipantIdsByDay,
                quadWordsParticipantIdsByDay,
                bothGamesParticipantIdsByDay);
    }

    private static java.util.Optional<ReportParticipant> projectParticipant(
            ReportParticipantQuery.ParticipantProfile profile,
            ReportPeriod period,
            Map<LocalDate, Set<Long>> gridWordsParticipantIdsByDay,
            Map<LocalDate, Set<Long>> quadWordsParticipantIdsByDay) {
        Set<LocalDate> gridWordsParticipationDays = new LinkedHashSet<>();
        Set<LocalDate> quadWordsParticipationDays = new LinkedHashSet<>();
        profile.participationPeriods().forEach(participationPeriod -> addParticipationDays(
                participationPeriod,
                period,
                participationPeriod.gameType() == GameType.GRIDWORDS
                        ? gridWordsParticipationDays : quadWordsParticipationDays,
                participationPeriod.gameType() == GameType.GRIDWORDS
                        ? gridWordsParticipantIdsByDay : quadWordsParticipantIdsByDay,
                profile.discordUserId()));
        Set<LocalDate> unionParticipationDays = new LinkedHashSet<>(gridWordsParticipationDays);
        unionParticipationDays.addAll(quadWordsParticipationDays);
        Set<LocalDate> bothGamesParticipationDays = new LinkedHashSet<>(gridWordsParticipationDays);
        bothGamesParticipationDays.retainAll(quadWordsParticipationDays);
        if (unionParticipationDays.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(new ReportParticipant(
                profile.discordUserId(),
                profile.displayName(),
                profile.firstParticipationStart(),
                unionParticipationDays.stream().sorted().toList(),
                gridWordsParticipationDays.stream().sorted().toList(),
                quadWordsParticipationDays.stream().sorted().toList(),
                bothGamesParticipationDays.stream().sorted().toList()));
    }

    private static void addParticipationDays(
            GameParticipationPeriod participationPeriod,
            ReportPeriod period,
            Set<LocalDate> participationDays,
            Map<LocalDate, Set<Long>> gameParticipantIdsByDay,
            long participantId) {
        LocalDate firstDay = participationPeriod.activeFrom().isAfter(period.startDate())
                ? participationPeriod.activeFrom() : period.startDate();
        LocalDate lastDay = participationPeriod.inactiveFrom() == null || participationPeriod.inactiveFrom().isAfter(period.endDate())
                ? period.endDate() : participationPeriod.inactiveFrom().minusDays(1);
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            participationDays.add(day);
            gameParticipantIdsByDay.get(day).add(participantId);
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
