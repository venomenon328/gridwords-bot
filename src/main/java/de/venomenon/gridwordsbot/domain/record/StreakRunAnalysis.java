package de.venomenon.gridwordsbot.domain.record;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministically ordered set of all runs derived for one analysis window. */
public record StreakRunAnalysis(List<StreakRun> runs) {
    public StreakRunAnalysis {
        List<StreakRun> copy = List.copyOf(Objects.requireNonNull(runs, "runs"));
        if (copy.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("runs must not contain null");
        Map<StreakRunIdentity, StreakRun> unique = new LinkedHashMap<>();
        for (StreakRun run : copy) {
            if (unique.putIfAbsent(run.identity(), run) != null) {
                throw new IllegalArgumentException("duplicate streak run identity");
            }
        }
        runs = unique.values().stream().sorted(order()).toList();
    }

    private static Comparator<StreakRun> order() {
        return Comparator.comparing((StreakRun run) -> run.identity().metric().name())
                .thenComparing(run -> scopeKey(run.identity().ownerScope()))
                .thenComparing(run -> run.identity().startDate())
                .thenComparing(StreakRun::endDate);
    }

    static String scopeKey(RecordScope scope) {
        return switch (scope) {
            case RecordScope.Personal personal -> "personal:%020d".formatted(personal.playerId());
            case RecordScope.ServerIndividual ignored -> "server";
            case RecordScope.Shared ignored -> "shared";
        };
    }
}
