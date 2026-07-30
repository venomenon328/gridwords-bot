package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for the resumable, source-message based submission state machine. */
public interface SubmissionStore {

    StoredSubmission register(SubmissionRegistration registration);

    Optional<StoredSubmission> findBySourceMessageId(long sourceMessageId);

    /** Stores once; replay with equivalent data returns the stored submission, contradictory data raises a conflict. */
    StoredSubmission storeResult(ResultStorage request);

    StoredSubmission reject(RejectedSubmission request);

    boolean transition(long sourceMessageId, SubmissionState expectedState, SubmissionState targetState);

    default void markRetryableFailure(long sourceMessageId, String safeTechnicalMessage) {
        throw new UnsupportedOperationException("retry state is not available");
    }

    default boolean completeCanonicalPublication(
            long sourceMessageId,
            long gameResultId,
            long canonicalMessageId,
            UUID claimToken) {
        throw new UnsupportedOperationException("canonical publication is not available");
    }

    /**
     * Records a write-ahead delivery attempt before any Discord REST call.  A surviving attempt makes startup
     * reconciliation mandatory even when the process dies after Discord has accepted the request.
     */
    default CanonicalDeliveryAttempt beginCanonicalDelivery(long sourceMessageId, long gameResultId, UUID claimToken) {
        throw new UnsupportedOperationException("canonical delivery fencing is not available");
    }
    /** Atomically chooses the newest publishable submission for one mutable game result. */
    default CanonicalPublicationPreparation prepareCanonicalPublication(long sourceMessageId, long gameResultId) {
        throw new UnsupportedOperationException("canonical publication preparation is not available");
    }

    /** Returns the current source and persisted refresh generation for one mutable result. */
    default Optional<CanonicalRefreshCandidate> findCurrentCanonicalPublicationCandidate(long gameResultId) {
        throw new UnsupportedOperationException("current canonical publication lookup is not available");
    }

    /** Lists refresh work that survived process termination and must be reconciled after startup. */
    default List<CanonicalRefreshCandidate> findCanonicalRefreshCandidates() {
        throw new UnsupportedOperationException("canonical refresh recovery is not available");
    }

    /** Durably records that a late Discord side effect requires the current canonical message to be reconciled. */
    default void requestCanonicalRefresh(long gameResultId) {
        throw new UnsupportedOperationException("canonical refresh request is not available");
    }

    /** Persists a token-owned refresh of an already published current canonical message. */
    default CanonicalRefreshCompletion completeCanonicalRefresh(
            long sourceMessageId,
            long gameResultId,
            long canonicalMessageId,
            UUID claimToken,
            long refreshGeneration) {
        throw new UnsupportedOperationException("canonical refresh is not available");
    }

    default List<StoredSubmission> findGridWordsAwaitingCanonicalPublication() { throw new UnsupportedOperationException("publication recovery is not available"); }
    default List<StoredSubmission> findAwaitingCanonicalPublication(GameType gameType) {
        return gameType == GameType.GRIDWORDS ? findGridWordsAwaitingCanonicalPublication()
                : throwUnsupportedPublicationRecovery();
    }
    private List<StoredSubmission> throwUnsupportedPublicationRecovery() { throw new UnsupportedOperationException("publication recovery is not available"); }

    /** Acquires a short, token-owned claim for one source-message delete REST call. */
    default Optional<SourceDeletionClaim> claimOriginalSourceDeletion(long sourceMessageId, Instant leaseUntil) {
        throw new UnsupportedOperationException("source deletion is not available");
    }

    /** Persists the external delete outcome only for the worker that owns the token. */
    default boolean recordOriginalSourceDeleted(long sourceMessageId, UUID claimToken) {
        throw new UnsupportedOperationException("source deletion is not available");
    }

