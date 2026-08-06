package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.record.RecordDayCloseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseKey;
import de.venomenon.gridwordsbot.domain.record.RecordDayCloseSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Persistent marker and claim boundary for idempotent, chronological record day closes. */
public interface RecordDayCloseStore {
    RecordDayCloseSnapshot register(RecordDayCloseKey key);
    Optional<RecordDayCloseSnapshot> find(RecordDayCloseKey key);
    Optional<LocalDate> latestSucceededDate(long guildId, String definitionVersion);
    Optional<RecordDayCloseClaim> claim(RecordDayCloseKey key, RecordLeaseClaimRequest request);
    boolean fence(RecordDayCloseKey key, UUID token, Instant now);
    boolean renewLease(RecordDayCloseKey key, UUID token, RecordLeaseClaimRequest request);
    boolean markSucceeded(RecordDayCloseKey key, UUID token, Instant completedAt);
    boolean markRetryableFailure(RecordDayCloseKey key, UUID token, RecordWorkFailure failure, Instant nextRetryAt);
    boolean markPermanentFailure(RecordDayCloseKey key, UUID token, RecordWorkFailure failure, Instant completedAt);
}
