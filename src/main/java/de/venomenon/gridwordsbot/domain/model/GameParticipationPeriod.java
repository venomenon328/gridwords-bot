package de.venomenon.gridwordsbot.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** A historical interval during which one player participates in one concrete game. */
public record GameParticipationPeriod(long playerId, GameType gameType, LocalDate activeFrom, LocalDate inactiveFrom) {
    public GameParticipationPeriod {
        if (playerId <= 0) throw new IllegalArgumentException("playerId must be positive");
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(activeFrom, "activeFrom");
        if (inactiveFrom != null && !inactiveFrom.isAfter(activeFrom)) {
            throw new IllegalArgumentException("inactiveFrom must be after activeFrom");
        }
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return !date.isBefore(activeFrom) && (inactiveFrom == null || date.isBefore(inactiveFrom));
    }
}
