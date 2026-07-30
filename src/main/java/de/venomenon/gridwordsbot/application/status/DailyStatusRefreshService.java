package de.venomenon.gridwordsbot.application.status;

import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.port.out.DailyStatusMessageGateway;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Claims delivery in a short transaction, performs Discord I/O outside it, then records the outcome. */
public final class DailyStatusRefreshService {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private final DailyStatusProjector projector;
    private final DailyStatusStore store;
    private final DailyStatusMessageGateway messages;
    private final Clock clock;
    private final ZoneId zone;
    private final long guildId;
    private final long channelId;

    public DailyStatusRefreshService(DailyStatusProjector projector, DailyStatusStore store, DailyStatusMessageGateway messages,
            Clock clock, ZoneId zone, long guildId, long channelId) {
        this.projector = Objects.requireNonNull(projector); this.store = Objects.requireNonNull(store);
        this.messages = Objects.requireNonNull(messages); this.clock = Objects.requireNonNull(clock);
        this.zone = Objects.requireNonNull(zone); this.guildId = guildId; this.channelId = channelId;
    }
    public void refresh(LocalDate date) {
        DailyStatusStore.StatusDelivery claim = store.claimStatus(guildId, channelId, date, clock.instant().plus(LEASE)).orElse(null);
        if (claim == null) return;
        try {
            DailyStatus status = projector.project(date, clock.instant().atZone(zone).toLocalDate());
            if (status.players().isEmpty()) { store.failStatus(claim, "no active participants", true); return; }
            long messageId = messages.publishOrEdit(channelId, claim.discordMessageId(), status);
            store.completeStatus(claim, messageId, Integer.toHexString(status.hashCode()));
        } catch (RuntimeException exception) {
            store.failStatus(claim, "status delivery failed", false);
        }
    }
}
