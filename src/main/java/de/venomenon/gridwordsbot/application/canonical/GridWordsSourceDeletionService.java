package de.venomenon.gridwordsbot.application.canonical;

import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/** Completes the persisted second phase of a GridWords replacement outside database transactions. */
public final class GridWordsSourceDeletionService {

    private static final long LEASE_SECONDS = 60;

    private final SubmissionStore submissions;
    private final SourceMessageDeletionGateway deletionGateway;
    private final Clock clock;
    private final PublicationRetryScheduler retryScheduler;
    private final Set<Long> scheduledRetries = ConcurrentHashMap.newKeySet();

    public GridWordsSourceDeletionService(
            SubmissionStore submissions,
            SourceMessageDeletionGateway deletionGateway,
            Clock clock,
            PublicationRetryScheduler retryScheduler) {
        this.submissions = Objects.requireNonNull(submissions);
        this.deletionGateway = Objects.requireNonNull(deletionGateway);
        this.clock = Objects.requireNonNull(clock);
        this.retryScheduler = Objects.requireNonNull(retryScheduler);
    }

    /** Returns true only once the source deletion has been durably completed. */
    public boolean deleteAfterCanonicalPublication(long sourceMessageId) {
        SubmissionStore.StoredSubmission submission = submissions.findBySourceMessageId(sourceMessageId).orElse(null);
        if (submission == null || submission.state() == SubmissionStore.SubmissionState.COMPLETED) {
            return submission != null;
        }
        if (submission.state() == SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED) {
            return submissions.completeOriginalSourceDeletion(sourceMessageId);
        }
        if (submission.state() != SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                && submission.state() != SubmissionStore.SubmissionState.SUPERSEDED) {
            return false;
        }

        SubmissionStore.SourceDeletionClaim claim = submissions.claimOriginalSourceDeletion(
                sourceMessageId, clock.instant().plusSeconds(LEASE_SECONDS)).orElse(null);
        if (claim == null) {
            return false;
        }

        SourceMessageDeletionGateway.DeletionResult outcome;
        try {
            outcome = deletionGateway.deleteSourceMessage(submission.channelId(), submission.sourceMessageId());
        } catch (RuntimeException exception) {
            outcome = SourceMessageDeletionGateway.DeletionResult.RETRYABLE_FAILURE;
        }

        return switch (outcome) {
            case DELETED, ALREADY_MISSING -> confirmDeleted(sourceMessageId, claim.token());
            case RETRYABLE_FAILURE -> {
                boolean recorded = submissions.recordOriginalSourceDeletionFailure(
                        sourceMessageId,
                        claim.token(),
                        SubmissionStore.OriginalDeletionFailure.RETRYABLE,
                        "source message deletion failed transiently");
                if (recorded) {
                    scheduleRetry(sourceMessageId);
                }
                yield false;
            }
            case PERMANENT_FAILURE -> {
                submissions.recordOriginalSourceDeletionFailure(
                        sourceMessageId,
                        claim.token(),
                        SubmissionStore.OriginalDeletionFailure.PERMANENT,
                        "source message deletion was denied permanently");
                yield false;
            }
        };
    }

    /** Startup recovery reads durable work; it never relies on a scheduler wake-up as its source of truth. */
    public void resumeOpenDeletions() {
        for (SubmissionStore.StoredSubmission submission : submissions.findGridWordsAwaitingOriginalSourceDeletion()) {
            deleteAfterCanonicalPublication(submission.sourceMessageId());
        }
    }

    private boolean confirmDeleted(long sourceMessageId, java.util.UUID claimToken) {
        if (!submissions.recordOriginalSourceDeleted(sourceMessageId, claimToken)) {
            return false;
        }
        return submissions.completeOriginalSourceDeletion(sourceMessageId);
    }

    private void scheduleRetry(long sourceMessageId) {
        if (!scheduledRetries.add(sourceMessageId)) {
            return;
        }
        try {
            retryScheduler.schedule(clock.instant().plusSeconds(LEASE_SECONDS + 1), () -> {
                scheduledRetries.remove(sourceMessageId);
                deleteAfterCanonicalPublication(sourceMessageId);
            });
        } catch (RejectedExecutionException ignored) {
            // A later startup can recover the durable delete state.
            scheduledRetries.remove(sourceMessageId);
        }
    }
}
