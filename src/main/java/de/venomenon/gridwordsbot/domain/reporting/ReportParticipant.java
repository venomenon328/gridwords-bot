package de.venomenon.gridwordsbot.domain.reporting;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** One report participant with the historical facts relevant to the selected period. */
public record ReportParticipant(
        long discordUserId,
        String displayName,
        LocalDate firstParticipationStart,
        List<LocalDate> participationDays) {
    public ReportParticipant {
        if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        Objects.requireNonNull(firstParticipationStart, "firstParticipationStart");
        participationDays = List.copyOf(Objects.requireNonNull(participationDays, "participationDays"));
        if (participationDays.isEmpty()) throw new IllegalArgumentException("participant needs participation days");
    }
}