    /** Records a classified failure while retaining the eligible canonical state for recovery. */
    default boolean recordOriginalSourceDeletionFailure(
            long sourceMessageId, UUID claimToken, OriginalDeletionFailure failure, String safeTechnicalMessage) {
        throw new UnsupportedOperationException("source deletion is not available");
    }

    /** Completes a durably recorded delete without issuing another Discord request. */
    default boolean completeOriginalSourceDeletion(long sourceMessageId) {
        throw new UnsupportedOperationException("source deletion is not available");
    }

    /** Lists only persisted GridWords source-delete work that has not reached the terminal completed state. */
    default List<StoredSubmission> findGridWordsAwaitingOriginalSourceDeletion() { throw new UnsupportedOperationException("source deletion recovery is not available"); }
    default List<StoredSubmission> findAwaitingOriginalSourceDeletion(GameType gameType) {
        return gameType == GameType.GRIDWORDS ? findGridWordsAwaitingOriginalSourceDeletion()
                : throwUnsupportedDeletionRecovery();
    }
    private List<StoredSubmission> throwUnsupportedDeletionRecovery() { throw new UnsupportedOperationException("source deletion recovery is not available"); }

    record SubmissionRegistration(
            long sourceMessageId,
            long guildId,
            long channelId,
            long authorPlayerId,
            String rawMessageContent,
            List<AttachmentSnapshot> attachments,
            Instant receivedAt) {

        public SubmissionRegistration {
            if (sourceMessageId <= 0 || guildId <= 0 || channelId <= 0 || authorPlayerId <= 0) {
                throw new IllegalArgumentException("Discord IDs must be positive");
            }
            Objects.requireNonNull(rawMessageContent, "rawMessageContent");
            attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
            Objects.requireNonNull(receivedAt, "receivedAt");
        }
    }

    record AttachmentSnapshot(int index, String fileName, Optional<String> contentType, long sizeBytes) {

        public AttachmentSnapshot {
            if (index < 0 || sizeBytes < 0) {
                throw new IllegalArgumentException("attachment index and size must not be negative");
            }
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(contentType, "contentType");
        }
    }

    /** Couples a fully validated player registration to result persistence in one transaction. */
    record ResultStorage(long sourceMessageId, GameResultStore.GameResultUpsert result, PlayerStore.ParticipationChange playerRegistration) {
        public ResultStorage {
            if (sourceMessageId <= 0) throw new IllegalArgumentException("sourceMessageId must be positive");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(playerRegistration, "playerRegistration");
            if (result.playerId() != playerRegistration.profile().discordUserId()) {
                throw new IllegalArgumentException("result and player registration must refer to the same player");
            }
        }
    }
    record RejectedSubmission(long sourceMessageId, String errorCode) {

        public RejectedSubmission {
            if (sourceMessageId <= 0) {
                throw new IllegalArgumentException("sourceMessageId must be positive");
            }
            Objects.requireNonNull(errorCode, "errorCode");
            if (errorCode.isBlank()) {
                throw new IllegalArgumentException("errorCode must not be blank");
            }
        }
    }

    /** Immutable pre-/post-transition facts for optional contextual series in this one publication. */
    record PublicationContext(
            boolean personalCompleteEstablished,
            boolean personalPerfectEstablished,
            boolean sharedCompleteEstablished,
            boolean sharedPerfectEstablished) {

        public static PublicationContext none() {
            return new PublicationContext(false, false, false, false);
        }
    }

    /** Durable write-ahead attempt identified by the owning publication claim token. */
    record CanonicalDeliveryAttempt(long refreshGeneration) {
        public CanonicalDeliveryAttempt {
            if (refreshGeneration < 0) {
                throw new IllegalArgumentException("refreshGeneration must not be negative");
            }
        }
    }
    /** A current source paired with the generation that made a canonical refresh necessary. */
    record CanonicalRefreshCandidate(StoredSubmission submission, long refreshGeneration) {
        public CanonicalRefreshCandidate {
            Objects.requireNonNull(submission, "submission");
            if (refreshGeneration < 0) {
                throw new IllegalArgumentException("refreshGeneration must not be negative");
            }
        }
    }

