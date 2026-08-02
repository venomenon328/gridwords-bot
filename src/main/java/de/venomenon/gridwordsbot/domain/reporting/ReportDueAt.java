package de.venomenon.gridwordsbot.domain.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/** A report due time expressed by its business-local date, time, and timezone. */
public record ReportDueAt(LocalDate localDate, LocalTime localTime, ZoneId zone) {
    public ReportDueAt {
        Objects.requireNonNull(localDate, "localDate");
        Objects.requireNonNull(localTime, "localTime");
        Objects.requireNonNull(zone, "zone");
    }

    public Instant instant() {
        return localDate.atTime(localTime).atZone(zone).toInstant();
    }
}
