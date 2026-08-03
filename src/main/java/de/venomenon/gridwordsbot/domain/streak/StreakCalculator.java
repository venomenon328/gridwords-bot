package de.venomenon.gridwordsbot.domain.streak;

import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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
        Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> index = index(results);
        Map<LocalDate, Map<GameType, PlayerResult>> ownDays = index.getOrDefault(playerId, Map.of());
        return new StreakSummary(
                current(asOfDate, provisionalCurrentDay,
                        day -> activity(ownDays, participation(participationPeriods, day), playerId, day)),
                current(asOfDate, provisionalCurrentDay,
                        day -> complete(ownDays, participation(participationPeriods, day), playerId, day)),
                current(asOfDate, provisionalCurrentDay,
                        day -> solved(ownDays, participation(participationPeriods, day), playerId, day,
                                GameType.GRIDWORDS)),
                current(asOfDate, provisionalCurrentDay,
                        day -> solved(ownDays, participation(participationPeriods, day), playerId, day,
                                GameType.QUADWORDS)),
                current(asOfDate, provisionalCurrentDay,
                        day -> perfect(ownDays, participation(participationPeriods, day), playerId, day)),
                current(asOfDate, provisionalCurrentDay,
                        day -> sharedSolved(index, participationPeriods, day, GameType.GRIDWORDS)),
                current(asOfDate, provisionalCurrentDay,
                        day -> sharedSolved(index, participationPeriods, day, GameType.QUADWORDS)),
                current(asOfDate, provisionalCurrentDay, day -> sharedComplete(index, participationPeriods, day)),
                current(asOfDate, provisionalCurrentDay, day -> sharedPerfect(index, participationPeriods, day)));
    }

    private int current(LocalDate asOfDate, boolean provisionalCurrentDay, Function<LocalDate, DayState> condition) {
        return calendarStreaks.current(asOfDate, provisionalCurrentDay, day -> switch (condition.apply(day)) {
            case MET -> CalendarStreakCalculator.DayState.MET;
            case PENDING -> CalendarStreakCalculator.DayState.PENDING;
            case VIOLATED -> CalendarStreakCalculator.DayState.VIOLATED;
        });
    }

    private DayState activity(
            Map<LocalDate, Map<GameType, PlayerResult>> days,
            DailyGameParticipation participation,
            long playerId,
            LocalDate day) {
        if (!participation.participatingPlayers().contains(playerId)) {
            return DayState.VIOLATED;
        }
        boolean submittedParticipatingGame = days.getOrDefault(day, Map.of()).keySet().stream()
                .anyMatch(gameType -> participation.playersFor(gameType).contains(playerId));
        return submittedParticipatingGame ? DayState.MET : DayState.PENDING;
    }

    private DayState complete(
            Map<LocalDate, Map<GameType, PlayerResult>> days,
            DailyGameParticipation participation,
            long playerId,
            LocalDate day) {
        if (!participation.bothGamesPlayers().contains(playerId)) {
            return DayState.VIOLATED;
        }
        return days.getOrDefault(day, Map.of()).size() == GameType.values().length
                ? DayState.MET
                : DayState.PENDING;
    }

    private DayState solved(
            Map<LocalDate, Map<GameType, PlayerResult>> days,
            DailyGameParticipation participation,
            long playerId,
            LocalDate day,
            GameType type) {
        if (!participation.playersFor(type).contains(playerId)) {
            return DayState.VIOLATED;
        }
        PlayerResult result = days.getOrDefault(day, Map.of()).get(type);
        return result == null ? DayState.PENDING : result.solved() ? DayState.MET : DayState.VIOLATED;
    }

    private DayState perfect(
            Map<LocalDate, Map<GameType, PlayerResult>> days,
            DailyGameParticipation participation,
            long playerId,
            LocalDate day) {
        if (!participation.bothGamesPlayers().contains(playerId)) {
            return DayState.VIOLATED;
        }
        Map<GameType, PlayerResult> games = days.getOrDefault(day, Map.of());
        if (games.size() == GameType.values().length) {
            return games.values().stream().allMatch(PlayerResult::solved) ? DayState.MET : DayState.VIOLATED;
        }
        return games.values().stream().anyMatch(game -> !game.solved())
                ? DayState.VIOLATED
                : DayState.PENDING;
    }

    private DayState sharedSolved(
            Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> results,
            List<GameParticipationPeriod> periods,
            LocalDate day,
            GameType type) {
        List<Long> active = List.copyOf(participation(periods, day).playersFor(type));
        if (active.size() < 2) {
            return DayState.VIOLATED;
        }
        boolean violation = active.stream()
                .map(id -> results.getOrDefault(id, Map.of()).getOrDefault(day, Map.of()).get(type))
                .filter(Objects::nonNull)
                .anyMatch(result -> !result.solved());
        boolean allSolved = active.stream()
                .map(id -> results.getOrDefault(id, Map.of()).getOrDefault(day, Map.of()).get(type))
                .allMatch(result -> result != null && result.solved());
        return allSolved ? DayState.MET : violation ? DayState.VIOLATED : DayState.PENDING;
    }

    private DayState sharedComplete(
            Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> results,
            List<GameParticipationPeriod> periods,
            LocalDate day) {
        DailyGameParticipation participation = participation(periods, day);
        List<Long> active = List.copyOf(participation.bothGamesPlayers());
        if (active.size() < 2) {
            return DayState.VIOLATED;
        }
        return active.stream().allMatch(id -> complete(
                        results.getOrDefault(id, Map.of()), participation, id, day) == DayState.MET)
                ? DayState.MET
                : DayState.PENDING;
    }

    private DayState sharedPerfect(
            Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> results,
            List<GameParticipationPeriod> periods,
            LocalDate day) {
        DailyGameParticipation participation = participation(periods, day);
        List<Long> active = List.copyOf(participation.bothGamesPlayers());
        if (active.size() < 2) {
            return DayState.VIOLATED;
        }
        boolean violation = active.stream()
                .anyMatch(id -> perfect(
                        results.getOrDefault(id, Map.of()), participation, id, day) == DayState.VIOLATED);
        return active.stream().allMatch(id -> perfect(
                        results.getOrDefault(id, Map.of()), participation, id, day) == DayState.MET)
                ? DayState.MET
                : violation ? DayState.VIOLATED : DayState.PENDING;
    }

    private static DailyGameParticipation participation(
            List<GameParticipationPeriod> periods, LocalDate day) {
        return DailyGameParticipation.fromPeriods(day, periods);
    }

    private Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> index(List<PlayerResult> results) {
        Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> index = new HashMap<>();
        for (PlayerResult result : results) {
            index.computeIfAbsent(result.playerId(), ignored -> new HashMap<>())
                    .computeIfAbsent(result.result().gameDate(), ignored -> new HashMap<>())
                    .put(result.result().gameType(), result);
        }
        return index;
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
    }

    private enum DayState {
        MET,
        PENDING,
        VIOLATED
    }
}
