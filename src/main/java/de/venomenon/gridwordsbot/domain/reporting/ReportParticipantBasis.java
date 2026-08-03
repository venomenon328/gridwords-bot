package de.venomenon.gridwordsbot.domain.reporting;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Transport-neutral participant and possible-day facts for a completed report period. */
public record ReportParticipantBasis(
        ReportPeriod period,
        List<ReportParticipant> participants,
        Map<LocalDate, Set<Long>> unionParticipantIdsByDay,
        Map<LocalDate, Set<Long>> gridWordsParticipantIdsByDay,
        Map<LocalDate, Set<Long>> quadWordsParticipantIdsByDay,
        Map<LocalDate, Set<Long>> bothGamesParticipantIdsByDay) {
    public ReportParticipantBasis {
        Objects.requireNonNull(period, "period");
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        unionParticipantIdsByDay = immutableDailyParticipants(unionParticipantIdsByDay, "unionParticipantIdsByDay");
        gridWordsParticipantIdsByDay = immutableDailyParticipants(gridWordsParticipantIdsByDay, "gridWordsParticipantIdsByDay");
        quadWordsParticipantIdsByDay = immutableDailyParticipants(quadWordsParticipantIdsByDay, "quadWordsParticipantIdsByDay");
        bothGamesParticipantIdsByDay = immutableDailyParticipants(bothGamesParticipantIdsByDay, "bothGamesParticipantIdsByDay");
        if (!unionParticipantIdsByDay.keySet().equals(gridWordsParticipantIdsByDay.keySet())
                || !unionParticipantIdsByDay.keySet().equals(quadWordsParticipantIdsByDay.keySet())
                || !unionParticipantIdsByDay.keySet().equals(bothGamesParticipantIdsByDay.keySet())) {
            throw new IllegalArgumentException("daily participation maps must cover the same dates");
        }
        for (LocalDate day : unionParticipantIdsByDay.keySet()) {
            Set<Long> expectedUnion = new LinkedHashSet<>(gridWordsParticipantIdsByDay.get(day));
            expectedUnion.addAll(quadWordsParticipantIdsByDay.get(day));
            Set<Long> expectedBoth = new LinkedHashSet<>(gridWordsParticipantIdsByDay.get(day));
            expectedBoth.retainAll(quadWordsParticipantIdsByDay.get(day));
            if (!unionParticipantIdsByDay.get(day).equals(expectedUnion)
                    || !bothGamesParticipantIdsByDay.get(day).equals(expectedBoth)) {
                throw new IllegalArgumentException("daily union and both-games participation must match game histories");
            }
        }
    }

    public Set<LocalDate> sharedPossibleDays() {
        Set<LocalDate> days = new LinkedHashSet<>();
        bothGamesParticipantIdsByDay.forEach((day, ids) -> {
            if (ids.size() >= 2) days.add(day);
        });
        return Collections.unmodifiableSet(days);
    }

    private static Map<LocalDate, Set<Long>> immutableDailyParticipants(
            Map<LocalDate, Set<Long>> dailyParticipants, String name) {
        Objects.requireNonNull(dailyParticipants, name);
        Map<LocalDate, Set<Long>> copy = new LinkedHashMap<>();
        dailyParticipants.forEach((date, ids) -> copy.put(
                Objects.requireNonNull(date, "active participant date"),
                Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(ids, "active participant ids")))));
        return Collections.unmodifiableMap(copy);
    }
}
