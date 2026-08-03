package de.venomenon.gridwordsbot.domain.excuse;

import java.util.Arrays;
import java.util.Optional;

/** Explicitly supported placeholders in editorial excuse texts. */
public enum ExcusePlaceholder {
    GAME("game"),
    SCORE("score"),
    DURATION("duration"),
    WORST_BOARD("worstBoard");

    private final String token;

    ExcusePlaceholder(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public String marker() {
        return "{" + token + "}";
    }

    public static Optional<ExcusePlaceholder> fromToken(String token) {
        return Arrays.stream(values()).filter(value -> value.token.equals(token)).findFirst();
    }
}
