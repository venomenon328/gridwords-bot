package de.venomenon.gridwordsbot.application.canonical;

import de.venomenon.gridwordsbot.domain.model.GameDateAdmissionPolicy;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceDeletionRecoveryStore;
import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/** Completes the persisted second phase of a canonical replacement outside database transactions. */
public final class GridWordsSourceDeletionService {

    private static final long LEASE_SECONDS = 60;

    private final SubmissionStore submissions;
    private final SourceMessageDeletionGateway deletionGateway;
    private final Clock clock;
    private final PublicationRetryScheduler retryScheduler;
    private final SourceDeletionRecoveryStore recoveryStore;
    private final GameResultStore results;
    private final GameDateAdmissionPolicy admission;
    private final Set<Long> scheduledRetries = ConcurrentHashMap.newKeySet();

    public GridWordsSourceDeletionService(
            SubmissionStore submissions,
            SourceMessageDeletionGateway deletionGateway,
            Clock clock,
            PublicationRetryScheduler retryScheduler) {
        this(submissions, deletionGateway, clock, retryScheduler, ignored -> 0);
    }

    public GridWordsSourceDeletionService(
            SubmissionStore submissions,
            SourceMessageDeletionGateway deletionGateway,
            Clock clock,
            PublicationRetryScheduler retryScheduler,
            SourceDeletionRecoveryStore recoveryStore) {
        this(submissions, deletionGateway, clock, retryScheduler, recoveryStore, null, null);
    }

    /**
     * Uses the same business-date admission policy as the inbound and canonical
     * publication paths so that a source-delete recovery cannot complete an
     * ordinary previous-day operation after its logical close.
     */
    public GridWordsSourceDeletionService(
            SubmissionStore submissions,
            SourceMessageDeletionGateway deletionGateway,
            Clock clock,
            PublicationRetryScheduler retryScheduler,
            SourceDeletionRecoveryStore recoveryStore,
            GameResultStore results,
            ZoneId zoneId,
            LocalTime dayCloseTime) {
        this(submissions, deletionGateway, clock, retryScheduler, recoveryStore,
                Objects.requireNonNull(results), new GameDateAdmissionPolicy(clock, zoneId, dayCloseTime));
    }

    private GridWordsSourceDeletionService(
            SubmissionStore submissions,
            SourceMessageDeletionGateway deletionGateway,
            Clock clock,
            PublicationRetryScheduler retryScheduler,
            SourceDeletionRecoveryStore recoveryStore,
            GameResultStore results,
            GameDateAdmissionPolicy admission) {
        this.submissions = Objects.requireNonNull(submissions);
        this.deletionGateway = Objects.requireNonNull(deletionGateway);
        this.clock = Objects.requireNonNull(clock);
        this.retryScheduler = Objects.requireNonNull(retryScheduler);
        this.recoveryStore = Objects.requireNonNull(recoveryStore);
        this.results = results;
        this.admission = admission;
    }

