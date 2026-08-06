package de.venomenon.gridwordsbot.domain.record;

import java.time.LocalDate;
import java.util.Objects;

/** Stable persistent identity of the logical close of one business day. */
public record RecordDayCloseKey(long guildId, RecordDefinitionVersion definitionVersion, LocalDate gameDate) {
    public RecordDayCloseKey {
        if (guildId <= 0) {
            throw new IllegalArgumentException("guildId must be positive");
        }
        Objects.requireNonNull(definitionVersion, "definitionVersion");
        Objects.requireNonNull(gameDate, "gameDate");
    }
}
