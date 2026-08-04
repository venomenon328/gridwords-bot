package de.venomenon.gridwordsbot.domain.excuse;

/** Injectable randomness boundary so every selection can be reproduced in tests. */
@FunctionalInterface
public interface ExcuseRandom {
    int nextInt(int bound);
}
