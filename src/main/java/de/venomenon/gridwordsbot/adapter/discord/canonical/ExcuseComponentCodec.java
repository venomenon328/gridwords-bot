package de.venomenon.gridwordsbot.adapter.discord.canonical;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import java.util.Optional;

/** Strict v1 codec for canonical excuse buttons. IDs deliberately carry no editorial text. */
public final class ExcuseComponentCodec {

    private static final String PREFIX = "excuse";
    private static final String VERSION = "v1";
    private static final String OPEN = "open";
    private static final String PICK = "pick";
    private static final String REROLL = "reroll";
    private static final String STYLE = "style";
    private static final String DECLINE = "decline";

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

    public String encodePick(long gameResultId, int contextGeneration, ExcuseRound round, int position) {
        requirePositive(gameResultId, "gameResultId");
        requirePositive(contextGeneration, "contextGeneration");
        if (position < 1 || position > 3) {
            throw new IllegalArgumentException("position must be between 1 and 3");
        }
        return PREFIX + ":" + VERSION + ":" + PICK + ":" + gameResultId + ":" + contextGeneration + ":"
                + java.util.Objects.requireNonNull(round, "round").name() + ":" + position;
    }

    public Optional<Pick> decodePick(String componentId) {
        String[] parts = parts(componentId, 7, PICK);
        if (parts == null) {
            return Optional.empty();
        }
        try {
            long gameResultId = Long.parseLong(parts[3]);
            int contextGeneration = Integer.parseInt(parts[4]);
            ExcuseRound round = ExcuseRound.valueOf(parts[5]);
            int position = Integer.parseInt(parts[6]);
            return gameResultId > 0 && contextGeneration > 0 && position >= 1 && position <= 3
                    ? Optional.of(new Pick(gameResultId, contextGeneration, round, position)) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public String encodeReroll(long gameResultId, int contextGeneration) {
        return encodeGenerationAction(REROLL, gameResultId, contextGeneration);
    }

    public Optional<Reroll> decodeReroll(String componentId) {
        return decodeGenerationAction(componentId, REROLL).map(value -> new Reroll(value.gameResultId(), value.contextGeneration()));
    }

    public String encodeStyle(long gameResultId, int contextGeneration) {
        return encodeGenerationAction(STYLE, gameResultId, contextGeneration);
    }

    public Optional<Style> decodeStyle(String componentId) {
        return decodeGenerationAction(componentId, STYLE).map(value -> new Style(value.gameResultId(), value.contextGeneration()));
    }

    public String encodeDecline(long gameResultId, int contextGeneration) {
        return encodeGenerationAction(DECLINE, gameResultId, contextGeneration);
    }

    public Optional<Decline> decodeDecline(String componentId) {
        return decodeGenerationAction(componentId, DECLINE)
                .map(value -> new Decline(value.gameResultId(), value.contextGeneration()));
    }

    /** Stable enum values for the ephemeral select menu; labels are rendered separately. */
    public String encodeStyleValue(ExcuseStyle style) {
        return java.util.Objects.requireNonNull(style, "style").name();
    }

    public Optional<ExcuseStyle> decodeStyleValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ExcuseStyle.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String encodeGenerationAction(String action, long gameResultId, int contextGeneration) {
        requirePositive(gameResultId, "gameResultId");
        requirePositive(contextGeneration, "contextGeneration");
        return PREFIX + ":" + VERSION + ":" + action + ":" + gameResultId + ":" + contextGeneration;
    }

    private Optional<GenerationAction> decodeGenerationAction(String componentId, String action) {
        String[] parts = parts(componentId, 5, action);
        if (parts == null) {
            return Optional.empty();
        }
        try {
            long gameResultId = Long.parseLong(parts[3]);
            int contextGeneration = Integer.parseInt(parts[4]);
            return gameResultId > 0 && contextGeneration > 0
                    ? Optional.of(new GenerationAction(gameResultId, contextGeneration)) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String[] parts(String componentId, int length, String action) {
        if (componentId == null) {
            return null;
        }
        String[] parts = componentId.split(":", -1);
        return parts.length == length && PREFIX.equals(parts[0]) && VERSION.equals(parts[1]) && action.equals(parts[2])
                ? parts : null;
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    public record Open(long gameResultId) {
        public Open {
            if (gameResultId <= 0) {
                throw new IllegalArgumentException("gameResultId must be positive");
            }
        }
    }

    public record Pick(long gameResultId, int contextGeneration, ExcuseRound round, int position) {
        public Pick {
            requirePositive(gameResultId, "gameResultId");
            requirePositive(contextGeneration, "contextGeneration");
            java.util.Objects.requireNonNull(round, "round");
            if (position < 1 || position > 3) {
                throw new IllegalArgumentException("position must be between 1 and 3");
            }
        }
    }

    public record Reroll(long gameResultId, int contextGeneration) {
        public Reroll { requirePositive(gameResultId, "gameResultId"); requirePositive(contextGeneration, "contextGeneration"); }
    }

    public record Style(long gameResultId, int contextGeneration) {
        public Style { requirePositive(gameResultId, "gameResultId"); requirePositive(contextGeneration, "contextGeneration"); }
    }

    public record Decline(long gameResultId, int contextGeneration) {
        public Decline { requirePositive(gameResultId, "gameResultId"); requirePositive(contextGeneration, "contextGeneration"); }
    }

    private record GenerationAction(long gameResultId, int contextGeneration) { }
}
