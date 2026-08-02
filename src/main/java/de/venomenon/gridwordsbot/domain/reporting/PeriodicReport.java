package de.venomenon.gridwordsbot.domain.reporting;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The complete immutable report model for one report type and inclusive period. */
public record PeriodicReport(
        ReportType reportType,
        ReportPeriod period,
        List<PeriodicReportParticipantSection> participants,
        PeriodicReportSharedSection shared) implements PeriodicReportResult {
    public PeriodicReport {
        Objects.requireNonNull(reportType, "reportType");
        Objects.requireNonNull(period, "period");
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        Objects.requireNonNull(shared, "shared");
        if (participants.isEmpty()) throw new IllegalArgumentException("periodic report needs participants");
        Set<Long> participantIds = new HashSet<>();
        for (PeriodicReportParticipantSection participant : participants) {
            if (!participantIds.add(Objects.requireNonNull(participant, "participant section")
                    .participant().discordUserId())) {
                throw new IllegalArgumentException("periodic report participant ids must be unique");
            }
        }
    }
}
