package de.venomenon.gridwordsbot.domain.reporting;

import java.util.List;
import java.util.Objects;

/** Transport-neutral package-four projection, deliberately not a complete periodic report. */
public record ReportDayAndStreakProjection(
        List<ReportParticipantDayAndStreakSnapshot> participants,
        ReportSharedDayCounts sharedDayCounts,
        ReportSharedStreaks sharedStreaks) {
    public ReportDayAndStreakProjection {
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        Objects.requireNonNull(sharedDayCounts, "sharedDayCounts");
        Objects.requireNonNull(sharedStreaks, "sharedStreaks");
    }
}
