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
    List<AchievementAnnouncement.Item> findItems(AchievementAnnouncement.Key key);
    boolean updatePendingContent(AchievementAnnouncement.Key key, String rendererVersion, String contentFingerprint);
    boolean replaceItems(AchievementAnnouncement.Key key, List<UUID> eventIds);
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
}
