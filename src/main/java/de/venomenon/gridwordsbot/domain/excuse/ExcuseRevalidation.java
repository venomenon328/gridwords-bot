package de.venomenon.gridwordsbot.domain.excuse;

import java.util.Objects;

/** Typed state transition requested after a correction of an already decided result. */
public record ExcuseRevalidation(long gameResultId, Outcome outcome, ExcuseOfferContext offerContext) {

    public enum Outcome {
        KEEP_AVAILABLE,
        REPLACE_AVAILABLE_CONTEXT,
        KEEP_SELECTED,
        INVALIDATE
    }

    public ExcuseRevalidation {
        if (gameResultId <= 0) {
            throw new IllegalArgumentException("gameResultId must be positive");
        }
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(offerContext, "offerContext");
    }
}
