package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaim;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaimRequest;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailure;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryPageProgress;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliverySnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent boundary for one logical report delivery. Implementations must atomically fence every mutable
 * operation by the supplied claim token and never hold a database transaction across message gateway I/O.
 */
public interface PeriodicReportDeliveryStore {

    /** Registers immutable delivery facts idempotently; conflicting facts for the same key are rejected. */
    PeriodicReportDeliverySnapshot register(PeriodicReportDeliveryRegistration registration);

    Optional<PeriodicReportDeliverySnapshot> find(PeriodicReportDeliveryKey key);

    /** Atomically claims only open/retryable work or work whose persisted lease has expired. */
    Optional<PeriodicReportDeliveryClaim> claim(
            PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaimRequest request);

    /** Persists one confirmed page only when the supplied token still owns the delivery. */
    boolean recordPage(PeriodicReportDeliveryKey key, UUID claimToken, PeriodicReportDeliveryPageProgress progress);

    boolean markSucceeded(PeriodicReportDeliveryKey key, UUID claimToken, Instant completedAt);

    boolean markNoOp(PeriodicReportDeliveryKey key, UUID claimToken, Instant completedAt);

    boolean markRetryableFailure(
            PeriodicReportDeliveryKey key,
            UUID claimToken,
            PeriodicReportDeliveryFailure failure,
            Instant nextRetryAt);

    boolean markPermanentFailure(
            PeriodicReportDeliveryKey key,
            UUID claimToken,
            PeriodicReportDeliveryFailure failure,
            Instant completedAt);

    /** Expires unfinished work once its half-open catch-up window has ended. */
    boolean markExpired(PeriodicReportDeliveryKey key, Instant completedAt);
}
