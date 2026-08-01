package de.venomenon.gridwordsbot.domain.streak;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.LocalDate;
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
        if (playerIds.isEmpty() || playerIds.stream().distinct().count() != playerIds.size() || !playerIds.contains(playerId)) {
            throw new IllegalArgumentException("player IDs must contain the queried player exactly once");
        }
        return calculateWithParticipation(results, playerIds.stream().map(id -> new ParticipationPeriod(id, LocalDate.MIN, null)).toList(), playerId, today);
    }

    public StreakSummary calculateWithParticipation(
            List<PlayerResult> results, List<ParticipationPeriod> participationPeriods, long playerId, LocalDate today) {
        return calculateWithParticipation(results, participationPeriods, playerId, today, true);
    }

    public StreakSummary calculateWithParticipation(
            List<PlayerResult> results, List<ParticipationPeriod> participationPeriods, long playerId,
            LocalDate asOfDate, boolean provisionalCurrentDay) {
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(participationPeriods, "participationPeriods");
        Objects.requireNonNull(asOfDate, "asOfDate");
        Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> index = index(results);
        Map<LocalDate, Map<GameType, PlayerResult>> ownDays = index.getOrDefault(playerId, Map.of());
        return new StreakSummary(
                current(asOfDate, provisionalCurrentDay, day -> activity(ownDays, day)),
                current(asOfDate, provisionalCurrentDay, day -> complete(ownDays, day)),
                current(asOfDate, provisionalCurrentDay, day -> solved(ownDays, day, GameType.GRIDWORDS)),
                current(asOfDate, provisionalCurrentDay, day -> solved(ownDays, day, GameType.QUADWORDS)),
                current(asOfDate, provisionalCurrentDay, day -> perfect(ownDays, day)),
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

    private DayState activity(Map<LocalDate, Map<GameType, PlayerResult>> days, LocalDate day) {
        return days.containsKey(day) ? DayState.MET : DayState.PENDING;
    }
    private DayState complete(Map<LocalDate, Map<GameType, PlayerResult>> days, LocalDate day) {
        return days.getOrDefault(day, Map.of()).size() == GameType.values().length ? DayState.MET : DayState.PENDING;
    }
    private DayState solved(Map<LocalDate, Map<GameType, PlayerResult>> days, LocalDate day, GameType type) {
        PlayerResult result = days.getOrDefault(day, Map.of()).get(type);
        return result == null ? DayState.PENDING : result.solved() ? DayState.MET : DayState.VIOLATED;
    }
    private DayState perfect(Map<LocalDate, Map<GameType, PlayerResult>> days, LocalDate day) {
        Map<GameType, PlayerResult> games = days.getOrDefault(day, Map.of());
        if (games.size() == GameType.values().length) return games.values().stream().allMatch(PlayerResult::solved) ? DayState.MET : DayState.VIOLATED;
        return games.values().stream().anyMatch(game -> !game.solved()) ? DayState.VIOLATED : DayState.PENDING;
    }
    private DayState sharedComplete(Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> results, List<ParticipationPeriod> periods, LocalDate day) {
        List<Long> active = activePlayerIds(periods, day);
        if (active.size() < 2) return DayState.VIOLATED;
        return active.stream().allMatch(id -> complete(results.getOrDefault(id, Map.of()), day) == DayState.MET) ? DayState.MET : DayState.PENDING;
    }
    private DayState sharedPerfect(Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> results, List<ParticipationPeriod> periods, LocalDate day) {
        List<Long> active = activePlayerIds(periods, day);
        if (active.size() < 2) return DayState.VIOLATED;
        boolean violation = active.stream().anyMatch(id -> perfect(results.getOrDefault(id, Map.of()), day) == DayState.VIOLATED);
        return active.stream().allMatch(id -> perfect(results.getOrDefault(id, Map.of()), day) == DayState.MET) ? DayState.MET : violation ? DayState.VIOLATED : DayState.PENDING;
    }
    private static List<Long> activePlayerIds(List<ParticipationPeriod> periods, LocalDate day) {
        return periods.stream().filter(period -> period.contains(day)).map(ParticipationPeriod::playerId).distinct().toList();
    }
    private Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> index(List<PlayerResult> results) {
        Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> index = new HashMap<>();
        for (PlayerResult result : results) index.computeIfAbsent(result.playerId(), ignored -> new HashMap<>()).computeIfAbsent(result.result().gameDate(), ignored -> new HashMap<>()).put(result.result().gameType(), result);
        return index;
    }
    public record PlayerResult(long playerId, ParsedGameResult result) {
        public PlayerResult { if (playerId <= 0) throw new IllegalArgumentException("player ID must be positive"); Objects.requireNonNull(result); }
        boolean solved() { return result.outcome() instanceof ShareOutcome.Solved; }
    }
    private enum DayState { MET, PENDING, VIOLATED }
}