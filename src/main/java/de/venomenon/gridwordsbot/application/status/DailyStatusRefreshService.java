package de.venomenon.gridwordsbot.application.status;

import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.port.out.DailyStatusMessageGateway;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Objects;

/** Claims delivery briefly, performs Discord I/O outside transactions, then records the outcome. */
public final class DailyStatusRefreshService {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private final DailyStatusProjector projector;
    private final DailyStatusStore store;
    private final DailyStatusMessageGateway messages;
    private final Clock clock;
    private final ZoneId zone;
    private final long guildId;
    private final long channelId;

    public DailyStatusRefreshService(DailyStatusProjector projector, DailyStatusStore store,
            DailyStatusMessageGateway messages, Clock clock, ZoneId zone, long guildId, long channelId) {
        this.projector = Objects.requireNonNull(projector);
        this.store = Objects.requireNonNull(store);
        this.messages = Objects.requireNonNull(messages);
        this.clock = Objects.requireNonNull(clock);
        this.zone = Objects.requireNonNull(zone);
        this.guildId = guildId;
        this.channelId = channelId;
    }

    /** A valid result may create the status for its game date. */
    public void refresh(LocalDate date) {
        deliver(date, true, false);
    }

    /** Recalculates an already-created status without creating one. */
    public void refreshExisting(LocalDate date) {
        deliver(date, false, true);
    }

    /** Startup/scheduler reconciliation optionally creates a missing status and verifies delivered presence. */
    public void reconcile(LocalDate date, boolean createIfMissing) {
        deliver(date, createIfMissing, true);
    }

    private void deliver(LocalDate date, boolean createIfMissing, boolean reconcileDelivered) {
        DailyStatus status = projector.project(date, clock.instant().atZone(zone).toLocalDate());
        if (status.players().isEmpty()) {
            return;
        }
        boolean hasResult = status.players().stream()
                .anyMatch(player -> player.gridWords().isPresent() || player.quadWords().isPresent());
        if (!createIfMissing && !hasResult && !store.statusExists(guildId, channelId, date)) {
            return;
        }
        String fingerprint = fingerprint(status);
        DailyStatusStore.StatusDelivery claim = store.claimStatus(
                guildId, channelId, date, fingerprint, reconcileDelivered, clock.instant().plus(LEASE)).orElse(null);
        if (claim == null) {
            return;
        }
        boolean contentChanged = claim.previousFingerprint().filter(fingerprint::equals).isEmpty();
        try {
            long messageId = messages.publishOrEdit(channelId, claim.discordMessageId(), status, contentChanged);
            store.completeStatus(claim, messageId, fingerprint);
        } catch (DiscordDeliveryException exception) {
            store.failStatus(claim, exception.getMessage(), exception.permanent());
        } catch (RuntimeException exception) {
            store.failStatus(claim, "unexpected status delivery failure", false);
        }
    }

    static String fingerprint(DailyStatus status) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(status.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
