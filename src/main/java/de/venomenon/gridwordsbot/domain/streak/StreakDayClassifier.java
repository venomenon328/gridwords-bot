package de.venomenon.gridwordsbot.domain.streak;

import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared source of truth for all positive and record-only negative streak day conditions. */
public final class StreakDayClassifier {
    private final List<GameParticipationPeriod> participationPeriods;
    private final Map<Long, Map<LocalDate, Map<GameType, StreakGameResult>>> results;

    public StreakDayClassifier(List<StreakGameResult> results, List<GameParticipationPeriod> participationPeriods) {
        this.participationPeriods = List.copyOf(Objects.requireNonNull(participationPeriods, "participationPeriods"));
        if (this.participationPeriods.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("participationPeriods must not contain null");
        }
        this.results = index(results);
    }

    public StreakDayAssessment personalActivity(long playerId, LocalDate day, boolean dayClosed) {
        DailyGameParticipation participation = participation(day);
        if (!participation.participatingPlayers().contains(playerId)) return participationBoundary();
        boolean submitted = games(playerId, day).keySet().stream()
                .anyMatch(game -> participation.playersFor(game).contains(playerId));
        return submitted ? StreakDayAssessment.met() : missing(dayClosed);
    }

    /** Canonical personal participation condition for one concrete game. */
    public StreakDayAssessment personalParticipation(long playerId, LocalDate day, GameType game, boolean dayClosed) {
        Objects.requireNonNull(game, "game");
        DailyGameParticipation participation = participation(day);
        if (!participation.playersFor(game).contains(playerId)) return participationBoundary();
        return games(playerId, day).containsKey(game) ? StreakDayAssessment.met() : missing(dayClosed);
    }

    public StreakDayAssessment personalComplete(long playerId, LocalDate day, boolean dayClosed) {
        DailyGameParticipation participation = participation(day);
        if (!participation.bothGamesPlayers().contains(playerId)) return participationBoundary();
        Map<GameType, StreakGameResult> games = games(playerId, day);
        boolean complete = games.containsKey(GameType.GRIDWORDS) && games.containsKey(GameType.QUADWORDS);
        return complete ? StreakDayAssessment.met() : missing(dayClosed);
    }

    public StreakDayAssessment personalSolved(long playerId, LocalDate day, GameType game, boolean dayClosed) {
        Objects.requireNonNull(game, "game");
        DailyGameParticipation participation = participation(day);
        if (!participation.playersFor(game).contains(playerId)) return participationBoundary();
        StreakGameResult result = games(playerId, day).get(game);
        if (result == null) return missing(dayClosed);
        return result.solved() ? StreakDayAssessment.met() : resultBoundary();
    }

    public StreakDayAssessment personalPerfect(long playerId, LocalDate day, boolean dayClosed) {
        DailyGameParticipation participation = participation(day);
        if (!participation.bothGamesPlayers().contains(playerId)) return participationBoundary();
        Map<GameType, StreakGameResult> games = games(playerId, day);
        if (games.values().stream().anyMatch(result -> !result.solved())) return resultBoundary();
        boolean complete = games.containsKey(GameType.GRIDWORDS) && games.containsKey(GameType.QUADWORDS);
        return complete ? StreakDayAssessment.met() : missing(dayClosed);
    }

    public StreakDayAssessment sharedSolved(LocalDate day, GameType game, boolean dayClosed) {
        Objects.requireNonNull(game, "game");
        List<Long> active = List.copyOf(participation(day).playersFor(game));
        if (active.isEmpty()) return participationBoundary();
        boolean anySolved = active.stream().map(id -> games(id, day).get(game))
                .anyMatch(result -> result != null && result.solved());
        if (anySolved) return StreakDayAssessment.met();
        boolean allUnsolved = active.stream().map(id -> games(id, day).get(game))
                .allMatch(result -> result != null && !result.solved());
        return allUnsolved ? resultBoundary() : missing(dayClosed);
    }

