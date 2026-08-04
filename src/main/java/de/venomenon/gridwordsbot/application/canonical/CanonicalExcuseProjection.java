package de.venomenon.gridwordsbot.application.canonical;

import java.util.Objects;

/** Transport-neutral optional content of a canonical result message. */
public sealed interface CanonicalExcuseProjection
        permits CanonicalExcuseProjection.None, CanonicalExcuseProjection.Available, CanonicalExcuseProjection.Selected {

    static None none() {
        return None.INSTANCE;
    }

    final class None implements CanonicalExcuseProjection {
        private static final None INSTANCE = new None();

        private None() {
        }
    }

    final class Available implements CanonicalExcuseProjection {
        public static final Available INSTANCE = new Available();

        private Available() {
        }
    }

    record Selected(String renderedText) implements CanonicalExcuseProjection {
        public Selected {
            Objects.requireNonNull(renderedText, "renderedText");
            if (renderedText.isBlank()) {
                throw new IllegalArgumentException("renderedText must not be blank");
            }
        }
    }
}
