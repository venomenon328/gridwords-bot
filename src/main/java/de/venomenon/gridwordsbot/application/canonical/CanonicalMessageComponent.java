package de.venomenon.gridwordsbot.application.canonical;

/** Transport-neutral interactive content attached to a canonical result message. */
public sealed interface CanonicalMessageComponent permits CanonicalMessageComponent.ExcuseOpen {

    /** Semantic action only; the Discord adapter owns its versioned custom-ID representation. */
    record ExcuseOpen(long gameResultId) implements CanonicalMessageComponent {
        public ExcuseOpen {
            if (gameResultId <= 0) {
                throw new IllegalArgumentException("gameResultId must be positive");
            }
        }
    }
}
