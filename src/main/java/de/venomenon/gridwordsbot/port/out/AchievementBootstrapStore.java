package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Token-fenced coordination state for Achievement bootstrap; orchestration is outside this port. */
public interface AchievementBootstrapStore {
    AchievementWork.BootstrapSnapshot register(AchievementWork.BootstrapKey key);
    Optional<AchievementWork.BootstrapSnapshot> find(AchievementWork.BootstrapKey key);
    Optional<AchievementWork.LeaseClaim> claim(AchievementWork.BootstrapKey key, AchievementWork.LeaseClaimRequest request);
    boolean renewLease(AchievementWork.BootstrapKey key, UUID token, AchievementWork.LeaseClaimRequest request);
    boolean markSucceeded(AchievementWork.BootstrapKey key, UUID token, Instant completedAt);
    boolean markRetryableFailure(
            AchievementWork.BootstrapKey key,
            UUID token,
            AchievementWork.Failure failure,
            Instant nextRetryAt);
    boolean markPermanentFailure(
            AchievementWork.BootstrapKey key,
            UUID token,
            AchievementWork.Failure failure,
            Instant completedAt);
}
