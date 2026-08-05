package de.venomenon.gridwordsbot.domain.streak;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Pure calendar-day calculation of all accepted, explicitly named current streaks. */
public final class StreakCalculator {
    private final CalendarStreakCalculator calendarStreaks = new CalendarStreakCalculator();

    /** Compatibility overload for callers whose participants are active for all dates. */
    public StreakSummary calculate(List<PlayerResult> results, List<Long> playerIds, long playerId, LocalDate today) {
        Objects.requireNonNull(playerIds, "playerIds");
        if (playerIds.isEmpty() || playerIds.stream().distinct().count() != playerIds.size()
                || !playerIds.contains(playerId)) {
            throw new IllegalArgumentException("player IDs must contain the queried player exactly once");
        }
        return calculateWithGameParticipation(results,
                playerIds.stream().flatMap(id -> Arrays.stream(GameType.values())
                        .map(gameType -> new GameParticipationPeriod(id, gameType, LocalDate.MIN, null)))
                        .toList(),
                playerId, today);
    }

    /** Temporary global compatibility API for report paths that still expose one participation history. */
    public StreakSummary calculateWithParticipation(
            List<PlayerResult> results, List<ParticipationPeriod> participationPeriods, long playerId,
            LocalDate today) {
        return calculateWithParticipation(results, participationPeriods, playerId, today, true);
    }

    public StreakSummary calculateWithParticipation(
            List<PlayerResult> results, List<ParticipationPeriod> participationPeriods, long playerId,
            LocalDate asOfDate, boolean provisionalCurrentDay) {
        Objects.requireNonNull(participationPeriods, "participationPeriods");
        return calculateWithGameParticipation(results,
                participationPeriods.stream().flatMap(period -> Arrays.stream(GameType.values())
                        .map(gameType -> new GameParticipationPeriod(
                                period.playerId(), gameType, period.activeFrom(), period.inactiveFrom())))
                        .toList(),
                playerId, asOfDate, provisionalCurrentDay);
    }

    public StreakSummary calculateWithGameParticipation(
            List<PlayerResult> results, List<GameParticipationPeriod> participationPeriods, long playerId,
            LocalDate today) {
        return calculateWithGameParticipation(results, participationPeriods, playerId, today, true);
    }

    public StreakSummary calculateWithGameParticipation(
            List<PlayerResult> results, List<GameParticipationPeriod> participationPeriods, long playerId,
            LocalDate asOfDate, boolean provisionalCurrentDay) {
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(participationPeriods, "participationPeriods");
        Objects.requireNonNull(asOfDate, "asOfDate");
        StreakDayClassifier classifier = new StreakDayClassifier(results.stream()
                .map(PlayerResult::asStreakGameResult).toList(), participationPeriods);
        return new StreakSummary(
                current(asOfDate, provisionalCurrentDay, classifier::personalActivity, playerId),
                current(asOfDate, provisionalCurrentDay, classifier::personalComplete, playerId),
                currentSolved(asOfDate, provisionalCurrentDay, classifier, playerId, GameType.GRIDWORDS),
                currentSolved(asOfDate, provisionalCurrentDay, classifier, playerId, GameType.QUADWORDS),
                current(asOfDate, provisionalCurrentDay, classifier::personalPerfect, playerId),
                currentSharedSolved(asOfDate, provisionalCurrentDay, classifier, GameType.GRIDWORDS),
                currentSharedSolved(asOfDate, provisionalCurrentDay, classifier, GameType.QUADWORDS),
                currentShared(asOfDate, provisionalCurrentDay, classifier::sharedComplete),
                currentShared(asOfDate, provisionalCurrentDay, classifier::sharedPerfect));
    }

    private int current(LocalDate asOfDate, boolean provisionalCurrentDay,
            PersonalCondition condition, long playerId) {
        return calculateCurrent(asOfDate, provisionalCurrentDay,
                (day, closed) -> condition.assess(playerId, day, closed));
    }

    private int currentSolved(LocalDate asOfDate, boolean provisionalCurrentDay,
            StreakDayClassifier classifier, long playerId, GameType game) {
        return calculateCurrent(asOfDate, provisionalCurrentDay,
                (day, closed) -> classifier.personalSolved(playerId, day, game, closed));
    }

    private int currentSharedSolved(LocalDate asOfDate, boolean provisionalCurrentDay,
            StreakDayClassifier classifier, GameType game) {
        return calculateCurrent(asOfDate, provisionalCurrentDay,
                (day, closed) -> classifier.sharedSolved(day, game, closed));
    }

    private int currentShared(LocalDate asOfDate, boolean provisionalCurrentDay, SharedCondition condition) {
        return calculateCurrent(asOfDate, provisionalCurrentDay, condition::assess);
    }

    private int calculateCurrent(LocalDate asOfDate, boolean provisionalCurrentDay,
            BiFunction<LocalDate, Boolean, StreakDayAssessment> condition) {
        return calendarStreaks.current(asOfDate, provisionalCurrentDay, day -> {
            boolean dayClosed = !day.equals(asOfDate) || !provisionalCurrentDay;
            return switch (condition.apply(day, dayClosed).state()) {
                case MET -> CalendarStreakCalculator.DayState.MET;
                case PENDING -> CalendarStreakCalculator.DayState.PENDING;
                case VIOLATED -> CalendarStreakCalculator.DayState.VIOLATED;
            };
        });
    }

    public record PlayerResult(long playerId, ParsedGameResult result) {
        public PlayerResult {
            if (playerId <= 0) {
                throw new IllegalArgumentException("player ID must be positive");
            }
            Objects.requireNonNull(result);
        }

        boolean solved() {
            return result.outcome() instanceof ShareOutcome.Solved;
        }

        StreakGameResult asStreakGameResult() {
            return new StreakGameResult(playerId, result.gameDate(), result.gameType(), solved());
        }
    }

    @FunctionalInterface
    private interface PersonalCondition {
        StreakDayAssessment assess(long playerId, LocalDate day, boolean dayClosed);
    }

    @FunctionalInterface
    private interface SharedCondition {
        StreakDayAssessment assess(LocalDate day, boolean dayClosed);
    }
}
