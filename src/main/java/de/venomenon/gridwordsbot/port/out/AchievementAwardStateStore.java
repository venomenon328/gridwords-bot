package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import java.util.List;
import java.time.LocalDate;
import java.util.Set;
import java.util.Optional;

/** Persistence boundary for the materialized, lock-versioned current Achievement award state. */
public interface AchievementAwardStateStore {
    Optional<AchievementAwardState.Snapshot> find(AchievementAwardState.Key key);
    List<AchievementAwardState.Snapshot> findAll(long guildId, long participantId);
    /** ACTIVE materialized awards with a business earned-on date in the inclusive period. */
    default List<AchievementAwardState.Snapshot> findActiveForPeriod(
            long guildId, Set<Long> participantIds, LocalDate startDate, LocalDate endDate) { return List.of(); }
    AchievementAwardState.InitializationResult initialize(AchievementAwardState.Key key, AchievementAwardState.Write write);
    AchievementAwardState.UpdateResult update(
            AchievementAwardState.Key key,
            AchievementAwardState.LockVersion expectedLockVersion,
            AchievementAwardState.Write write);
}
