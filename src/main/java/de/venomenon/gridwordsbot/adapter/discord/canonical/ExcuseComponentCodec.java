package de.venomenon.gridwordsbot.adapter.discord.canonical;

import java.util.Optional;

/** Strict v1 codec for canonical excuse buttons. IDs deliberately carry no editorial text. */
public final class ExcuseComponentCodec {

    private static final String PREFIX = "excuse";
    private static final String VERSION = "v1";
    private static final String OPEN = "open";

    public String encodeOpen(long gameResultId) {
        if (gameResultId <= 0) {
            throw new IllegalArgumentException("gameResultId must be positive");
        }
        return PREFIX + ":" + VERSION + ":" + OPEN + ":" + gameResultId;
    }

    public Optional<Open> decodeOpen(String componentId) {
        if (componentId == null) {
            return Optional.empty();
        }
        String[] parts = componentId.split(":", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0]) || !VERSION.equals(parts[1]) || !OPEN.equals(parts[2])) {
            return Optional.empty();
        }
        try {
            long gameResultId = Long.parseLong(parts[3]);
            return gameResultId > 0 ? Optional.of(new Open(gameResultId)) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public record Open(long gameResultId) {
        public Open {
            if (gameResultId <= 0) {
                throw new IllegalArgumentException("gameResultId must be positive");
            }
        }
    }
}
