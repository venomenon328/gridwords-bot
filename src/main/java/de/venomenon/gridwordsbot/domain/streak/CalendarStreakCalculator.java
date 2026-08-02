package de.venomenon.gridwordsbot.domain.streak;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Shared calendar-day core for current and longest streak calculations. */
public final class CalendarStreakCalculator {
    public int current(LocalDate asOfDate, boolean provisionalCurrentDay,
            Function<LocalDate, DayState> condition) {
        Objects.requireNonNull(asOfDate, "asOfDate");
        Objects.requireNonNull(condition, "condition");
        int count = 0;
        for (LocalDate day = asOfDate; ; day = day.minusDays(1)) {
            DayState state = condition.apply(day);
            if (state == DayState.MET) {
                count++;
                continue;
            }
            if (provisionalCurrentDay && day.equals(asOfDate) && state == DayState.PENDING) {
                continue;
            }
            return count;
        }
    }

    public StreakValues through(LocalDate firstRelevantDay, LocalDate asOfDate,
            Predicate<LocalDate> condition) {
        Objects.requireNonNull(firstRelevantDay, "firstRelevantDay");
        Objects.requireNonNull(asOfDate, "asOfDate");
        Objects.requireNonNull(condition, "condition");
        if (firstRelevantDay.isAfter(asOfDate)) {
            return new StreakValues(0, 0);
        }

        int current = 0;
        for (LocalDate day = asOfDate; !day.isBefore(firstRelevantDay); day = day.minusDays(1)) {
            if (!condition.test(day)) {
                break;
            }
            current++;
        }
        int record = 0;
        int running = 0;
        for (LocalDate day = firstRelevantDay; !day.isAfter(asOfDate); day = day.plusDays(1)) {
            if (condition.test(day)) {
                running++;
                record = Math.max(record, running);
            } else {
                running = 0;
            }
        }
        return new StreakValues(current, record);
    }

    public enum DayState { MET, PENDING, VIOLATED }

    public record StreakValues(int current, int record) {
        public StreakValues {
            if (current < 0 || record < 0 || current > record) {
                throw new IllegalArgumentException("invalid streak values");
            }
        }
    }
}
