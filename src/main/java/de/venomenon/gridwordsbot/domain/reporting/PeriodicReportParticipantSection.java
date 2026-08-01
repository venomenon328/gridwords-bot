package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** One ordered personal section of a transport-neutral periodic report. */
public record PeriodicReportParticipantSection(
        ReportParticipant participant,
        ReportPlayerGameStatistics gameStatistics,
        ReportPersonalDayCounts dayCounts,
        ReportPersonalStreaks streaks) {
    public PeriodicReportParticipantSection {
        Objects.requireNonNull(participant, "participant");
        Objects.requireNonNull(gameStatistics, "gameStatistics");
        Objects.requireNonNull(dayCounts, "dayCounts");
        Objects.requireNonNull(streaks, "streaks");
        if (gameStatistics.discordUserId() != participant.discordUserId()) {
            throw new IllegalArgumentException("game statistics must belong to the participant");
        }
        if (dayCounts.participationDays() != participant.participationDays().size()) {
            throw new IllegalArgumentException("day counts must match participant participation days");
        }
    }
}
