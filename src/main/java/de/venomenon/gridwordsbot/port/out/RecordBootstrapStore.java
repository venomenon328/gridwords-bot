package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Token-fenced coordination state only; historical bootstrap orchestration is deliberately outside this port. */
public interface RecordBootstrapStore {
    RecordBootstrapSnapshot register(RecordBootstrapKey key);
    Optional<RecordBootstrapSnapshot> find(RecordBootstrapKey key);
    Optional<RecordLeaseClaim> claim(RecordBootstrapKey key, RecordLeaseClaimRequest request);
    boolean renewLease(RecordBootstrapKey key, UUID token, RecordLeaseClaimRequest request);
    boolean markSucceeded(RecordBootstrapKey key, UUID token, Instant completedAt);
    boolean markRetryableFailure(RecordBootstrapKey key, UUID token, RecordWorkFailure failure, Instant nextRetryAt);
    boolean markPermanentFailure(RecordBootstrapKey key, UUID token, RecordWorkFailure failure, Instant completedAt);
}
