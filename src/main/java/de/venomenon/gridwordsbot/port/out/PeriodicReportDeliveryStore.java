package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaim;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryContent;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaimRequest;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryExpiration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailure;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryPageProgress;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliverySnapshot;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryScope;
import java.time.Instant;
import java.time.LocalDate;
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

    /** Finds the latest registered period start for one exact guild, channel, and report type scope. */
    Optional<LocalDate> findLatestPeriodStart(PeriodicReportDeliveryScope scope);

    /** Atomically materializes or preserves the snapshot resulting from an expired planned delivery. */
    PeriodicReportDeliverySnapshot expire(PeriodicReportDeliveryExpiration expiration, Instant completedAt);

    /** Atomically claims only open/retryable work or work whose persisted lease has expired. */
    Optional<PeriodicReportDeliveryClaim> claim(
            PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaimRequest request);

    /** Persists one confirmed page only when the supplied token still owns the delivery. */
    boolean recordPage(PeriodicReportDeliveryKey key, UUID claimToken, PeriodicReportDeliveryPageProgress progress);

    /** Replaces one missing confirmed page while retaining its visible position under the current claim. */
    boolean replacePage(PeriodicReportDeliveryKey key, UUID claimToken, PeriodicReportDeliveryPageProgress progress);

    /** Atomically discards a damaged page group and switches to a newly rendered fingerprint under the current claim. */
    boolean replaceContent(
            PeriodicReportDeliveryKey key,
            UUID claimToken,
            PeriodicReportDeliveryContent content);
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
