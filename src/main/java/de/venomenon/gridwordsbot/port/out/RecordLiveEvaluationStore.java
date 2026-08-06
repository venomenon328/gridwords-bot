package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationKey;
import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistent, token-fenced work queue for versioned live record evaluations. */
public interface RecordLiveEvaluationStore {
    RecordLiveEvaluationSnapshot register(RecordLiveEvaluationKey key, RecordProcessingOrigin processingOrigin);
    Optional<RecordLiveEvaluationSnapshot> find(RecordLiveEvaluationKey key);
    List<RecordLiveEvaluationSnapshot> findAll(long guildId, long gameResultId);
    Optional<RecordLiveEvaluationClaim> claimNext(RecordLeaseClaimRequest request);
    /** Locks and validates the currently owned work immediately before coupled writes. */
    default boolean fence(RecordLiveEvaluationKey key, UUID token, Instant now) { return false; }
    boolean renewLease(RecordLiveEvaluationKey key, UUID token, RecordLeaseClaimRequest request);
    boolean markSucceeded(RecordLiveEvaluationKey key, UUID token, Instant completedAt);
    boolean markRetryableFailure(
            RecordLiveEvaluationKey key,
            UUID token,
            RecordWorkFailure failure,
            Instant nextRetryAt);
    boolean markPermanentFailure(
            RecordLiveEvaluationKey key,
            UUID token,
            RecordWorkFailure failure,
            Instant completedAt);
}
