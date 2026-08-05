package de.venomenon.gridwordsbot.domain.record;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Previous comparable streak runs; may contain completed and other currently running runs. */
public record StreakRecordHistorySnapshot(List<StreakRun> runs) {
    public StreakRecordHistorySnapshot {
        List<StreakRun> copy = List.copyOf(Objects.requireNonNull(runs, "runs"));
        if (copy.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("runs must not contain null");
        Map<StreakRunIdentity, StreakRun> unique = new LinkedHashMap<>();
        for (StreakRun run : copy) {
            if (unique.putIfAbsent(run.identity(), run) != null) {
                throw new IllegalArgumentException("duplicate streak run identity");
            }
        }
        runs = List.copyOf(unique.values());
    }
}
