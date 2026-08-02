package de.venomenon.gridwordsbot.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Durable retirement intent for visible channel messages; business results remain untouched. */
public interface ChannelMessageRetirementStore extends CanonicalPublicationRetirementQuery {

    List<ResultMessage> findResultMessagesBefore(long guildId, long channelId, LocalDate before);

    List<ReminderMessage> findReminderMessagesBefore(long guildId, long channelId, LocalDate before);

    List<ReminderMessage> findFirstReminderMessagesReadyForRetirement(
            long guildId, long channelId, LocalDate date);

    Optional<ResultRetirementClaim> claimResultMessage(long resultId, Instant leaseUntil);

    Optional<ReminderRetirementClaim> claimReminderMessage(
            long guildId, long channelId, LocalDate gameDate, int stage, Instant leaseUntil);

    void completeResultRetirement(ResultRetirementClaim claim);

    void completeReminderRetirement(ReminderRetirementClaim claim);

    void failResultRetirement(ResultRetirementClaim claim, String safeError, boolean permanent);

    void failReminderRetirement(ReminderRetirementClaim claim, String safeError, boolean permanent);

    /** A non-active retirement row is a durable fence against canonical recovery. */
    boolean isCanonicalPublicationAllowed(long resultId);

    record ResultMessage(
            long resultId,
            long channelId,
            OptionalLong messageId,
            LocalDate gameDate,
            String publicationKey) {
        public ResultMessage {
            if (resultId <= 0 || channelId <= 0) {
                throw new IllegalArgumentException("result and channel IDs must be positive");
            }
            java.util.Objects.requireNonNull(messageId, "messageId");
            java.util.Objects.requireNonNull(gameDate, "gameDate");
            java.util.Objects.requireNonNull(publicationKey, "publicationKey");
            if (messageId.isPresent() && messageId.getAsLong() <= 0) {
                throw new IllegalArgumentException("messageId must be positive when present");
            }
            if (publicationKey.isBlank()) {
                throw new IllegalArgumentException("publicationKey must not be blank");
            }
        }
    }

    record ReminderMessage(
            long guildId,
            long channelId,
            LocalDate gameDate,
            int stage,
            OptionalLong messageId) {
        public ReminderMessage {
            if (guildId <= 0 || channelId <= 0 || (stage != 1 && stage != 2)) {
                throw new IllegalArgumentException("invalid reminder message key");
            }
            java.util.Objects.requireNonNull(gameDate, "gameDate");
            java.util.Objects.requireNonNull(messageId, "messageId");
            if (messageId.isPresent() && messageId.getAsLong() <= 0) {
                throw new IllegalArgumentException("messageId must be positive when present");
            }
        }
    }

    record ResultRetirementClaim(long resultId, UUID token, Instant leaseUntil) {
        public ResultRetirementClaim {
            if (resultId <= 0) {
                throw new IllegalArgumentException("resultId must be positive");
            }
            java.util.Objects.requireNonNull(token, "token");
            java.util.Objects.requireNonNull(leaseUntil, "leaseUntil");
        }
    }

    record ReminderRetirementClaim(
            long guildId, long channelId, LocalDate gameDate, int stage, UUID token, Instant leaseUntil) {
        public ReminderRetirementClaim {
            if (guildId <= 0 || channelId <= 0 || (stage != 1 && stage != 2)) {
                throw new IllegalArgumentException("invalid reminder retirement key");
            }
            java.util.Objects.requireNonNull(gameDate, "gameDate");
            java.util.Objects.requireNonNull(token, "token");
            java.util.Objects.requireNonNull(leaseUntil, "leaseUntil");
        }
    }
}
