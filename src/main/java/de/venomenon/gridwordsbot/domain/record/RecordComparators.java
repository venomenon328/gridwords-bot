package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;

/** Die vier expliziten Vergleichsordnungen der versionierten Rekordkataloge. */
public final class RecordComparators {
    private static final RecordValueComparator<AttemptsDurationRecordValue> FEWEST_ATTEMPTS =
            new FewestAttemptsComparator();
    private static final RecordValueComparator<DurationRecordValue> FASTEST_DURATION =
            new FastestDurationComparator();
    private static final RecordValueComparator<DurationRecordValue> SLOWEST_DURATION =
            new SlowestDurationComparator();
    private static final RecordValueComparator<StreakRecordValue> LONGEST_STREAK =
            new LongestStreakComparator();

    private RecordComparators() {
    }

    public static RecordValueComparator<AttemptsDurationRecordValue> fewestAttempts() {
        return FEWEST_ATTEMPTS;
    }

    public static RecordValueComparator<DurationRecordValue> fastestDuration() {
        return FASTEST_DURATION;
    }

    public static RecordValueComparator<DurationRecordValue> slowestDuration() {
        return SLOWEST_DURATION;
    }

    public static RecordValueComparator<StreakRecordValue> longestStreak() {
        return LONGEST_STREAK;
    }

    private static RecordComparison fromComparison(int comparison) {
        if (comparison < 0) return RecordComparison.BETTER;
        if (comparison > 0) return RecordComparison.WORSE;
        return RecordComparison.EQUAL;
    }

    private static final class FewestAttemptsComparator
            implements RecordValueComparator<AttemptsDurationRecordValue> {
        @Override
        public Class<AttemptsDurationRecordValue> valueType() {
            return AttemptsDurationRecordValue.class;
        }

        @Override
        public RecordComparison compare(AttemptsDurationRecordValue candidate, AttemptsDurationRecordValue current) {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(current, "current");
            int attempts = Integer.compare(candidate.attempts(), current.attempts());
            if (attempts != 0) return fromComparison(attempts);
            return fromComparison(candidate.duration().compareTo(current.duration()));
        }
    }

    private static final class FastestDurationComparator implements RecordValueComparator<DurationRecordValue> {
        @Override
        public Class<DurationRecordValue> valueType() {
            return DurationRecordValue.class;
        }

        @Override
        public RecordComparison compare(DurationRecordValue candidate, DurationRecordValue current) {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(current, "current");
            return fromComparison(candidate.duration().compareTo(current.duration()));
        }
    }

    private static final class SlowestDurationComparator implements RecordValueComparator<DurationRecordValue> {
        @Override
        public Class<DurationRecordValue> valueType() {
            return DurationRecordValue.class;
        }

        @Override
        public RecordComparison compare(DurationRecordValue candidate, DurationRecordValue current) {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(current, "current");
            return fromComparison(current.duration().compareTo(candidate.duration()));
        }
    }

    private static final class LongestStreakComparator implements RecordValueComparator<StreakRecordValue> {
        @Override
        public Class<StreakRecordValue> valueType() {
            return StreakRecordValue.class;
        }

        @Override
        public RecordComparison compare(StreakRecordValue candidate, StreakRecordValue current) {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(current, "current");
            return fromComparison(Integer.compare(current.length(), candidate.length()));
        }
    }
}
