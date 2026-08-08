package de.venomenon.gridwordsbot.domain.achievement.persistence;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistente gewünschte Projektion genau einer öffentlichen Achievement-Nachricht. */
public final class AchievementAnnouncement {
    private AchievementAnnouncement() {}

    public enum Type { LIVE_UNLOCK_BATCH, HISTORICAL_INTRODUCTION }

    public enum DeliveryState {
        OPEN,
        CLAIMED,
        RETRYABLE,
        SYNCHRONIZED,
        FAILED_PERMANENT,
        EXTERNALLY_REMOVED,
        SUPPRESSED
    }

    public record Key(long guildId, String idempotencyKey) {
        public Key {
            if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
            idempotencyKey = AchievementAwardState.requireText(idempotencyKey, "idempotencyKey");
        }
    }

    public record Registration(
            long guildId,
            long channelId,
            long participantId,
            AchievementDefinitionVersion definitionVersion,
            Type type,
            String idempotencyKey,
            String rendererVersion,
            String contentFingerprint) {
        public Registration {
            if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
            if (channelId <= 0) throw new IllegalArgumentException("channelId must be positive");
            if (participantId <= 0) throw new IllegalArgumentException("participantId must be positive");
            Objects.requireNonNull(definitionVersion, "definitionVersion");
            Objects.requireNonNull(type, "type");
            idempotencyKey = AchievementAwardState.requireText(idempotencyKey, "idempotencyKey");
            rendererVersion = AchievementAwardState.requireText(rendererVersion, "rendererVersion");
            contentFingerprint = AchievementAwardState.requireText(contentFingerprint, "contentFingerprint");
            if (!contentFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("contentFingerprint must be lowercase SHA-256 hex");
            }
        }
        public Key key() { return new Key(guildId, idempotencyKey); }
    }

    public record Item(int position, UUID eventId) {
        public Item {
            if (position < 0) throw new IllegalArgumentException("position must not be negative");
            Objects.requireNonNull(eventId, "eventId");
        }
    }

    public record Snapshot(
            long id,
            Registration registration,
            DeliveryState deliveryState,
            Optional<UUID> claimToken,
            Optional<Instant> claimUntil,
            int attemptCount,
            Optional<Instant> nextRetryAt,
            Optional<AchievementWork.Failure> failure,
            Optional<Long> discordMessageId,
            Optional<Instant> deliveredAt,
            Optional<Instant> synchronizedAt,
            Optional<Instant> externallyRemovedAt,
            Optional<Instant> suppressedAt,
            Instant createdAt,
            Instant updatedAt) {
        public Snapshot {
            if (id <= 0) throw new IllegalArgumentException("id must be positive");
            Objects.requireNonNull(registration, "registration");
            Objects.requireNonNull(deliveryState, "deliveryState");
            claimToken = Objects.requireNonNull(claimToken, "claimToken");
            claimUntil = Objects.requireNonNull(claimUntil, "claimUntil");
            nextRetryAt = Objects.requireNonNull(nextRetryAt, "nextRetryAt");
            failure = Objects.requireNonNull(failure, "failure");
            discordMessageId = Objects.requireNonNull(discordMessageId, "discordMessageId");
            deliveredAt = Objects.requireNonNull(deliveredAt, "deliveredAt");
            synchronizedAt = Objects.requireNonNull(synchronizedAt, "synchronizedAt");
            externallyRemovedAt = Objects.requireNonNull(externallyRemovedAt, "externallyRemovedAt");
            suppressedAt = Objects.requireNonNull(suppressedAt, "suppressedAt");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (attemptCount < 0) throw new IllegalArgumentException("attemptCount must not be negative");
            if (discordMessageId.isPresent() && discordMessageId.orElseThrow() <= 0) {
                throw new IllegalArgumentException("discordMessageId must be positive");
            }
            if ((deliveryState == DeliveryState.CLAIMED) != (claimToken.isPresent() && claimUntil.isPresent())) {
                throw new IllegalArgumentException("claim token and lease are required exactly for CLAIMED state");
            }
            if ((deliveryState == DeliveryState.RETRYABLE) != nextRetryAt.isPresent()) {
                throw new IllegalArgumentException("nextRetryAt is required exactly for RETRYABLE state");
            }
            if (discordMessageId.isPresent() != deliveredAt.isPresent()) {
                throw new IllegalArgumentException("discordMessageId and deliveredAt must be present together");
            }
            if ((deliveryState == DeliveryState.SYNCHRONIZED) != synchronizedAt.isPresent()) {
                throw new IllegalArgumentException("synchronizedAt is required exactly for SYNCHRONIZED state");
            }
            if ((deliveryState == DeliveryState.EXTERNALLY_REMOVED) != externallyRemovedAt.isPresent()) {
                throw new IllegalArgumentException("externallyRemovedAt is required exactly for EXTERNALLY_REMOVED state");
            }
            if ((deliveryState == DeliveryState.SUPPRESSED) != suppressedAt.isPresent()) {
                throw new IllegalArgumentException("suppressedAt is required exactly for SUPPRESSED state");
            }
            if (deliveryState == DeliveryState.SYNCHRONIZED && discordMessageId.isEmpty()) {
                throw new IllegalArgumentException("SYNCHRONIZED announcement requires confirmed Discord message");
            }
            if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }
}
