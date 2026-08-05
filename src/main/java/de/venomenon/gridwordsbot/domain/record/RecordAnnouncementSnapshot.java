package de.venomenon.gridwordsbot.domain.record;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RecordAnnouncementSnapshot(
        RecordAnnouncementRegistration registration,
        RecordWorkState state,
        Optional<UUID> claimToken,
        Optional<Instant> claimUntil,
        int attemptCount,
        Optional<Instant> nextRetryAt,
        Optional<RecordWorkFailure> failure,
        List<RecordAnnouncementMessage> messages,
        Optional<Instant> publishedAt,
        Optional<Instant> changedAt,
        Optional<Instant> deletedAt,
        Optional<Instant> externallyRemovedAt,
        Instant createdAt,
        Instant updatedAt) {
    public RecordAnnouncementSnapshot {
        Objects.requireNonNull(registration, "registration"); Objects.requireNonNull(state, "state");
        claimToken = Objects.requireNonNull(claimToken, "claimToken"); claimUntil = Objects.requireNonNull(claimUntil, "claimUntil");
        nextRetryAt = Objects.requireNonNull(nextRetryAt, "nextRetryAt"); failure = Objects.requireNonNull(failure, "failure");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt"); changedAt = Objects.requireNonNull(changedAt, "changedAt");
        deletedAt = Objects.requireNonNull(deletedAt, "deletedAt"); externallyRemovedAt = Objects.requireNonNull(externallyRemovedAt, "externallyRemovedAt");
        Objects.requireNonNull(createdAt, "createdAt"); Objects.requireNonNull(updatedAt, "updatedAt");
        if (attemptCount < 0 || updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("invalid announcement counters or timestamps");
        if ((state == RecordWorkState.CLAIMED) != (claimToken.isPresent() && claimUntil.isPresent())) throw new IllegalArgumentException("invalid announcement claim");
        if ((state == RecordWorkState.RETRYABLE) != nextRetryAt.isPresent()) throw new IllegalArgumentException("invalid announcement retry");
        if ((state == RecordWorkState.EXTERNALLY_REMOVED) != externallyRemovedAt.isPresent()) throw new IllegalArgumentException("invalid external removal state");
        if (messages.stream().map(RecordAnnouncementMessage::position).distinct().count() != messages.size()
                || messages.stream().map(RecordAnnouncementMessage::messageId).distinct().count() != messages.size()) {
            throw new IllegalArgumentException("announcement messages must be unique");
        }
    }
}
