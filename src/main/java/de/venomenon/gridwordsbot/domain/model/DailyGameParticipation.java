package de.venomenon.gridwordsbot.domain.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Closed projection of the GridWords, QuadWords, union and two-game player sets for one day. */
public record DailyGameParticipation(
        LocalDate gameDate,
        Set<Long> gridWordsPlayers,
        Set<Long> quadWordsPlayers,
        Set<Long> participatingPlayers,
        Set<Long> bothGamesPlayers) {

    public DailyGameParticipation {
        Objects.requireNonNull(gameDate, "gameDate");
        gridWordsPlayers = checkedPlayers(gridWordsPlayers, "gridWordsPlayers");
        quadWordsPlayers = checkedPlayers(quadWordsPlayers, "quadWordsPlayers");
        participatingPlayers = checkedPlayers(participatingPlayers, "participatingPlayers");
        bothGamesPlayers = checkedPlayers(bothGamesPlayers, "bothGamesPlayers");

        Set<Long> expectedUnion = union(gridWordsPlayers, quadWordsPlayers);
        if (!participatingPlayers.equals(expectedUnion)) {
            throw new IllegalArgumentException("participatingPlayers must be the union of both game sets");
        }
        Set<Long> expectedIntersection = intersection(gridWordsPlayers, quadWordsPlayers);
        if (!bothGamesPlayers.equals(expectedIntersection)) {
            throw new IllegalArgumentException("bothGamesPlayers must be the intersection of both game sets");
        }
    }

    public static DailyGameParticipation fromPeriods(LocalDate gameDate, Collection<GameParticipationPeriod> periods) {
        Objects.requireNonNull(gameDate, "gameDate");
        Objects.requireNonNull(periods, "periods");
        Set<Long> gridWords = new LinkedHashSet<>();
        Set<Long> quadWords = new LinkedHashSet<>();
        for (GameParticipationPeriod period : periods) {
            Objects.requireNonNull(period, "periods must not contain null");
            if (!period.contains(gameDate)) continue;
            switch (period.gameType()) {
                case GRIDWORDS -> gridWords.add(period.playerId());
                case QUADWORDS -> quadWords.add(period.playerId());
            }
        }
        Set<Long> union = union(gridWords, quadWords);
        return new DailyGameParticipation(gameDate, gridWords, quadWords, union, intersection(gridWords, quadWords));
    }

    public Set<Long> playersFor(GameType gameType) {
        Objects.requireNonNull(gameType, "gameType");
        return switch (gameType) {
            case GRIDWORDS -> gridWordsPlayers;
            case QUADWORDS -> quadWordsPlayers;
        };
    }

    private static Set<Long> checkedPlayers(Set<Long> players, String name) {
        Objects.requireNonNull(players, name);
        for (Long playerId : players) {
            if (playerId == null || playerId <= 0) throw new IllegalArgumentException(name + " must contain positive player ids");
        }
        return Set.copyOf(players);
    }

    private static Set<Long> union(Set<Long> left, Set<Long> right) {
        Set<Long> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return Set.copyOf(result);
    }

    private static Set<Long> intersection(Set<Long> left, Set<Long> right) {
        Set<Long> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return Set.copyOf(result);
    }
}
