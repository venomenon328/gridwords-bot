package de.venomenon.gridwordsbot.domain.record;

/** Mindestbasis und öffentliche Längenschwellen einer Definition. */
public sealed interface RecordAnnouncementThreshold
        permits RecordAnnouncementThreshold.Result, RecordAnnouncementThreshold.Streak {

    record Result(int minimumPriorSolvedResults, int minimumPriorDistinctPlayers)
            implements RecordAnnouncementThreshold {
        public Result {
            if (minimumPriorSolvedResults <= 0) {
                throw new IllegalArgumentException("minimumPriorSolvedResults must be positive");
            }
            if (minimumPriorDistinctPlayers <= 0) {
                throw new IllegalArgumentException("minimumPriorDistinctPlayers must be positive");
            }
            if (minimumPriorDistinctPlayers > minimumPriorSolvedResults) {
                throw new IllegalArgumentException("distinct players cannot exceed prior solved results");
            }
        }
    }

    record Streak(int minimumLength, int minimumPriorCompletedRuns, int minimumPriorDistinctPlayers)
            implements RecordAnnouncementThreshold {
        public Streak {
            if (minimumLength <= 0) throw new IllegalArgumentException("minimumLength must be positive");
            if (minimumPriorCompletedRuns <= 0) {
                throw new IllegalArgumentException("minimumPriorCompletedRuns must be positive");
            }
            if (minimumPriorDistinctPlayers < 0) {
                throw new IllegalArgumentException("minimumPriorDistinctPlayers must not be negative");
            }
            if (minimumPriorDistinctPlayers > minimumPriorCompletedRuns) {
                throw new IllegalArgumentException("distinct players cannot exceed prior completed runs");
            }
        }
    }
}
