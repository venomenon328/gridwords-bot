package de.venomenon.gridwordsbot.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable retirement intent for visible channel messages; business results remain untouched. */
public interface ChannelMessageRetirementStore {

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

    record ResultMessage(long resultId, long channelId, long messageId, LocalDate gameDate) {
        public ResultMessage {
            if (resultId <= 0 || channelId <= 0 || messageId <= 0) {
                throw new IllegalArgumentException("result, channel and message IDs must be positive");
            }
        }
    }

    record ReminderMessage(long guildId, long channelId, LocalDate gameDate, int stage, long messageId) {
        public ReminderMessage {
            if (guildId <= 0 || channelId <= 0 || messageId <= 0 || (stage != 1 && stage != 2)) {
                throw new IllegalArgumentException("invalid reminder message key");
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

