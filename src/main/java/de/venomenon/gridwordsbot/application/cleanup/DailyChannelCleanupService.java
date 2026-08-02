package de.venomenon.gridwordsbot.application.cleanup;

import de.venomenon.gridwordsbot.application.status.DailyStatusRefreshService;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Idempotent day-close order: finalise yesterday's status, retire results, retire reminders, then create today.
 */
public final class DailyChannelCleanupService {
    private final DailyStatusRefreshService status;
    private final ChannelMessageRetirementService retirement;
    private final DailyStatusStore deliveries;
    private final Clock clock;
    private final ZoneId zone;
    private final LocalTime cleanupTime;
    private final long guildId;
    private final long channelId;

    public DailyChannelCleanupService(
            DailyStatusRefreshService status,
            ChannelMessageRetirementService retirement,
            DailyStatusStore deliveries,
            Clock clock,
            ZoneId zone,
            LocalTime cleanupTime,
            long guildId,
            long channelId) {
        this.status = Objects.requireNonNull(status);
        this.retirement = Objects.requireNonNull(retirement);
        this.deliveries = Objects.requireNonNull(deliveries);
        this.clock = Objects.requireNonNull(clock);
        this.zone = Objects.requireNonNull(zone);
        this.cleanupTime = Objects.requireNonNull(cleanupTime);
        this.guildId = guildId;
        this.channelId = channelId;
    }

    public void reconcile() {
        var now = clock.instant().atZone(zone);
        if (now.toLocalTime().isBefore(cleanupTime)) {
            return;
        }

        LocalDate today = now.toLocalDate();
        if (!status.reconcile(today.minusDays(1), true)) {
            return;
        }
        if (!retirement.retireResultMessagesBefore(today)) {
            return;
        }

        deliveries.expireOpenRemindersBefore(guildId, channelId, today);
        if (!retirement.retireReminderMessagesBefore(today)) {
            return;
        }
        status.reconcile(today, true);
    }
}
