package de.venomenon.gridwordsbot.domain.model;

import java.util.List;

/** Explicit command-boundary selection of the supported GridGames participation modes. */
public enum GameParticipationSelection {
    GRIDWORDS(List.of(GameType.GRIDWORDS)),
    QUADWORDS(List.of(GameType.QUADWORDS)),
    BOTH(List.of(GameType.GRIDWORDS, GameType.QUADWORDS));

    private final List<GameType> gameTypes;

    GameParticipationSelection(List<GameType> gameTypes) {
        this.gameTypes = List.copyOf(gameTypes);
    }

    /** Returns every selected game exactly once in the stable GridWords, QuadWords order. */
    public List<GameType> gameTypes() {
        return gameTypes;
    }

    /** Maps one validated result type to its corresponding single-game participation selection. */
    public static GameParticipationSelection forGameType(GameType gameType) {
        return switch (java.util.Objects.requireNonNull(gameType, "gameType")) {
            case GRIDWORDS -> GRIDWORDS;
            case QUADWORDS -> QUADWORDS;
        };
    }
}