    /** Returns true only once the source deletion has been durably completed. */
    public boolean deleteAfterCanonicalPublication(long sourceMessageId) {
        SubmissionStore.StoredSubmission submission = submissions.findBySourceMessageId(sourceMessageId).orElse(null);
        if (submission == null || submission.state() == SubmissionStore.SubmissionState.COMPLETED) {
            return submission != null;
        }
        if (!allowedToComplete(submission)) {
            return false;
        }
        if (submission.state() == SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED) {
            return submissions.completeOriginalSourceDeletion(sourceMessageId);
        }
        if (submission.state() != SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                && submission.state() != SubmissionStore.SubmissionState.SUPERSEDED) {
            return false;
        }

        java.time.Instant now = clock.instant();
        if (submission.sourceDeletionLeaseUntil().filter(leaseUntil -> leaseUntil.isAfter(now)).isPresent()) {
            scheduleRetryAt(sourceMessageId, submission.sourceDeletionLeaseUntil().orElseThrow().plusSeconds(1));
            return false;
        }

        SubmissionStore.SourceDeletionClaim claim = submissions.claimOriginalSourceDeletion(
                sourceMessageId, now.plusSeconds(LEASE_SECONDS)).orElse(null);
        if (claim == null) {
            scheduleRetryAfterBusyClaim(sourceMessageId, now);
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
                    scheduleRetryAt(sourceMessageId, clock.instant().plusSeconds(LEASE_SECONDS + 1));
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

    /**
     * Starts deletion for the newly confirmed source and any older sources of the same result that became safe.
     * Each source is considered at most once during this reconciliation pass.
     */
    public void reconcileAfterCanonicalPublication(long sourceMessageId) {
        SubmissionStore.StoredSubmission current = submissions.findBySourceMessageId(sourceMessageId).orElse(null);
        if (current == null || current.gameResultId().isEmpty()) {
            return;
        }
        if (!allowedToComplete(current)) {
            return;
        }
        long resultId = current.gameResultId().orElseThrow();

        recoveryStore.reactivatePermanentFailures(OptionalLong.of(resultId));
        deleteAfterCanonicalPublication(sourceMessageId);
        for (SubmissionStore.StoredSubmission candidate : findAllAwaitingOriginalSourceDeletion()) {
            if (candidate.sourceMessageId() != sourceMessageId
                    && candidate.gameResultId().filter(candidateResultId -> candidateResultId == resultId).isPresent()) {
                deleteAfterCanonicalPublication(candidate.sourceMessageId());
            }
        }
    }

    /** Startup recovery reads durable work; it never relies on a scheduler wake-up as its source of truth. */
    public void resumeOpenDeletions() {
        reactivateAdmittedPermanentFailures();
        for (SubmissionStore.StoredSubmission submission : findAllAwaitingOriginalSourceDeletion()) {
            deleteAfterCanonicalPublication(submission.sourceMessageId());
        }
    }

    private void reactivateAdmittedPermanentFailures() {
        for (long resultId : recoveryStore.findPermanentlyFailedResultIds()) {
            if (resultId <= 0) {
                throw new IllegalStateException("permanent source-deletion recovery returned an invalid result ID");
            }
            if (results == null || admission == null) {
                // Compatibility-only constructors lack the facts needed to
                // make this safety decision, so they must not reactivate.
                continue;
            }
            boolean admitted = results.findById(resultId)
                    .map(result -> admission.allows(result.parsedResult().gameDate()))
                    .orElse(false);
            if (admitted) {
                recoveryStore.reactivatePermanentFailures(OptionalLong.of(resultId));
            }
        }
    }

    private boolean allowedToComplete(SubmissionStore.StoredSubmission submission) {
        // The compatibility constructors are retained for isolated legacy
        // tests. Production wiring always supplies both dependencies.
        if (results == null || admission == null) {
            return true;
        }
        return submission.gameResultId()
                .flatMap(results::findById)
                .map(result -> admission.allows(result.parsedResult().gameDate()))
                .orElse(false);
    }

    private java.util.List<SubmissionStore.StoredSubmission> findAllAwaitingOriginalSourceDeletion() {
        java.util.List<SubmissionStore.StoredSubmission> all = new java.util.ArrayList<>(
                submissions.findGridWordsAwaitingOriginalSourceDeletion());
        try {
            all.addAll(submissions.findAwaitingOriginalSourceDeletion(GameType.QUADWORDS));
        } catch (UnsupportedOperationException ignored) {
        }
        return all;
    }

    private boolean confirmDeleted(long sourceMessageId, java.util.UUID claimToken) {
        if (!submissions.recordOriginalSourceDeleted(sourceMessageId, claimToken)) {
            return false;
        }
        return submissions.completeOriginalSourceDeletion(sourceMessageId);
    }

    private void scheduleRetryAfterBusyClaim(long sourceMessageId, java.time.Instant now) {
        submissions.findBySourceMessageId(sourceMessageId)
                .flatMap(SubmissionStore.StoredSubmission::sourceDeletionLeaseUntil)
                .filter(leaseUntil -> leaseUntil.isAfter(now))
                .ifPresent(leaseUntil -> scheduleRetryAt(sourceMessageId, leaseUntil.plusSeconds(1)));
    }

    private void scheduleRetryAt(long sourceMessageId, java.time.Instant retryAt) {
        if (!scheduledRetries.add(sourceMessageId)) {
            return;
        }
        try {
            retryScheduler.schedule(retryAt, () -> {
                scheduledRetries.remove(sourceMessageId);
                deleteAfterCanonicalPublication(sourceMessageId);
            });
        } catch (RejectedExecutionException ignored) {
            // A later startup can recover the durable delete state.
            scheduledRetries.remove(sourceMessageId);
        }
    }
}
