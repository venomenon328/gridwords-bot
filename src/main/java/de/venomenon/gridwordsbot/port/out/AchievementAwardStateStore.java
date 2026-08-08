package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for the materialized, lock-versioned current Achievement award state. */
public interface AchievementAwardStateStore {
    Optional<AchievementAwardState.Snapshot> find(AchievementAwardState.Key key);
    List<AchievementAwardState.Snapshot> findAll(long guildId, long participantId);
    AchievementAwardState.InitializationResult initialize(AchievementAwardState.Key key, AchievementAwardState.Write write);
    AchievementAwardState.UpdateResult update(
            AchievementAwardState.Key key,
            AchievementAwardState.LockVersion expectedLockVersion,
            AchievementAwardState.Write write);
}
