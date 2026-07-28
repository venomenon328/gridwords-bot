package de.venomenon.gridwordsbot.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persistence boundary for the resumable, source-message based submission state machine. */
public interface SubmissionStore {
    StoredSubmission register(SubmissionRegistration registration);
    Optional<StoredSubmission> findBySourceMessageId(long sourceMessageId);
    StoredSubmission storeResult(ResultStorage request);
    boolean transition(long sourceMessageId, SubmissionState expectedState, SubmissionState targetState);

    record SubmissionRegistration(long sourceMessageId, long guildId, long channelId, long authorPlayerId,
                                  String rawMessageContent, List<AttachmentSnapshot> attachments, Instant receivedAt) {
        public SubmissionRegistration {
            if (sourceMessageId <= 0 || guildId <= 0 || channelId <= 0 || authorPlayerId <= 0) throw new IllegalArgumentException("Discord IDs must be positive");
            Objects.requireNonNull(rawMessageContent, "rawMessageContent");
            attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
            Objects.requireNonNull(receivedAt, "receivedAt");
        }
    }
    record AttachmentSnapshot(int index, String fileName, Optional<String> contentType, long sizeBytes) {
        public AttachmentSnapshot {
            if (index < 0 || sizeBytes < 0) throw new IllegalArgumentException("attachment index and size must not be negative");
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(contentType, "contentType");
        }
    }
    record ResultStorage(long sourceMessageId, GameResultStore.GameResultUpsert result) {
        public ResultStorage { if (sourceMessageId <= 0) throw new IllegalArgumentException("sourceMessageId must be positive"); Objects.requireNonNull(result, "result"); }
    }
    record StoredSubmission(long sourceMessageId, long guildId, long channelId, long authorPlayerId, String rawMessageContent,
                            SubmissionState state, Optional<Long> gameResultId, List<AttachmentSnapshot> attachments,
                            Instant receivedAt, Instant updatedAt) {
        public StoredSubmission { attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments")); Objects.requireNonNull(state, "state"); Objects.requireNonNull(gameResultId, "gameResultId"); }
    }
    enum SubmissionState { RECEIVED, VALIDATED, RESULT_STORED, CANONICAL_MESSAGE_PUBLISHED, ORIGINAL_MESSAGE_DELETED, COMPLETED, PARSE_REJECTED, FAILED_RETRYABLE, FAILED_FINAL }
}
