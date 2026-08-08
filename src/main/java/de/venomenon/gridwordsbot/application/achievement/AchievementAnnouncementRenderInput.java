package de.venomenon.gridwordsbot.application.achievement;

import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import java.util.List;
import java.util.Objects;

/** Fully resolved persisted facts supplied to the pure Achievement renderer. */
public record AchievementAnnouncementRenderInput(
        AchievementAnnouncement.Snapshot announcement, List<AchievementEventFact.Snapshot> events, String participantDisplayName) {
    public AchievementAnnouncementRenderInput {
        Objects.requireNonNull(announcement, "announcement");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        Objects.requireNonNull(participantDisplayName, "participantDisplayName");
    }
}
