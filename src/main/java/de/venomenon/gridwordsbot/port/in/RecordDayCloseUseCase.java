package de.venomenon.gridwordsbot.port.in;

import java.time.LocalDate;

/** Durable reconciliation of all record day closes up to one inclusive business date. */
@FunctionalInterface
public interface RecordDayCloseUseCase {
    int reconcileThrough(long guildId, LocalDate inclusiveCloseDate);
}
