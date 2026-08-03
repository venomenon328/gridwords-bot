package de.venomenon.gridwordsbot.domain.excuse;

import java.util.Objects;
import java.util.random.RandomGenerator;

public final class JavaExcuseRandom implements ExcuseRandom {

    private final RandomGenerator random;

    public JavaExcuseRandom(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return random.nextInt(bound);
    }
}
