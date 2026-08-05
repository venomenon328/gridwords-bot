package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementMessage;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistent desired record-announcement projection; this port never performs Discord I/O. */
public interface RecordAnnouncementStore {
    RecordAnnouncementSnapshot registerOrUpdate(RecordAnnouncementRegistration registration);
    Optional<RecordAnnouncementSnapshot> find(RecordAnnouncementKey key);
    Optional<RecordLeaseClaim> claim(RecordAnnouncementKey key, RecordLeaseClaimRequest request);
    boolean renewLease(RecordAnnouncementKey key, UUID token, RecordLeaseClaimRequest request);
    boolean replaceMessages(RecordAnnouncementKey key, UUID token, List<RecordAnnouncementMessage> messages);
    boolean markSynchronized(RecordAnnouncementKey key, UUID token, Instant synchronizedAt);
    boolean markRetryableFailure(RecordAnnouncementKey key, UUID token, RecordWorkFailure failure, Instant nextRetryAt);
    boolean markPermanentFailure(RecordAnnouncementKey key, UUID token, RecordWorkFailure failure, Instant completedAt);
    boolean markExternallyRemoved(RecordAnnouncementKey key, UUID token, Instant removedAt);
}
