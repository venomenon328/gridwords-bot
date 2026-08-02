package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** Derived day counts and final streaks for one visible report participant. */
public record ReportParticipantDayAndStreakSnapshot(
        long discordUserId, ReportPersonalDayCounts dayCounts, ReportPersonalStreaks streaks) {
    public ReportParticipantDayAndStreakSnapshot {
        if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
        Objects.requireNonNull(dayCounts, "dayCounts");
        Objects.requireNonNull(streaks, "streaks");
    }
}
