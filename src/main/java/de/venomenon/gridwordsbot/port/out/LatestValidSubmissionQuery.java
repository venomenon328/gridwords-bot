package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Reads at most one valid, non-superseded submission per game type for one player. */
public interface LatestValidSubmissionQuery {
    List<LatestValidSubmission> findLatestValidSubmissions(long discordUserId);

    record LatestValidSubmission(
            GameType gameType,
            ShareOutcome outcome,
            Duration duration,
            LocalDate gameDate,
            Instant receivedAt) {
        public LatestValidSubmission {
            Objects.requireNonNull(gameType, "gameType");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(gameDate, "gameDate");
            Objects.requireNonNull(receivedAt, "receivedAt");
            if (duration.isNegative()) {
                throw new IllegalArgumentException("duration must not be negative");
            }
        }
    }
}
