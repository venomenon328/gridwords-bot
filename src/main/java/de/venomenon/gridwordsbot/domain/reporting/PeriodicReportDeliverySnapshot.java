package de.venomenon.gridwordsbot.domain.reporting;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Complete immutable delivery and recovery state; calculated report statistics are deliberately absent. */
public record PeriodicReportDeliverySnapshot(
        PeriodicReportDeliveryRegistration registration,
        PeriodicReportDeliveryState state,
        Optional<PeriodicReportDeliveryClaim> claim,
        int attemptCount,
        Optional<Instant> nextRetryAt,
        Optional<PeriodicReportDeliveryFailure> failure,
        List<PeriodicReportDeliveryPageProgress> pageProgress,
        Optional<Instant> completedAt,
        Instant createdAt,
        Instant updatedAt) {
    public PeriodicReportDeliverySnapshot {
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(state, "state");
        claim = Objects.requireNonNull(claim, "claim");
        nextRetryAt = Objects.requireNonNull(nextRetryAt, "nextRetryAt");
        failure = Objects.requireNonNull(failure, "failure");
        pageProgress = List.copyOf(Objects.requireNonNull(pageProgress, "pageProgress"));
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (attemptCount < 0 || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("attemptCount and timestamps must be consistent");
        }
        validatePageProgress(registration.content(), pageProgress);
        validateState(state, claim, nextRetryAt, failure, pageProgress, completedAt);
    }

    private static void validatePageProgress(
            Optional<PeriodicReportDeliveryContent> content,
            List<PeriodicReportDeliveryPageProgress> pageProgress) {
        if (content.isEmpty() && !pageProgress.isEmpty()) {
            throw new IllegalArgumentException("page progress requires generated report content");
        }
        if (content.isPresent() && pageProgress.size() > content.get().expectedPageCount()) {
            throw new IllegalArgumentException("page progress exceeds expected page count");
        }
        for (int index = 0; index < pageProgress.size(); index++) {
            if (pageProgress.get(index).pageIndex() != index) {
                throw new IllegalArgumentException("page progress must be contiguous and ordered by page index");
            }
        }
    }

    private static void validateState(
            PeriodicReportDeliveryState state,
            Optional<PeriodicReportDeliveryClaim> claim,
            Optional<Instant> nextRetryAt,
            Optional<PeriodicReportDeliveryFailure> failure,
            List<PeriodicReportDeliveryPageProgress> pageProgress,
            Optional<Instant> completedAt) {
        if (state == PeriodicReportDeliveryState.CLAIMED != claim.isPresent()) {
            throw new IllegalArgumentException("only a claimed delivery may retain a claim");
        }
        if (state == PeriodicReportDeliveryState.RETRYABLE != nextRetryAt.isPresent()) {
            throw new IllegalArgumentException("only a retryable delivery may have nextRetryAt");
        }
        if (state == PeriodicReportDeliveryState.RETRYABLE && failure.isEmpty()) {
            throw new IllegalArgumentException("retryable delivery needs a failure");
        }
        if (state == PeriodicReportDeliveryState.FAILED_PERMANENT
                && (failure.isEmpty() || failure.get().category() != PeriodicReportDeliveryFailureCategory.PERMANENT)) {
            throw new IllegalArgumentException("permanently failed delivery needs a permanent failure");
        }
        if (state.isTerminal() != completedAt.isPresent()) {
            throw new IllegalArgumentException("only terminal delivery states may have completedAt");
        }
        if (state == PeriodicReportDeliveryState.NO_OP && !pageProgress.isEmpty()) {
            throw new IllegalArgumentException("NO_OP delivery must not have page progress");
        }
        if (state == PeriodicReportDeliveryState.SUCCEEDED && pageProgress.isEmpty()) {
            throw new IllegalArgumentException("successful delivery needs page progress");
        }
    }
}
