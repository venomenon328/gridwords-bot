package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementMessage;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementClaim;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistent desired record-announcement projection; this port never performs Discord I/O. */
public interface RecordAnnouncementStore {
    RecordAnnouncementSnapshot registerOrUpdate(RecordAnnouncementRegistration registration);
    Optional<RecordAnnouncementSnapshot> find(RecordAnnouncementKey key);
    default List<RecordAnnouncementSnapshot> findByEventId(UUID eventId) { return List.of(); }
    Optional<RecordLeaseClaim> claim(RecordAnnouncementKey key, RecordLeaseClaimRequest request);
    /** Atomically claims the next deterministic delivery candidate; implementations must not hold it across Discord I/O. */
    default Optional<RecordAnnouncementClaim> claimNext(RecordLeaseClaimRequest request, boolean publicAnnouncementsEnabled) {
        return Optional.empty();
    }
    boolean renewLease(RecordAnnouncementKey key, UUID token, RecordLeaseClaimRequest request);
    boolean replaceMessages(RecordAnnouncementKey key, UUID token, List<RecordAnnouncementMessage> messages);
    boolean markSynchronized(RecordAnnouncementKey key, UUID token, Instant synchronizedAt);
    boolean markRetryableFailure(RecordAnnouncementKey key, UUID token, RecordWorkFailure failure, Instant nextRetryAt);
    boolean markPermanentFailure(RecordAnnouncementKey key, UUID token, RecordWorkFailure failure, Instant completedAt);
    boolean markExternallyRemoved(RecordAnnouncementKey key, UUID token, Instant removedAt);
    /** Stops never-published CREATE work permanently; later activation must not create a backlog. */
    default boolean markSuppressed(RecordAnnouncementKey key, UUID token, Instant suppressedAt) { return false; }
    /** Atomically suppresses queued never-published creates while public announcements are disabled. */
    default int suppressPendingCreates(Instant suppressedAt) { return 0; }
}
