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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Delivers reports with token-fenced persistence around every Discord call and reconciles damaged report groups
 * only inside their persisted half-open catch-up window.
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

    /** Registers immutable facts, resumes progress, and reconciles a succeeded snapshot only while repair is allowed. */
    public void deliver(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryMetadata metadata,
            PeriodicReportResult result) {
        deliver(key, metadata, result, false);
    }

    /**
     * Explicitly allows a succeeded snapshot to be replaced when its visible fingerprint changed.
     * The ordinary delivery path stays frozen; this is reserved for narrow operator-approved refresh triggers.
     */
    public void deliverRefreshingSucceededContent(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryMetadata metadata,
            PeriodicReportResult result) {
        deliver(key, metadata, result, true);
    }

    private void deliver(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryMetadata metadata,
            PeriodicReportResult result,
            boolean refreshChangedSucceededContent) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(result, "result");
        validateReportIdentity(key, metadata, result);

        Optional<PeriodicReportDeliverySnapshot> existing = store.find(key);
        Instant now = clock.instant();
        if (existing.isPresent() && catchUpEnded(existing.get(), now)) {
            expireUnfinished(key, existing.get(), now);
            return;
        }
        if (existing.filter(snapshot -> snapshot.state().isTerminal() && snapshot.state() != PeriodicReportDeliveryState.SUCCEEDED)
                .isPresent()) {
            return;
        }
        if (existing.filter(snapshot -> snapshot.state() == PeriodicReportDeliveryState.SUCCEEDED)
                .filter(snapshot -> !withinCatchUp(snapshot, now)).isPresent()) {
            return;
        }

        RenderedPeriodicReport rendered = result instanceof PeriodicReport report ? renderer.render(report) : null;
        PeriodicReportDeliveryRegistration registration = registration(key, metadata, rendered);
        boolean reconcileSucceededSnapshot = existing
                .map(snapshot -> snapshot.state() == PeriodicReportDeliveryState.SUCCEEDED)
                .orElse(false);
        boolean resumeChangedUnfinishedContent = existing
                .filter(snapshot -> !snapshot.state().isTerminal())
                .filter(snapshot -> rendered != null)
                .filter(snapshot -> snapshot.registration().metadata().equals(metadata))
                .filter(snapshot -> contentFingerprintChanged(snapshot, rendered))
                .isPresent();
        PeriodicReportDeliverySnapshot snapshot;
        if (reconcileSucceededSnapshot || resumeChangedUnfinishedContent) {
            if (rendered == null) {
                return;
            }
            snapshot = existing.orElseThrow();
        } else {
            snapshot = store.register(registration);
            if (!snapshot.registration().equals(registration) || snapshot.state().isTerminal()) {
                return;
            }
        }

        Instant claimedAt = clock.instant();
        Instant catchUpEndsAt = snapshot.registration().metadata().catchUpEndsAt();
        if (!claimedAt.isBefore(catchUpEndsAt)) {
            expireUnfinished(key, snapshot, claimedAt);
            return;
        }
        Instant leaseUntil = claimedAt.plus(LEASE);
        if (leaseUntil.isAfter(catchUpEndsAt)) {
            leaseUntil = catchUpEndsAt;
        }
        Optional<PeriodicReportDeliveryClaim> claim = store.claim(
                key, new PeriodicReportDeliveryClaimRequest(claimedAt, leaseUntil));
        if (claim.isEmpty()) {
            return;
        }

        Optional<ActiveClaim> ownedClaim = activeClaim(key, claim.get());
        if (ownedClaim.isEmpty()) {
            return;
        }

        try {
            if (rendered == null) {
                activeClaim(key, claim.get()).ifPresent(current ->
                        store.markNoOp(key, claim.get().token(), current.checkedAt()));
                return;
            }
            boolean changedUnfinishedContent = !reconcileSucceededSnapshot
                    && contentFingerprintChanged(ownedClaim.get().snapshot(), rendered);
            boolean delivered = reconcileSucceededSnapshot
                    ? reconcileSucceededSnapshot(
                            key,
                            claim.get(),
                            ownedClaim.get().snapshot(),
                            rendered,
                            refreshChangedSucceededContent)
                    : changedUnfinishedContent
                            ? replaceEntirePageGroup(
                                    key,
                                    claim.get(),
                                    ownedClaim.get().snapshot().pageProgress(),
                                    rendered)
                            : reconcilePages(key, claim.get(), rendered, ownedClaim.get().snapshot().pageProgress());
            if (delivered) {
                activeClaim(key, claim.get()).ifPresent(current ->
                        store.markSucceeded(key, claim.get().token(), current.checkedAt()));
            }
        } catch (PeriodicReportMessageGateway.PermanentMessageException exception) {
            markPermanentFailure(
                    key,
                    claim.get(),
                    "periodic report Discord delivery permanently failed");
        } catch (PeriodicReportMessageGateway.UnknownMessageException
                | PeriodicReportMessageGateway.MissingMessageException exception) {
            markRetryableFailure(
                    key,
                    claim.get(),
                    snapshot.attemptCount(),
                    catchUpEndsAt,
                    PeriodicReportDeliveryFailureCategory.UNKNOWN,
                    "periodic report Discord delivery outcome is unknown");
        } catch (PeriodicReportMessageGateway.RetryableMessageException exception) {
            markRetryableFailure(
                    key,
                    claim.get(),
                    snapshot.attemptCount(),
                    catchUpEndsAt,
                    PeriodicReportDeliveryFailureCategory.RETRYABLE,
                    "periodic report Discord delivery is retryable");
        } catch (RuntimeException exception) {
            markRetryableFailure(
                    key,
                    claim.get(),
                    snapshot.attemptCount(),
                    catchUpEndsAt,
                    PeriodicReportDeliveryFailureCategory.UNKNOWN,
                    "unexpected periodic report delivery failure");
        }
    }

    private static boolean contentFingerprintChanged(
            PeriodicReportDeliverySnapshot snapshot,
            RenderedPeriodicReport rendered) {
        return snapshot.registration().content()
                .map(content -> !content.fingerprint().equals(rendered.contentFingerprint()))
                .orElse(true);
    }

    private boolean reconcileSucceededSnapshot(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            PeriodicReportDeliverySnapshot snapshot,
            RenderedPeriodicReport rendered,
            boolean refreshChangedSucceededContent) {
        boolean fingerprintChanged = contentFingerprintChanged(snapshot, rendered);
        if (refreshChangedSucceededContent && fingerprintChanged) {
            return replaceEntirePageGroup(key, claim, snapshot.pageProgress(), rendered);
        }

        boolean missing = false;
        for (PeriodicReportDeliveryPageProgress progress : snapshot.pageProgress()) {
            if (!ownsActiveClaim(key, claim)) {
                return false;
            }
            try {
                PeriodicReportMessageGateway.PublishedReportPage published = messages.load(key.channelId(), progress.messageId());
                if (!ownsActiveClaim(key, claim)) {
                    return false;
                }
                if (!fingerprintChanged && !published.page().equals(expectedPage(rendered, progress.pageIndex()))) {
                    if (!ownsActiveClaim(key, claim)) {
                        return false;
                    }
                    messages.edit(key.channelId(), progress.messageId(), expectedPage(rendered, progress.pageIndex()));
                    if (!ownsActiveClaim(key, claim)) {
                        return false;
                    }
                }
            } catch (PeriodicReportMessageGateway.MissingMessageException ignored) {
                if (!ownsActiveClaim(key, claim)) {
                    return false;
                }
                missing = true;
            }
        }
        if (!missing) {
            return fingerprintChanged || reconcileDuplicates(key, claim, rendered, snapshot.pageProgress());
        }
        if (fingerprintChanged) {
            return replaceEntirePageGroup(key, claim, snapshot.pageProgress(), rendered);
        }
        return reconcilePages(key, claim, rendered, snapshot.pageProgress());
    }

    private boolean reconcilePages(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            RenderedPeriodicReport rendered,
            List<PeriodicReportDeliveryPageProgress> confirmedPages) {
        for (PeriodicReportDeliveryPageProgress progress : confirmedPages) {
            PeriodicReportMessageGateway.ReportPage expected = expectedPage(rendered, progress.pageIndex());
            if (!ownsActiveClaim(key, claim)) {
                return false;
            }
            try {
                PeriodicReportMessageGateway.PublishedReportPage published = messages.load(key.channelId(), progress.messageId());
                if (!ownsActiveClaim(key, claim)) {
                    return false;
                }
                if (!published.page().equals(expected)) {
                    if (!ownsActiveClaim(key, claim)) {
                        return false;
                    }
                    messages.edit(key.channelId(), progress.messageId(), expected);
                    if (!ownsActiveClaim(key, claim)) {
                        return false;
                    }
                }
                if (!reconcileDuplicates(key, claim, expected, progress.messageId())) {
                    return false;
                }
            } catch (PeriodicReportMessageGateway.MissingMessageException ignored) {
                if (!ownsActiveClaim(key, claim) || !replaceMissingPage(key, claim, expected)) {
                    return false;
                }
            }
        }
        for (int index = confirmedPages.size(); index < rendered.pages().size(); index++) {
            if (!findOrCreateAndRecord(key, claim, expectedPage(rendered, index))) {
                return false;
            }
        }
        return true;
    }

    private boolean replaceMissingPage(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            PeriodicReportMessageGateway.ReportPage expected) {
        Optional<List<PeriodicReportMessageGateway.PublishedReportPage>> exactMatches =
                findExactMatches(key, claim, expected);
        if (exactMatches.isEmpty()) {
            return false;
        }
        long winner;
        if (exactMatches.get().isEmpty()) {
            if (!ownsActiveClaim(key, claim)) {
                return false;
            }
            winner = messages.create(key.channelId(), expected);
            if (!ownsActiveClaim(key, claim)) {
                return false;
            }
        } else {
            winner = exactMatches.get().getFirst().messageId();
        }
        if (!ownsActiveClaim(key, claim)
                || !store.replacePage(
                        key,
                        claim.token(),
                        new PeriodicReportDeliveryPageProgress(expected.pageIndex(), winner))) {
            return false;
        }
        return reconcileDuplicates(key, claim, expected, winner);
    }

    private boolean findOrCreateAndRecord(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            PeriodicReportMessageGateway.ReportPage expected) {
        Optional<List<PeriodicReportMessageGateway.PublishedReportPage>> exactMatches =
                findExactMatches(key, claim, expected);
        if (exactMatches.isEmpty()) {
            return false;
        }
        long winner;
        if (exactMatches.get().isEmpty()) {
            if (!ownsActiveClaim(key, claim)) {
                return false;
            }
            winner = messages.create(key.channelId(), expected);
            if (!ownsActiveClaim(key, claim)) {
                return false;
            }
        } else {
            winner = exactMatches.get().getFirst().messageId();
        }
        if (!ownsActiveClaim(key, claim)
                || !store.recordPage(
                        key,
                        claim.token(),
                        new PeriodicReportDeliveryPageProgress(expected.pageIndex(), winner))) {
            return false;
        }
        return reconcileDuplicates(key, claim, expected, winner);
    }

    private boolean replaceEntirePageGroup(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            List<PeriodicReportDeliveryPageProgress> existingPages,
            RenderedPeriodicReport rendered) {
        for (PeriodicReportDeliveryPageProgress progress : existingPages) {
            if (!ownsActiveClaim(key, claim)) {
                return false;
            }
            try {
                messages.delete(key.channelId(), progress.messageId());
            } catch (PeriodicReportMessageGateway.MissingMessageException ignored) {
                // A missing old page already satisfies controlled replacement.
            }
            if (!ownsActiveClaim(key, claim)) {
                return false;
            }
        }
        PeriodicReportDeliveryContent content = new PeriodicReportDeliveryContent(
                rendered.contentFingerprint(), rendered.pages().size());
        if (!ownsActiveClaim(key, claim) || !store.replaceContent(key, claim.token(), content)) {
            return false;
        }
        for (int index = 0; index < rendered.pages().size(); index++) {
            if (!findOrCreateAndRecord(key, claim, expectedPage(rendered, index))) {
                return false;
            }
        }
        return true;
    }

    private boolean reconcileDuplicates(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            RenderedPeriodicReport rendered,
            List<PeriodicReportDeliveryPageProgress> confirmedPages) {
        for (PeriodicReportDeliveryPageProgress progress : confirmedPages) {
            if (!reconcileDuplicates(key, claim, expectedPage(rendered, progress.pageIndex()), progress.messageId())) {
                return false;
            }
        }
        return true;
    }

    private boolean reconcileDuplicates(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            PeriodicReportMessageGateway.ReportPage expected,
            long persistedMessageId) {
        Optional<List<PeriodicReportMessageGateway.PublishedReportPage>> exactMatches =
                findExactMatches(key, claim, expected);
        if (exactMatches.isEmpty()) {
            return false;
        }
        List<PeriodicReportMessageGateway.PublishedReportPage> matches = exactMatches.get();
        boolean persistedMatch = matches.stream().anyMatch(match -> match.messageId() == persistedMessageId);
        long winner = persistedMatch ? persistedMessageId : matches.stream()
                .mapToLong(PeriodicReportMessageGateway.PublishedReportPage::messageId)
                .min()
                .orElse(persistedMessageId);
        for (PeriodicReportMessageGateway.PublishedReportPage match : matches) {
            if (match.messageId() != winner) {
                if (!ownsActiveClaim(key, claim)) {
                    return false;
                }
                try {
                    messages.delete(key.channelId(), match.messageId());
                } catch (PeriodicReportMessageGateway.MissingMessageException ignored) {
                    // The exact duplicate is already gone.
                }
                if (!ownsActiveClaim(key, claim)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Optional<List<PeriodicReportMessageGateway.PublishedReportPage>> findExactMatches(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            PeriodicReportMessageGateway.ReportPage expected) {
        if (!ownsActiveClaim(key, claim)) {
            return Optional.empty();
        }
        List<PeriodicReportMessageGateway.PublishedReportPage> matches = messages.findExactMatches(key.channelId(), expected)
                .stream()
                .sorted(Comparator.comparingLong(PeriodicReportMessageGateway.PublishedReportPage::messageId))
                .toList();
        return ownsActiveClaim(key, claim) ? Optional.of(matches) : Optional.empty();
    }

    private Optional<PeriodicReportDeliverySnapshot> ownedSnapshot(
            PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaim claim) {
        return store.find(key)
                .filter(current -> current.state() == PeriodicReportDeliveryState.CLAIMED)
                .filter(current -> current.claim().map(PeriodicReportDeliveryClaim::token)
                        .filter(claim.token()::equals)
                        .isPresent());
    }

    private Optional<ActiveClaim> activeClaim(
            PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaim claim) {
        Optional<PeriodicReportDeliverySnapshot> current = ownedSnapshot(key, claim);
        Instant now = clock.instant();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        PeriodicReportDeliverySnapshot snapshot = current.get();
        PeriodicReportDeliveryClaim persistedClaim = snapshot.claim().orElseThrow();
        if (!withinCatchUp(snapshot, now) || !now.isBefore(persistedClaim.leaseUntil())) {
            expireUnfinished(key, snapshot, now);
            return Optional.empty();
        }
        return Optional.of(new ActiveClaim(snapshot, now));
    }

    private boolean ownsActiveClaim(PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaim claim) {
        return activeClaim(key, claim).isPresent();
    }

    private void expireUnfinished(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliverySnapshot snapshot,
            Instant now) {
        if (!snapshot.state().isTerminal() && catchUpEnded(snapshot, now)) {
            store.markExpired(key, now);
        }
    }

    private static boolean catchUpEnded(PeriodicReportDeliverySnapshot snapshot, Instant now) {
        return !now.isBefore(snapshot.registration().metadata().catchUpEndsAt());
    }

    private static boolean withinCatchUp(PeriodicReportDeliverySnapshot snapshot, Instant now) {
        Instant dueAt = snapshot.registration().metadata().dueAt().instant();
        Instant catchUpEnd = snapshot.registration().metadata().catchUpEndsAt();
        return !now.isBefore(dueAt) && now.isBefore(catchUpEnd);
    }

    private void markRetryableFailure(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            int priorAttemptCount,
            Instant catchUpEndsAt,
            PeriodicReportDeliveryFailureCategory category,
            String safeMessage) {
        Optional<ActiveClaim> current = activeClaim(key, claim);
        if (current.isEmpty()) {
            return;
        }
        Instant failureAt = current.get().checkedAt();
        Instant nextRetryAt = retryAt(failureAt, priorAttemptCount);
        if (nextRetryAt.isAfter(catchUpEndsAt)) {
            nextRetryAt = catchUpEndsAt;
        }
        store.markRetryableFailure(
                key,
                claim.token(),
                new PeriodicReportDeliveryFailure(category, safeMessage),
                nextRetryAt);
    }

    private void markPermanentFailure(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            String safeMessage) {
        Optional<ActiveClaim> current = activeClaim(key, claim);
        if (current.isEmpty()) {
            return;
        }
        store.markPermanentFailure(
                key,
                claim.token(),
                new PeriodicReportDeliveryFailure(PeriodicReportDeliveryFailureCategory.PERMANENT, safeMessage),
                current.get().checkedAt());
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

    private static PeriodicReportMessageGateway.ReportPage expectedPage(RenderedPeriodicReport rendered, int pageIndex) {
        return new PeriodicReportMessageGateway.ReportPage(pageIndex, rendered.pages().get(pageIndex));
    }

    private static PeriodicReportDeliveryRegistration registration(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryMetadata metadata,
            RenderedPeriodicReport rendered) {
        return new PeriodicReportDeliveryRegistration(
                key,
                metadata,
                Optional.ofNullable(rendered)
                        .map(value -> new PeriodicReportDeliveryContent(
                                value.contentFingerprint(), value.pages().size())));
    }

    private record ActiveClaim(PeriodicReportDeliverySnapshot snapshot, Instant checkedAt) { }

    private static void validateReportIdentity(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryMetadata metadata,
            PeriodicReportResult result) {
        if (!key.periodStart().equals(metadata.period().startDate())) {
            throw new IllegalArgumentException("delivery key period must match metadata period");
        }
        if (result instanceof PeriodicReport report
                && (report.reportType() != key.reportType()
                        || !report.period().equals(metadata.period()))) {
            throw new IllegalArgumentException("generated report must match delivery key and metadata");
        }
        if (result instanceof PeriodicReportNoOp noOp
                && (noOp.reportType() != key.reportType()
                        || !noOp.period().equals(metadata.period()))) {
            throw new IllegalArgumentException("NO_OP report must match delivery key and metadata");
        }
    }
}
