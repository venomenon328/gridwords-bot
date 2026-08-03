package de.venomenon.gridwordsbot.domain.excuse;

import java.time.Instant;
import java.util.Objects;

/** Server-validated reference to a currently persisted option. */
public record ExcuseOptionSelection(
        long gameResultId,
        int contextGeneration,
        int position,
        Instant selectedAt) {

    public ExcuseOptionSelection {
        if (gameResultId <= 0) {
            throw new IllegalArgumentException("gameResultId must be positive");
        }
        if (contextGeneration < 1) {
            throw new IllegalArgumentException("contextGeneration must be positive");
        }
        if (position < 1 || position > 3) {
            throw new IllegalArgumentException("position must be between 1 and 3");
        }
        Objects.requireNonNull(selectedAt, "selectedAt");
    }
}
