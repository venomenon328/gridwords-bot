package de.venomenon.gridwordsbot.port.out;

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

    default List<StoredSubmission> findGridWordsAwaitingCanonicalPublication() {
        throw new UnsupportedOperationException("publication recovery is not available");
    }

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

    /** Carries the configured pair so persistence can atomically record the actual day-state transition. */
    record ResultStorage(long sourceMessageId, GameResultStore.GameResultUpsert result, List<Long> configuredPlayerIds) {

        public ResultStorage(long sourceMessageId, GameResultStore.GameResultUpsert result) {
            this(sourceMessageId, result, List.of());
        }

        public ResultStorage {
            if (sourceMessageId <= 0) {
                throw new IllegalArgumentException("sourceMessageId must be positive");
            }
            Objects.requireNonNull(result, "result");
            configuredPlayerIds = List.copyOf(Objects.requireNonNull(configuredPlayerIds, "configuredPlayerIds"));
            if (!configuredPlayerIds.isEmpty()
                    && (configuredPlayerIds.size() != 2
                    || configuredPlayerIds.stream().distinct().count() != 2
                    || configuredPlayerIds.stream().anyMatch(playerId -> playerId <= 0))) {
                throw new IllegalArgumentException("configuredPlayerIds must contain exactly two distinct positive IDs");
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
        FAILED_FINAL
    }
}