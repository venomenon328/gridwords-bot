package de.venomenon.gridwordsbot.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

/** Durable, short-transaction state for daily status refreshes and reminder deliveries. */
public interface DailyStatusStore {
    Optional<StatusDelivery> claimStatus(long guildId, long channelId, LocalDate date, String fingerprint,
                                         boolean reconcileDelivered, Instant leaseUntil);
    void completeStatus(StatusDelivery claim, long discordMessageId, String fingerprint);
    void failStatus(StatusDelivery claim, String safeError, boolean permanent);
    boolean statusExists(long guildId, long channelId, LocalDate date);

    Optional<ReminderDelivery> claimReminder(long guildId, long channelId, LocalDate date, int stage,
                                             LocalTime scheduledTime, Instant leaseUntil);
    void completeReminder(ReminderDelivery claim, ReminderState state, Optional<Long> discordMessageId);
    void failReminder(ReminderDelivery claim, String safeError, boolean permanent);
    void supersedeReminder(long guildId, long channelId, LocalDate date, int stage, LocalTime scheduledTime);
    void expireOpenRemindersBefore(long guildId, long channelId, LocalDate today);

    record StatusDelivery(long guildId, long channelId, LocalDate gameDate, UUID claimToken,
                          Optional<Long> discordMessageId, Optional<String> previousFingerprint, String requestedFingerprint) { }
    record ReminderDelivery(long guildId, long channelId, LocalDate gameDate, int stage, LocalTime scheduledTime,
                            UUID claimToken) { }
    enum ReminderState { SENT, NO_CANDIDATES, SUPERSEDED, EXPIRED }
}