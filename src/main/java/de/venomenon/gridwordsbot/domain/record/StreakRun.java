package de.venomenon.gridwordsbot.domain.record;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** One fully derived positive or negative calendar-day streak run. */
public record StreakRun(StreakRunIdentity identity, LocalDate endDate, StreakRunStatus status) {
    public StreakRun {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(status, "status");
        if (endDate.isBefore(identity.startDate())) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
    }

    public int length() {
        long days = ChronoUnit.DAYS.between(identity.startDate(), endDate) + 1;
        if (days > Integer.MAX_VALUE) throw new IllegalStateException("streak run is too long");
        return Math.toIntExact(days);
    }

    public boolean completed() {
        return status.completed();
    }

    public StreakRecordValue value() {
        return new StreakRecordValue(length(), identity.startDate(), endDate);
    }

    public RecordSourceReference.StreakRun sourceReference() {
        RecordSourceReference.StreakRunOwner owner = switch (identity.ownerScope()) {
            case RecordScope.Personal personal -> new RecordSourceReference.StreakRunOwner.Player(personal.playerId());
            case RecordScope.Shared ignored -> new RecordSourceReference.StreakRunOwner.Shared();
            case RecordScope.ServerIndividual ignored -> throw new IllegalStateException("invalid run owner");
        };
        return new RecordSourceReference.StreakRun(identity.metric(), owner, identity.startDate());
    }
}
