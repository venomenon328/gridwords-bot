package de.venomenon.gridwordsbot.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/** A historical interval during which one player participates in shared day states. */
public record ParticipationPeriod(long playerId, LocalDate activeFrom, LocalDate inactiveFrom) {
    public ParticipationPeriod {
        if (playerId <= 0) throw new IllegalArgumentException("playerId must be positive");
        Objects.requireNonNull(activeFrom, "activeFrom");
        if (inactiveFrom != null && !inactiveFrom.isAfter(activeFrom)) throw new IllegalArgumentException("inactiveFrom must be after activeFrom");
    }
    public boolean contains(LocalDate date) { return !date.isBefore(activeFrom) && (inactiveFrom == null || date.isBefore(inactiveFrom)); }
}
