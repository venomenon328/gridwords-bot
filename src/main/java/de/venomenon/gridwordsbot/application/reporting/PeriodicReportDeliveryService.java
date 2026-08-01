package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaim;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaimRequest;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryContent;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailure;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailureCategory;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryPageProgress;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliverySnapshot;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportNoOp;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportResult;
import de.venomenon.gridwordsbot.domain.reporting.RenderedPeriodicReport;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Delivers one rendered periodic report and resumes only persistently confirmed page progress. Store operations
 * are deliberately short and surround, rather than contain, each Discord call.
 */
public final class PeriodicReportDeliveryService {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration RETRY_BASE_DELAY = Duration.ofSeconds(30);
    private static final Duration RETRY_MAX_DELAY = Duration.ofMinutes(15);

    private final PeriodicReportDeliveryStore store;
    private final PeriodicReportMessageGateway messages;
    private final PeriodicReportRenderer renderer;
    private final Clock clock;

    public PeriodicReportDeliveryService(
            PeriodicReportDeliveryStore store,
            PeriodicReportMessageGateway messages,
            PeriodicReportRenderer renderer,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Registers immutable facts, then resumes or starts delivery without repeating confirmed pages. */
    public void deliver(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryMetadata metadata,
            PeriodicReportResult result) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(result, "result");
        validateReportIdentity(key, metadata, result);

        Optional<PeriodicReportDeliverySnapshot> existing = store.find(key);
        if (existing.filter(snapshot -> snapshot.state().isTerminal()).isPresent()) {
            return;
        }

        RenderedPeriodicReport rendered = result instanceof PeriodicReport report ? renderer.render(report) : null;
        PeriodicReportDeliveryRegistration registration = registration(key, metadata, rendered);
        PeriodicReportDeliverySnapshot snapshot = store.register(registration);
        if (!snapshot.registration().equals(registration) || snapshot.state().isTerminal()) {
            return;
        }

        Instant claimedAt = clock.instant();
        Optional<PeriodicReportDeliveryClaim> claim = store.claim(
                key, new PeriodicReportDeliveryClaimRequest(claimedAt, claimedAt.plus(LEASE)));
        if (claim.isEmpty()) {
            return;
        }

        Optional<PeriodicReportDeliverySnapshot> ownedSnapshot = store.find(key)
                .filter(current -> current.state() == PeriodicReportDeliveryState.CLAIMED)
                .filter(current -> current.claim().map(PeriodicReportDeliveryClaim::token).filter(claim.get().token()::equals).isPresent());
        if (ownedSnapshot.isEmpty()) {
            return;
        }

        try {
            if (rendered == null) {
                store.markNoOp(key, claim.get().token(), clock.instant());
                return;
            }
            if (sendPages(key, claim.get(), rendered, ownedSnapshot.get().pageProgress().size())) {
                store.markSucceeded(key, claim.get().token(), clock.instant());
            }
        } catch (PeriodicReportMessageGateway.PermanentMessageException exception) {
            markPermanentFailure(key, claim.get(), "periodic report Discord delivery permanently failed");
        } catch (PeriodicReportMessageGateway.UnknownMessageException | PeriodicReportMessageGateway.MissingMessageException exception) {
            markRetryableFailure(key, claim.get(), snapshot.attemptCount(),
                    PeriodicReportDeliveryFailureCategory.UNKNOWN, "periodic report Discord delivery outcome is unknown");
        } catch (PeriodicReportMessageGateway.RetryableMessageException exception) {
            markRetryableFailure(key, claim.get(), snapshot.attemptCount(),
                    PeriodicReportDeliveryFailureCategory.RETRYABLE, "periodic report Discord delivery is retryable");
        } catch (RuntimeException exception) {
            markRetryableFailure(key, claim.get(), snapshot.attemptCount(),
                    PeriodicReportDeliveryFailureCategory.UNKNOWN, "unexpected periodic report delivery failure");
        }
    }

    private boolean sendPages(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            RenderedPeriodicReport rendered,
            int confirmedPageCount) {
        for (int index = confirmedPageCount; index < rendered.pages().size(); index++) {
            long messageId = messages.create(key.channelId(),
                    new PeriodicReportMessageGateway.ReportPage(index, rendered.pages().get(index)));
            if (!store.recordPage(key, claim.token(), new PeriodicReportDeliveryPageProgress(index, messageId))) {
                return false;
            }
        }
        return true;
    }

    private void markRetryableFailure(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            int priorAttemptCount,
            PeriodicReportDeliveryFailureCategory category,
            String safeMessage) {
        store.markRetryableFailure(key, claim.token(), new PeriodicReportDeliveryFailure(category, safeMessage),
                retryAt(clock.instant(), priorAttemptCount));
    }

    private void markPermanentFailure(PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaim claim, String safeMessage) {
        store.markPermanentFailure(key, claim.token(),
                new PeriodicReportDeliveryFailure(PeriodicReportDeliveryFailureCategory.PERMANENT, safeMessage), clock.instant());
    }

    static Instant retryAt(Instant failureAt, int priorAttemptCount) {
        Objects.requireNonNull(failureAt, "failureAt");
        if (priorAttemptCount < 0) {
            throw new IllegalArgumentException("priorAttemptCount must not be negative");
        }
        int shift = Math.min(priorAttemptCount, 30);
        Duration delay = RETRY_BASE_DELAY.multipliedBy(1L << shift);
        if (delay.compareTo(RETRY_MAX_DELAY) > 0) {
            delay = RETRY_MAX_DELAY;
        }
        return failureAt.plus(delay);
    }

    private static PeriodicReportDeliveryRegistration registration(
            PeriodicReportDeliveryKey key, PeriodicReportDeliveryMetadata metadata, RenderedPeriodicReport rendered) {
        return new PeriodicReportDeliveryRegistration(key, metadata, Optional.ofNullable(rendered)
                .map(value -> new PeriodicReportDeliveryContent(value.contentFingerprint(), value.pages().size())));
    }

    private static void validateReportIdentity(
            PeriodicReportDeliveryKey key, PeriodicReportDeliveryMetadata metadata, PeriodicReportResult result) {
        if (!key.periodStart().equals(metadata.period().startDate())) {
            throw new IllegalArgumentException("delivery key period must match metadata period");
        }
        if (result instanceof PeriodicReport report
                && (report.reportType() != key.reportType() || !report.period().equals(metadata.period()))) {
            throw new IllegalArgumentException("generated report must match delivery key and metadata");
        }
        if (result instanceof PeriodicReportNoOp noOp
                && (noOp.reportType() != key.reportType() || !noOp.period().equals(metadata.period()))) {
            throw new IllegalArgumentException("NO_OP report must match delivery key and metadata");
        }
    }
}
