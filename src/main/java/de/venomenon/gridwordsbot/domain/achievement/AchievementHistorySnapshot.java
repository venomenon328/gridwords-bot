package de.venomenon.gridwordsbot.domain.achievement;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.streak.StreakGameResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Transportneutraler, deterministisch sortierter historischer Teilnehmer-Snapshot für die reine Achievement-Auswertung.
 */
public record AchievementHistorySnapshot(
        long participantId,
        List<Result> results,
        List<GameParticipationPeriod> participationPeriods) {

    private static final Comparator<Result> RESULT_ORDER = Comparator.comparing(Result::gameDate)
            .thenComparing(Result::game)
            .thenComparing(Result::receivedAt)
            .thenComparingLong(Result::resultId);

    private static final Comparator<GameParticipationPeriod> PERIOD_ORDER = Comparator
            .comparing(GameParticipationPeriod::gameType)
            .thenComparing(GameParticipationPeriod::activeFrom)
            .thenComparing(period -> Optional.ofNullable(period.inactiveFrom()).orElse(LocalDate.MAX));

    public AchievementHistorySnapshot {
        if (participantId <= 0) {
            throw new IllegalArgumentException("participantId must be positive");
        }
        results = Objects.requireNonNull(results, "results").stream()
                .map(result -> Objects.requireNonNull(result, "results must not contain null"))
                .sorted(RESULT_ORDER)
                .toList();
        participationPeriods = Objects.requireNonNull(participationPeriods, "participationPeriods").stream()
                .map(period -> Objects.requireNonNull(period, "participationPeriods must not contain null"))
                .sorted(PERIOD_ORDER)
                .toList();

        Set<Long> resultIds = new HashSet<>();
        Set<ResultIdentity> identities = new HashSet<>();
        for (Result result : results) {
            if (!resultIds.add(result.resultId())) {
                throw new IllegalArgumentException("duplicate game result ID: " + result.resultId());
            }
            if (!identities.add(new ResultIdentity(result.gameDate(), result.game()))) {
                throw new IllegalArgumentException("duplicate game result for date and game");
            }
        }
        if (participationPeriods.stream().anyMatch(period -> period.playerId() != participantId)) {
            throw new IllegalArgumentException("participation period belongs to another participant");
        }
    }

    public List<Result> resultsFor(GameType game) {
        Objects.requireNonNull(game, "game");
        return results.stream().filter(result -> result.game() == game).toList();
    }

    public List<StreakGameResult> streakResults() {
        return results.stream()
                .map(result -> new StreakGameResult(participantId, result.gameDate(), result.game(), result.solved()))
                .toList();
    }

    public record Result(
            long resultId,
            GameType game,
            LocalDate gameDate,
            boolean solved,
            OptionalInt attempts,
            Instant receivedAt,
            Optional<QuadWordsBoards> quadWordsBoards) {

        public Result {
            if (resultId <= 0) {
                throw new IllegalArgumentException("resultId must be positive");
            }
            Objects.requireNonNull(game, "game");
            Objects.requireNonNull(gameDate, "gameDate");
            attempts = Objects.requireNonNull(attempts, "attempts");
            Objects.requireNonNull(receivedAt, "receivedAt");
            quadWordsBoards = Objects.requireNonNull(quadWordsBoards, "quadWordsBoards");

            if (solved != attempts.isPresent()) {
                throw new IllegalArgumentException("solved results require attempts and failed results must not expose attempts");
            }
            if (attempts.isPresent()) {
                int minimum = game == GameType.GRIDWORDS ? 1 : 4;
                int maximum = game == GameType.GRIDWORDS ? 6 : 9;
                if (attempts.getAsInt() < minimum || attempts.getAsInt() > maximum) {
                    throw new IllegalArgumentException("attempts outside the solved range for " + game);
                }
            }
            if (game != GameType.QUADWORDS && quadWordsBoards.isPresent()) {
                throw new IllegalArgumentException("board details are only valid for QuadWords");
            }
        }
    }

    private record ResultIdentity(LocalDate gameDate, GameType game) {
    }
}
