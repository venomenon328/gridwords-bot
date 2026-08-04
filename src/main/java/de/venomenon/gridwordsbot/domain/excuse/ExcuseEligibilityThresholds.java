package de.venomenon.gridwordsbot.domain.excuse;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/** Typed, closed MVP thresholds for determining whether a result merits an excuse offer. */
public record ExcuseEligibilityThresholds(
        ZoneId businessZone,
        LocalTime veryLateSubmissionAt,
        Duration gridWordsVerySlow,
        Duration quadWordsVerySlow,
        int worstSolvedBoardMinimumAttempt,
        int worstBoardMinimumGap,
        int gridWordsOutlierMinimumAttempts,
        int gridWordsOutlierMinimumAttemptGap,
        Duration gridWordsOutlierMinimumDuration,
        Duration gridWordsOutlierMinimumDurationGap,
        Duration quadWordsOutlierMinimumDuration,
        Duration quadWordsOutlierMinimumDurationGap) {

    public ExcuseEligibilityThresholds {
        Objects.requireNonNull(businessZone, "businessZone");
        Objects.requireNonNull(veryLateSubmissionAt, "veryLateSubmissionAt");
        gridWordsVerySlow = positiveDuration(gridWordsVerySlow, "gridWordsVerySlow");
        quadWordsVerySlow = positiveDuration(quadWordsVerySlow, "quadWordsVerySlow");
        if (worstSolvedBoardMinimumAttempt < 1 || worstBoardMinimumGap < 1
                || gridWordsOutlierMinimumAttempts < 1 || gridWordsOutlierMinimumAttemptGap < 1) {
            throw new IllegalArgumentException("attempt thresholds must be positive");
        }
        gridWordsOutlierMinimumDuration = positiveDuration(
                gridWordsOutlierMinimumDuration, "gridWordsOutlierMinimumDuration");
        gridWordsOutlierMinimumDurationGap = positiveDuration(
                gridWordsOutlierMinimumDurationGap, "gridWordsOutlierMinimumDurationGap");
        quadWordsOutlierMinimumDuration = positiveDuration(
                quadWordsOutlierMinimumDuration, "quadWordsOutlierMinimumDuration");
        quadWordsOutlierMinimumDurationGap = positiveDuration(
                quadWordsOutlierMinimumDurationGap, "quadWordsOutlierMinimumDurationGap");
    }

    public static ExcuseEligibilityThresholds defaults() {
        return new ExcuseEligibilityThresholds(
                ZoneId.of("Europe/Berlin"),
                LocalTime.of(23, 30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(8),
                8,
                3,
                5,
                2,
                Duration.ofMinutes(4),
                Duration.ofMinutes(2),
                Duration.ofMinutes(6),
                Duration.ofMinutes(3));
    }

    private static Duration positiveDuration(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
