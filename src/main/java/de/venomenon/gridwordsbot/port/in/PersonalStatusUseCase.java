package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

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
            boolean known,
            TodayGameStatus gridWordsToday,
            TodayGameStatus quadWordsToday,
            PersonalStreaks streaks,
            ParticipationStatus gridWordsParticipation,
            ParticipationStatus quadWordsParticipation,
            boolean reminderOptIn,
            Optional<LatestSubmission> latestGridWordsSubmission,
            Optional<LatestSubmission> latestQuadWordsSubmission) {
        public PersonalStatus {
            Objects.requireNonNull(gridWordsToday, "gridWordsToday");
            Objects.requireNonNull(quadWordsToday, "quadWordsToday");
            Objects.requireNonNull(streaks, "streaks");
            Objects.requireNonNull(gridWordsParticipation, "gridWordsParticipation");
            Objects.requireNonNull(quadWordsParticipation, "quadWordsParticipation");
            if (gridWordsToday.gameType() != GameType.GRIDWORDS) {
                throw new IllegalArgumentException("gridWordsToday must be GridWords");
            }
            if (quadWordsToday.gameType() != GameType.QUADWORDS) {
                throw new IllegalArgumentException("quadWordsToday must be QuadWords");
            }
            latestGridWordsSubmission = requiredSubmission(
                    latestGridWordsSubmission, GameType.GRIDWORDS, "latestGridWordsSubmission");
            latestQuadWordsSubmission = requiredSubmission(
                    latestQuadWordsSubmission, GameType.QUADWORDS, "latestQuadWordsSubmission");
            if (!known && (gridWordsParticipation.active() || quadWordsParticipation.active()
                    || reminderOptIn || latestGridWordsSubmission.isPresent() || latestQuadWordsSubmission.isPresent())) {
                throw new IllegalArgumentException("unknown player status must not expose persisted state");
            }
        }

        public static PersonalStatus unknown() {
            return new PersonalStatus(
                    false,
                    TodayGameStatus.notParticipating(GameType.GRIDWORDS),
                    TodayGameStatus.notParticipating(GameType.QUADWORDS),
                    PersonalStreaks.none(),
                    ParticipationStatus.inactive(),
                    ParticipationStatus.inactive(),
                    false,
                    Optional.empty(),
                    Optional.empty());
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

    record TodayGameStatus(
            GameType gameType,
            boolean participating,
            Optional<ShareOutcome> outcome,
            Optional<Duration> duration) {
        public TodayGameStatus {
            Objects.requireNonNull(gameType, "gameType");
            outcome = Objects.requireNonNull(outcome, "outcome");
            duration = Objects.requireNonNull(duration, "duration");
            if (!participating && (outcome.isPresent() || duration.isPresent())) {
                throw new IllegalArgumentException("non-participating game cannot expose a result");
            }
            if (outcome.isPresent() != duration.isPresent()) {
                throw new IllegalArgumentException("outcome and duration must be present together");
            }
            duration.ifPresent(value -> {
                if (value.isNegative()) throw new IllegalArgumentException("duration must not be negative");
            });
        }

        public static TodayGameStatus notParticipating(GameType gameType) {
            return new TodayGameStatus(gameType, false, Optional.empty(), Optional.empty());
        }

        public static TodayGameStatus open(GameType gameType) {
            return new TodayGameStatus(gameType, true, Optional.empty(), Optional.empty());
        }

        public static TodayGameStatus submitted(GameType gameType, ShareOutcome outcome, Duration duration) {
            return new TodayGameStatus(gameType, true, Optional.of(outcome), Optional.of(duration));
        }
    }

    record PersonalStreaks(
            OptionalInt activity,
            OptionalInt complete,
            OptionalInt gridWordsSolved,
            OptionalInt quadWordsSolved,
            OptionalInt perfect) {
        public PersonalStreaks {
            requireNonNegative(activity, "activity");
            requireNonNegative(complete, "complete");
            requireNonNegative(gridWordsSolved, "gridWordsSolved");
            requireNonNegative(quadWordsSolved, "quadWordsSolved");
            requireNonNegative(perfect, "perfect");
        }

        public static PersonalStreaks none() {
            return new PersonalStreaks(
                    OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty());
        }

        private static void requireNonNegative(OptionalInt value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isPresent() && value.getAsInt() < 0) {
                throw new IllegalArgumentException(name + " must not be negative");
            }
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

        public static ParticipationStatus inactive() {
            return new ParticipationStatus(false, Optional.empty(), Optional.empty());
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