    /** A refresh completion can succeed while a newer late side effect already requires another pass. */
    record CanonicalRefreshCompletion(boolean refreshStillRequired) {
    }

    record SourceDeletionClaim(UUID token, Instant leaseUntil) {
        public SourceDeletionClaim {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(leaseUntil, "leaseUntil");
        }
    }

    record StoredSubmission(
            long sourceMessageId,
            long guildId,
            long channelId,
            long authorPlayerId,
            String rawMessageContent,
            SubmissionState state,
            Optional<Long> gameResultId,
            List<AttachmentSnapshot> attachments,
            Optional<String> parserErrorCode,
            Optional<String> technicalErrorMessage,
            PublicationContext publicationContext,
            Optional<Instant> originalDeletedAt,
            OriginalDeletionFailure originalDeletionFailure,
            Optional<Instant> sourceDeletionLeaseUntil,
            Instant receivedAt,
            Instant updatedAt) {

        public StoredSubmission(
                long sourceMessageId,
                long guildId,
                long channelId,
                long authorPlayerId,
                String rawMessageContent,
                SubmissionState state,
                Optional<Long> gameResultId,
                List<AttachmentSnapshot> attachments,
                Optional<String> parserErrorCode,
                Optional<String> technicalErrorMessage,
                Instant receivedAt,
                Instant updatedAt) {
            this(
                    sourceMessageId,
                    guildId,
                    channelId,
                    authorPlayerId,
                    rawMessageContent,
                    state,
                    gameResultId,
                    attachments,
                    parserErrorCode,
                    technicalErrorMessage,
                    PublicationContext.none(),
                    Optional.empty(),
                    OriginalDeletionFailure.NONE,
                    Optional.empty(),
                    receivedAt,
                    updatedAt);
        }

        public StoredSubmission(
                long sourceMessageId,
                long guildId,
                long channelId,
                long authorPlayerId,
                String rawMessageContent,
                SubmissionState state,
                Optional<Long> gameResultId,
                List<AttachmentSnapshot> attachments,
                Optional<String> parserErrorCode,
                Optional<String> technicalErrorMessage,
                PublicationContext publicationContext,
                Instant receivedAt,
                Instant updatedAt) {
            this(
                    sourceMessageId,
                    guildId,
                    channelId,
                    authorPlayerId,
                    rawMessageContent,
                    state,
                    gameResultId,
                    attachments,
                    parserErrorCode,
                    technicalErrorMessage,
                    publicationContext,
                    Optional.empty(),
                    OriginalDeletionFailure.NONE,
                    Optional.empty(),
                    receivedAt,
                    updatedAt);
        }

        public StoredSubmission {
            attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(gameResultId, "gameResultId");
            Objects.requireNonNull(parserErrorCode, "parserErrorCode");
            Objects.requireNonNull(technicalErrorMessage, "technicalErrorMessage");
            Objects.requireNonNull(publicationContext, "publicationContext");
            Objects.requireNonNull(originalDeletedAt, "originalDeletedAt");
            Objects.requireNonNull(originalDeletionFailure, "originalDeletionFailure");
            Objects.requireNonNull(receivedAt, "receivedAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    enum SubmissionState {
        RECEIVED,
        VALIDATED,
        RESULT_STORED,
        CANONICAL_MESSAGE_PUBLISHED,
        ORIGINAL_MESSAGE_DELETED,
        COMPLETED,
        PARSE_REJECTED,
        FAILED_RETRYABLE,
        FAILED_FINAL,
        SUPERSEDED
    }

    enum OriginalDeletionFailure {
        NONE,
        RETRYABLE,
        PERMANENT
    }

    enum CanonicalPublicationPreparation {
        PUBLISHABLE,
        ALREADY_PUBLISHED,
        SUPERSEDED
    }
}
