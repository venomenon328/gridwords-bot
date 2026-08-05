package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;
import java.util.Optional;

/** Transport-neutral reconciliation fact between two full streak-run analyses. */
public record StreakRunChange(Type type, Optional<StreakRun> previous, Optional<StreakRun> current) {
    public StreakRunChange {
        Objects.requireNonNull(type, "type");
        previous = Objects.requireNonNull(previous, "previous");
        current = Objects.requireNonNull(current, "current");
        switch (type) {
            case ADDED -> {
                if (previous.isPresent() || current.isEmpty()) throw new IllegalArgumentException("invalid ADDED change");
            }
            case UPDATED -> {
                if (previous.isEmpty() || current.isEmpty()
                        || !previous.orElseThrow().identity().equals(current.orElseThrow().identity())
                        || previous.equals(current)) {
                    throw new IllegalArgumentException("invalid UPDATED change");
                }
            }
            case REMOVED -> {
                if (previous.isEmpty() || current.isPresent()) throw new IllegalArgumentException("invalid REMOVED change");
            }
        }
    }

    public enum Type { ADDED, UPDATED, REMOVED }
}
