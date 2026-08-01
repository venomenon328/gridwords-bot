package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Read boundary for the historical participant facts needed to project one report period. */
public interface ReportParticipantQuery {
    List<ParticipantProfile> findParticipantsTouching(ReportPeriod period);

    /** A current profile and its periods that overlap the requested report period. */
    record ParticipantProfile(
            long discordUserId,
            String displayName,
            LocalDate firstParticipationStart,
            List<ParticipationPeriod> participationPeriods) {
        public ParticipantProfile {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
            if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            Objects.requireNonNull(firstParticipationStart, "firstParticipationStart");
            participationPeriods = List.copyOf(Objects.requireNonNull(participationPeriods, "participationPeriods"));
            if (participationPeriods.isEmpty()) {
                throw new IllegalArgumentException("participant profile needs participation periods");
            }
            if (participationPeriods.stream().anyMatch(period -> period.playerId() != discordUserId)) {
                throw new IllegalArgumentException("participation periods must belong to the participant");
            }
        }
    }
}
