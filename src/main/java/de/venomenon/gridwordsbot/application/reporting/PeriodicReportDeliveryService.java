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
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(result, "result");
        validateReportIdentity(key, metadata, result);

        Instant now = clock.instant();
        Optional<PeriodicReportDeliverySnapshot> existing = store.find(key);
        if (existing.isPresent() && !now.isBefore(existing.get().registration().metadata().catchUpEndsAt())) {
            if (!existing.get().state().isTerminal()) {
                store.markExpired(key, now);
            }
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
        PeriodicReportDeliverySnapshot snapshot;
        if (reconcileSucceededSnapshot) {
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
        Optional<PeriodicReportDeliveryClaim> claim = store.claim(
                key, new PeriodicReportDeliveryClaimRequest(claimedAt, claimedAt.plus(LEASE)));
        if (claim.isEmpty()) {
            return;
        }

        Optional<PeriodicReportDeliverySnapshot> ownedSnapshot = ownedSnapshot(key, claim.get());
        if (ownedSnapshot.isEmpty()) {
            return;
        }

        try {
            if (rendered == null) {
                store.markNoOp(key, claim.get().token(), clock.instant());
                return;
            }
            boolean delivered = reconcileSucceededSnapshot
                    ? reconcileSucceededSnapshot(key, claim.get(), ownedSnapshot.get(), rendered)
                    : reconcilePages(key, claim.get(), rendered, ownedSnapshot.get().pageProgress());
            if (delivered) {
                store.markSucceeded(key, claim.get().token(), clock.instant());
            }
        } catch (PeriodicReportMessageGateway.PermanentMessageException exception) {
            markPermanentFailure(key, claim.get(), "periodic report Discord delivery permanently failed");
        } catch (PeriodicReportMessageGateway.UnknownMessageException
                | PeriodicReportMessageGateway.MissingMessageException exception) {
            markRetryableFailure(
                    key,
                    claim.get(),
                    snapshot.attemptCount(),
                    PeriodicReportDeliveryFailureCategory.UNKNOWN,
                    "periodic report Discord delivery outcome is unknown");
        } catch (PeriodicReportMessageGateway.RetryableMessageException exception) {
            markRetryableFailure(
                    key,
                    claim.get(),
                    snapshot.attemptCount(),
                    PeriodicReportDeliveryFailureCategory.RETRYABLE,
                    "periodic report Discord delivery is retryable");
        } catch (RuntimeException exception) {
            markRetryableFailure(
                    key,
                    claim.get(),
                    snapshot.attemptCount(),
                    PeriodicReportDeliveryFailureCategory.UNKNOWN,
                    "unexpected periodic report delivery failure");
        }
    }

    private boolean reconcileSucceededSnapshot(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            PeriodicReportDeliverySnapshot snapshot,
            RenderedPeriodicReport rendered) {
        boolean fingerprintChanged = snapshot.registration().content()
                .map(content -> !content.fingerprint().equals(rendered.contentFingerprint()))
                .orElse(true);
        boolean missing = false;
        for (PeriodicReportDeliveryPageProgress progress : snapshot.pageProgress()) {
            try {
                PeriodicReportMessageGateway.PublishedReportPage published = messages.load(key.channelId(), progress.messageId());
                if (!ownsClaim(key, claim)) {
                    return false;
                }
                if (!fingerprintChanged && !published.page().equals(expectedPage(rendered, progress.pageIndex()))) {
                    messages.edit(key.channelId(), progress.messageId(), expectedPage(rendered, progress.pageIndex()));
                    if (!ownsClaim(key, claim)) {
                        return false;
                    }
                }
            } catch (PeriodicReportMessageGateway.MissingMessageException ignored) {
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
            try {
                PeriodicReportMessageGateway.PublishedReportPage published = messages.load(key.channelId(), progress.messageId());
                if (!ownsClaim(key, claim)) {
                    return false;
                }
                if (!published.page().equals(expected)) {
                    messages.edit(key.channelId(), progress.messageId(), expected);
                    if (!ownsClaim(key, claim)) {
                        return false;
                    }
                }
                if (!reconcileDuplicates(key, claim, expected, progress.messageId())) {
                    return false;
                }
            } catch (PeriodicReportMessageGateway.MissingMessageException ignored) {
                if (!replaceMissingPage(key, claim, expected)) {
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
        long winner = findExactMatches(key, expected).stream()
                .mapToLong(PeriodicReportMessageGateway.PublishedReportPage::messageId)
                .min()
                .orElseGet(() -> messages.create(key.channelId(), expected));
        if (!store.replacePage(key, claim.token(), new PeriodicReportDeliveryPageProgress(expected.pageIndex(), winner))) {
            return false;
        }
        return reconcileDuplicates(key, claim, expected, winner);
    }

    private boolean findOrCreateAndRecord(
            PeriodicReportDeliveryKey key,
            PeriodicReportDeliveryClaim claim,
            PeriodicReportMessageGateway.ReportPage expected) {
        long winner = findExactMatches(key, expected).stream()
                .mapToLong(PeriodicReportMessageGateway.PublishedReportPage::messageId)
                .min()
                .orElseGet(() -> messages.create(key.channelId(), expected));
        if (!store.recordPage(key, claim.token(), new PeriodicReportDeliveryPageProgress(expected.pageIndex(), winner))) {
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
            try {
                messages.delete(key.channelId(), progress.messageId());
            } catch (PeriodicReportMessageGateway.MissingMessageException ignored) {
                // A missing old page already satisfies controlled replacement.
            }
            if (!ownsClaim(key, claim)) {
                return false;
            }
        }
        PeriodicReportDeliveryContent content = new PeriodicReportDeliveryContent(
                rendered.contentFingerprint(), rendered.pages().size());
        if (!store.replaceContent(key, claim.token(), content)) {
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
        List<PeriodicReportMessageGateway.PublishedReportPage> matches = findExactMatches(key, expected);
        boolean persistedMatch = matches.stream().anyMatch(match -> match.messageId() == persistedMessageId);
        long winner = persistedMatch ? persistedMessageId : matches.stream()
                .mapToLong(PeriodicReportMessageGateway.PublishedReportPage::messageId)
                .min()
                .orElse(persistedMessageId);
        for (PeriodicReportMessageGateway.PublishedReportPage match : matches) {
            if (match.messageId() != winner) {
                try {
                    messages.delete(key.channelId(), match.messageId());
                } catch (PeriodicReportMessageGateway.MissingMessageException ignored) {
                    // The exact duplicate is already gone.
                }
                if (!ownsClaim(key, claim)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<PeriodicReportMessageGateway.PublishedReportPage> findExactMatches(
            PeriodicReportDeliveryKey key, PeriodicReportMessageGateway.ReportPage expected) {
        return messages.findExactMatches(key.channelId(), expected).stream()
                .sorted(Comparator.comparingLong(PeriodicReportMessageGateway.PublishedReportPage::messageId))
                .toList();
    }

    private Optional<PeriodicReportDeliverySnapshot> ownedSnapshot(
            PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaim claim) {
        return store.find(key)
                .filter(current -> current.state() == PeriodicReportDeliveryState.CLAIMED)
                .filter(current -> current.claim().map(PeriodicReportDeliveryClaim::token)
                        .filter(claim.token()::equals)
                        .isPresent());
    }

    private boolean ownsClaim(PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaim claim) {
        return ownedSnapshot(key, claim).isPresent();
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
            PeriodicReportDeliveryFailureCategory category,
            String safeMessage) {
        store.markRetryableFailure(
                key,
                claim.token(),
                new PeriodicReportDeliveryFailure(category, safeMessage),
                retryAt(clock.instant(), priorAttemptCount));
    }

    private void markPermanentFailure(
            PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaim claim, String safeMessage) {
        store.markPermanentFailure(
                key,
                claim.token(),
                new PeriodicReportDeliveryFailure(PeriodicReportDeliveryFailureCategory.PERMANENT, safeMessage),
                clock.instant());
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
