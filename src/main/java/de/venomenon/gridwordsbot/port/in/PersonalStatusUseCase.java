package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Provides a structured, transport-neutral status projection for the calling player only. */
public interface PersonalStatusUseCase {
    PersonalStatus status(PlayerIdentity actor);

    record PlayerIdentity(long discordUserId, String displayName) {
        public PlayerIdentity {
            if (discordUserId <= 0) {
                throw new IllegalArgumentException("discordUserId must be positive");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("displayName must not be blank");
            }
        }
    }

    record PersonalStatus(
            ParticipationStatus participation,
            boolean reminderOptIn,
            Optional<LatestSubmission> latestGridWordsSubmission,
            Optional<LatestSubmission> latestQuadWordsSubmission) {
        public PersonalStatus {
            Objects.requireNonNull(participation, "participation");
            latestGridWordsSubmission = requiredSubmission(
                    latestGridWordsSubmission, GameType.GRIDWORDS, "latestGridWordsSubmission");
            latestQuadWordsSubmission = requiredSubmission(
                    latestQuadWordsSubmission, GameType.QUADWORDS, "latestQuadWordsSubmission");
        }

        private static Optional<LatestSubmission> requiredSubmission(
                Optional<LatestSubmission> submission, GameType expectedType, String name) {
            Objects.requireNonNull(submission, name);
            submission.ifPresent(value -> {
                if (value.gameType() != expectedType) {
                    throw new IllegalArgumentException(name + " must have game type " + expectedType);
                }
            });
            return submission;
        }
    }

    record ParticipationStatus(
            boolean active,
            Optional<LocalDate> activeFrom,
            Optional<LocalDate> activeUntil) {
        public ParticipationStatus {
            Objects.requireNonNull(activeFrom, "activeFrom");
            Objects.requireNonNull(activeUntil, "activeUntil");
            if (active && activeFrom.isEmpty()) {
                throw new IllegalArgumentException("active participation requires activeFrom");
            }
            if (!active && (activeFrom.isPresent() || activeUntil.isPresent())) {
                throw new IllegalArgumentException("inactive participation must not expose a current period");
            }
            if (activeFrom.isPresent() && activeUntil.isPresent()
                    && activeUntil.orElseThrow().isBefore(activeFrom.orElseThrow())) {
                throw new IllegalArgumentException("activeUntil must not precede activeFrom");
            }
        }
    }

    record LatestSubmission(
            GameType gameType,
            ShareOutcome outcome,
            Duration duration,
            LocalDate gameDate,
            Instant receivedAt) {
        public LatestSubmission {
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
