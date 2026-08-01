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
        Map<LocalDate, Set<Long>> activeParticipantIdsByDay,
        Set<LocalDate> sharedPossibleDays) {
    public ReportParticipantBasis {
        Objects.requireNonNull(period, "period");
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        activeParticipantIdsByDay = immutableDailyParticipants(activeParticipantIdsByDay);
        sharedPossibleDays = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(sharedPossibleDays, "sharedPossibleDays")));
    }

    private static Map<LocalDate, Set<Long>> immutableDailyParticipants(Map<LocalDate, Set<Long>> dailyParticipants) {
        Objects.requireNonNull(dailyParticipants, "activeParticipantIdsByDay");
        Map<LocalDate, Set<Long>> copy = new LinkedHashMap<>();
        dailyParticipants.forEach((date, ids) -> copy.put(
                Objects.requireNonNull(date, "active participant date"),
                Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(ids, "active participant ids")))));
        return Collections.unmodifiableMap(copy);
    }
}
