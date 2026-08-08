package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only audit boundary for Achievement transition facts. */
public interface AchievementEventStore {
    AchievementEventFact.AppendResult append(AchievementEventFact.Draft draft);
    Optional<AchievementEventFact.Snapshot> find(UUID eventId);
    Optional<AchievementEventFact.Snapshot> findByIdempotencyKey(String idempotencyKey);
    List<AchievementEventFact.Snapshot> findByParticipant(long guildId, long participantId);
}
