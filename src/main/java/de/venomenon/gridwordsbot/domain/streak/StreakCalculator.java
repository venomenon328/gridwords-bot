package de.venomenon.gridwordsbot.domain.streak;

import de.venomenon.gridwordsbot.domain.model.GameType;
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

    public StreakSummary calculate(List<PlayerResult> results, List<Long> playerIds, long playerId, LocalDate today) {
        Objects.requireNonNull(results);
        Objects.requireNonNull(playerIds);
        Objects.requireNonNull(today);
        if (playerIds.size() != 2 || playerIds.stream().distinct().count() != 2 || !playerIds.contains(playerId)) {
            throw new IllegalArgumentException("two configured players are required");
        }

        Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> index = index(results);
        Map<LocalDate, Map<GameType, PlayerResult>> ownDays = index.getOrDefault(playerId, Map.of());
        return new StreakSummary(
                current(today, day -> activity(ownDays, day)),
                current(today, day -> complete(ownDays, day)),
                current(today, day -> solved(ownDays, day, GameType.GRIDWORDS)),
                current(today, day -> solved(ownDays, day, GameType.QUADWORDS)),
                current(today, day -> perfect(ownDays, day)),
                current(today, day -> sharedComplete(index, playerIds, day)),
                current(today, day -> sharedPerfect(index, playerIds, day)));
    }

    private int current(LocalDate today, Function<LocalDate, DayState> condition) {
        int count = 0;
        for (LocalDate day = today; ; day = day.minusDays(1)) {
            DayState state = condition.apply(day);
            if (state == DayState.MET) {
                count++;
                continue;
            }
            if (day.equals(today) && state == DayState.PENDING) {
                continue;
            }
            return count;
        }
    }

    private DayState activity(Map<LocalDate, Map<GameType, PlayerResult>> days, LocalDate day) {
        return days.containsKey(day) ? DayState.MET : DayState.PENDING;
    }

    private DayState complete(Map<LocalDate, Map<GameType, PlayerResult>> days, LocalDate day) {
        return days.getOrDefault(day, Map.of()).size() == 2 ? DayState.MET : DayState.PENDING;
    }

    private DayState solved(
            Map<LocalDate, Map<GameType, PlayerResult>> days,
            LocalDate day,
            GameType type) {
        PlayerResult result = days.getOrDefault(day, Map.of()).get(type);
        if (result == null) {
            return DayState.PENDING;
        }
        return result.solved() ? DayState.MET : DayState.VIOLATED;
    }

    private DayState perfect(Map<LocalDate, Map<GameType, PlayerResult>> days, LocalDate day) {
        Map<GameType, PlayerResult> games = days.getOrDefault(day, Map.of());
        if (games.size() == 2) {
            return games.values().stream().allMatch(PlayerResult::solved) ? DayState.MET : DayState.VIOLATED;
        }
        return games.values().stream().anyMatch(game -> !game.solved()) ? DayState.VIOLATED : DayState.PENDING;
    }

    private DayState sharedComplete(
            Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> results,
            List<Long> playerIds,
            LocalDate day) {
        return playerIds.stream().allMatch(id -> complete(results.getOrDefault(id, Map.of()), day) == DayState.MET)
                ? DayState.MET
                : DayState.PENDING;
    }

    private DayState sharedPerfect(
            Map<Long, Map<LocalDate, Map<GameType, PlayerResult>>> results,
            List<Long> playerIds,
            LocalDate day) {
        boolean violation = playerIds.stream()
                .anyMatch(id -> perfect(results.getOrDefault(id, Map.of()), day) == DayState.VIOLATED);
        return playerIds.stream().allMatch(id -> perfect(results.getOrDefault(id, Map.of()), day) == DayState.MET)
                ? DayState.MET
                : violation ? DayState.VIOLATED : DayState.PENDING;
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