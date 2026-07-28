package de.venomenon.gridwordsbot.domain.model;

/** A valid attempt result from a Gridgames share header. */
public sealed interface ShareOutcome permits ShareOutcome.Solved, ShareOutcome.Unsolved {

    int maxAttempts();

    record Solved(int attemptsUsed, int maxAttempts) implements ShareOutcome {

        public Solved {
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("maxAttempts must be positive");
            }
            if (attemptsUsed < 1 || attemptsUsed > maxAttempts) {
                throw new IllegalArgumentException("attemptsUsed must be between 1 and maxAttempts");
            }
        }
    }

    record Unsolved(int maxAttempts) implements ShareOutcome {

        public Unsolved {
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("maxAttempts must be positive");
            }
        }
    }
}