    public StreakDayAssessment sharedComplete(LocalDate day, boolean dayClosed) {
        DailyGameParticipation participation = participation(day);
        List<Long> active = List.copyOf(participation.bothGamesPlayers());
        if (active.isEmpty()) return participationBoundary();
        boolean anyComplete = active.stream().anyMatch(id -> {
            Map<GameType, StreakGameResult> games = games(id, day);
            return games.containsKey(GameType.GRIDWORDS) && games.containsKey(GameType.QUADWORDS);
        });
        return anyComplete ? StreakDayAssessment.met() : missing(dayClosed);
    }

    public StreakDayAssessment sharedPerfect(LocalDate day, boolean dayClosed) {
        DailyGameParticipation participation = participation(day);
        List<Long> active = List.copyOf(participation.bothGamesPlayers());
        if (active.isEmpty()) return participationBoundary();
        boolean anyPerfect = active.stream().anyMatch(id -> {
            Map<GameType, StreakGameResult> games = games(id, day);
            return games.containsKey(GameType.GRIDWORDS) && games.get(GameType.GRIDWORDS).solved()
                    && games.containsKey(GameType.QUADWORDS) && games.get(GameType.QUADWORDS).solved();
        });
        if (anyPerfect) return StreakDayAssessment.met();
        boolean allBlockedByUnsolvedResult = active.stream().allMatch(id -> java.util.Arrays.stream(GameType.values())
                .map(game -> games(id, day).get(game))
                .anyMatch(result -> result != null && !result.solved()));
        return allBlockedByUnsolvedResult ? resultBoundary() : missing(dayClosed);
    }

    public StreakDayAssessment personalDrought(long playerId, LocalDate day, GameType game, boolean dayClosed) {
        Objects.requireNonNull(game, "game");
        if (!participation(day).playersFor(game).contains(playerId)) return participationBoundary();
        StreakGameResult result = games(playerId, day).get(game);
        if (result == null) return missing(dayClosed);
        return result.solved() ? resultBoundary() : StreakDayAssessment.met();
    }

    public StreakDayAssessment personalWithoutPerfectDay(long playerId, LocalDate day, boolean dayClosed) {
        if (!participation(day).bothGamesPlayers().contains(playerId)) return participationBoundary();
        Map<GameType, StreakGameResult> games = games(playerId, day);
        if (games.values().stream().anyMatch(result -> !result.solved())) return StreakDayAssessment.met();
        boolean complete = games.containsKey(GameType.GRIDWORDS) && games.containsKey(GameType.QUADWORDS);
        if (complete) return resultBoundary();
        return dayClosed ? StreakDayAssessment.met() : StreakDayAssessment.pending();
    }

    private DailyGameParticipation participation(LocalDate day) {
        return DailyGameParticipation.fromPeriods(Objects.requireNonNull(day, "day"), participationPeriods);
    }

    private Map<GameType, StreakGameResult> games(long playerId, LocalDate day) {
        if (playerId <= 0) throw new IllegalArgumentException("playerId must be positive");
        Objects.requireNonNull(day, "day");
        return results.getOrDefault(playerId, Map.of()).getOrDefault(day, Map.of());
    }

    private static StreakDayAssessment missing(boolean dayClosed) {
        return dayClosed ? StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.DAY_CLOSE)
                : StreakDayAssessment.pending();
    }

    private static StreakDayAssessment participationBoundary() {
        return StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.PARTICIPATION);
    }

    private static StreakDayAssessment resultBoundary() {
        return StreakDayAssessment.violated(StreakDayAssessment.BoundaryReason.RESULT);
    }

    private static Map<Long, Map<LocalDate, Map<GameType, StreakGameResult>>> index(
            List<StreakGameResult> source) {
        Map<Long, Map<LocalDate, Map<GameType, StreakGameResult>>> index = new HashMap<>();
        for (StreakGameResult result : List.copyOf(Objects.requireNonNull(source, "results"))) {
            Objects.requireNonNull(result, "results must not contain null");
            Map<GameType, StreakGameResult> day = index.computeIfAbsent(result.playerId(), ignored -> new HashMap<>())
                    .computeIfAbsent(result.gameDate(), ignored -> new HashMap<>());
            if (day.putIfAbsent(result.game(), result) != null) {
                throw new IllegalArgumentException("duplicate game result for player, date and game");
            }
        }
        return index;
    }
}
