package de.venomenon.gridwordsbot.domain.achievement;

import de.venomenon.gridwordsbot.domain.model.GameType;

/** Fachlicher Spielbezug einer Achievement-Definition. */
public enum AchievementScope {
    GRIDWORDS,
    QUADWORDS,
    CROSS_GAME,
    GLOBAL;

    public static AchievementScope forGame(GameType game) {
        return switch (game) {
            case GRIDWORDS -> GRIDWORDS;
            case QUADWORDS -> QUADWORDS;
        };
    }
}
