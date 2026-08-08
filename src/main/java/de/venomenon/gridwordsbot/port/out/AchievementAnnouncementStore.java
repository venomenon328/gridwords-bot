package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistent desired Achievement-announcement projection; this port never performs Discord I/O. */
public interface AchievementAnnouncementStore {
    AchievementAnnouncement.Snapshot register(AchievementAnnouncement.Registration registration);
    Optional<AchievementAnnouncement.Snapshot> find(AchievementAnnouncement.Key key);
    /** Pending live handoffs that may still be reduced after a canonical correction. */
    List<AchievementAnnouncement.Snapshot> findPending(long guildId, long participantId);
    List<AchievementAnnouncement.Item> findItems(AchievementAnnouncement.Key key);
    boolean updatePendingContent(AchievementAnnouncement.Key key, String rendererVersion, String contentFingerprint);
    boolean replaceItems(AchievementAnnouncement.Key key, List<UUID> eventIds);
    /** Revalidation immediately before Discord create; only the owning delivery claim may change it. */
    default boolean updateClaimedContent(
            AchievementAnnouncement.Key key, UUID token, String rendererVersion, String contentFingerprint) {
        throw new UnsupportedOperationException("claimed announcement updates are not available");
    }
    default boolean replaceClaimedItems(AchievementAnnouncement.Key key, UUID token, List<UUID> eventIds) {
        throw new UnsupportedOperationException("claimed announcement item replacement is not available");
    }
    boolean wasSynchronized(long guildId, long participantId, AchievementKey achievementKey);
    Optional<AchievementWork.LeaseClaim> claim(
            AchievementAnnouncement.Key key, AchievementWork.LeaseClaimRequest request);
    Optional<AchievementAnnouncement.Snapshot> claimNext(AchievementWork.LeaseClaimRequest request);
    boolean renewLease(
            AchievementAnnouncement.Key key, UUID token, AchievementWork.LeaseClaimRequest request);
    boolean markDelivered(
            AchievementAnnouncement.Key key, UUID token, long discordMessageId, Instant deliveredAt);
    boolean markSynchronized(AchievementAnnouncement.Key key, UUID token, Instant synchronizedAt);
    boolean markRetryableFailure(
            AchievementAnnouncement.Key key,
            UUID token,
            AchievementWork.Failure failure,
            Instant nextRetryAt);
    boolean markPermanentFailure(
            AchievementAnnouncement.Key key,
            UUID token,
            AchievementWork.Failure failure,
            Instant completedAt);
    boolean markExternallyRemoved(AchievementAnnouncement.Key key, UUID token, Instant removedAt);
    boolean markSuppressed(AchievementAnnouncement.Key key, Instant suppressedAt);
    default boolean markSuppressed(AchievementAnnouncement.Key key, UUID token, Instant suppressedAt) {
        throw new UnsupportedOperationException("token-bound suppression is not available");
    }
}
