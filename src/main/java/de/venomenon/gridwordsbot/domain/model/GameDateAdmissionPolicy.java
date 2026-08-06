package de.venomenon.gridwordsbot.domain.model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Pure business-time admission rule for ordinary user result processing.
 *
 * <p>The rule deliberately has no knowledge of persisted results, source-message
 * reception time, scheduler progress, or processing state.  A previous game day
 * is admissible only before the configured logical day-close time.</p>
 */
public final class GameDateAdmissionPolicy {
    private final Clock clock;
    private final ZoneId zoneId;
    private final LocalTime dayCloseTime;

    public GameDateAdmissionPolicy(Clock clock, ZoneId zoneId, LocalTime dayCloseTime) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.dayCloseTime = Objects.requireNonNull(dayCloseTime, "dayCloseTime");
    }

    /** Returns whether an ordinary user operation may process this game day now. */
    public boolean allows(LocalDate gameDate) {
        Objects.requireNonNull(gameDate, "gameDate");
        var localNow = clock.instant().atZone(zoneId);
        LocalDate today = localNow.toLocalDate();
        if (gameDate.equals(today)) {
            return true;
        }
        return gameDate.equals(today.minusDays(1)) && localNow.toLocalTime().isBefore(dayCloseTime);
    }
}
