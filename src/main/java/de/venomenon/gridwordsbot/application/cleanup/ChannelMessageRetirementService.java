package de.venomenon.gridwordsbot.application.cleanup;

import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.ChannelMessageRetirementStore;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import de.venomenon.gridwordsbot.port.out.ReminderMessageGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/** Executes token-owned visible-message retirement outside database transactions. */
public final class ChannelMessageRetirementService {
    private static final Duration LEASE = Duration.ofMinutes(2);

    private final ChannelMessageRetirementStore store;
    private final CanonicalMessageGateway canonicalMessages;
    private final ReminderMessageGateway reminderMessages;
    private final Clock clock;
    private final long guildId;
    private final long channelId;

    public ChannelMessageRetirementService(
            ChannelMessageRetirementStore store,
            CanonicalMessageGateway canonicalMessages,
            ReminderMessageGateway reminderMessages,
            Clock clock,
            long guildId,
            long channelId) {
        this.store = store;
        this.canonicalMessages = canonicalMessages;
        this.reminderMessages = reminderMessages;
        this.clock = clock;
        this.guildId = guildId;
        this.channelId = channelId;
    }

    /** Retires a sent stage-1 reminder only after stage 2 has a durable success state. */
    public boolean reconcileFirstReminderRetention(LocalDate date) {
        boolean completed = true;
        for (ChannelMessageRetirementStore.ReminderMessage message
                : store.findFirstReminderMessagesReadyForRetirement(guildId, channelId, date)) {
            completed &= retireReminder(message);
        }
        return completed;
    }

    public boolean retireResultMessagesBefore(LocalDate before) {
        boolean completed = true;
        for (ChannelMessageRetirementStore.ResultMessage message
                : store.findResultMessagesBefore(guildId, channelId, before)) {
            completed &= retireResult(message);
        }
        return completed;
    }

    public boolean retireReminderMessagesBefore(LocalDate before) {
        boolean completed = true;
        for (ChannelMessageRetirementStore.ReminderMessage message
                : store.findReminderMessagesBefore(guildId, channelId, before)) {
            completed &= retireReminder(message);
        }
        return completed;
    }

    private boolean retireResult(ChannelMessageRetirementStore.ResultMessage message) {
        Optional<ChannelMessageRetirementStore.ResultRetirementClaim> claimed =
                store.claimResultMessage(message.resultId(), clock.instant().plus(LEASE));
        if (claimed.isEmpty()) {
            return false;
        }
        try {
            canonicalMessages.delete(message.channelId(), message.messageId());
            store.completeResultRetirement(claimed.get());
            return true;
        } catch (CanonicalMessageGateway.UnknownMessageException ignored) {
            store.completeResultRetirement(claimed.get());
            return true;
        } catch (DiscordDeliveryException exception) {
            store.failResultRetirement(claimed.get(), exception.getMessage(), exception.permanent());
            return false;
        } catch (RuntimeException exception) {
            store.failResultRetirement(claimed.get(), "unexpected canonical retirement failure", false);
            return false;
        }
    }

    private boolean retireReminder(ChannelMessageRetirementStore.ReminderMessage message) {
        Optional<ChannelMessageRetirementStore.ReminderRetirementClaim> claimed =
                store.claimReminderMessage(
                        message.guildId(), message.channelId(), message.gameDate(), message.stage(),
                        clock.instant().plus(LEASE));
        if (claimed.isEmpty()) {
            return false;
        }
        try {
            reminderMessages.delete(message.channelId(), message.messageId());
            store.completeReminderRetirement(claimed.get());
            return true;
        } catch (DiscordDeliveryException exception) {
            store.failReminderRetirement(claimed.get(), exception.getMessage(), exception.permanent());
            return false;
        } catch (RuntimeException exception) {
            store.failReminderRetirement(claimed.get(), "unexpected reminder retirement failure", false);
            return false;
        }
    }
}
