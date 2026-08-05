package de.venomenon.gridwordsbot.domain.record;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure diff of derived runs; splits and joins emerge as removals plus additions. */
public final class StreakRunReconciler {
    public List<StreakRunChange> reconcile(StreakRunAnalysis previous, StreakRunAnalysis current) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Map<StreakRunIdentity, StreakRun> before = index(previous.runs());
        Map<StreakRunIdentity, StreakRun> after = index(current.runs());
        Set<StreakRunIdentity> identities = new LinkedHashSet<>(before.keySet());
        identities.addAll(after.keySet());
        List<StreakRunChange> changes = new ArrayList<>();
        identities.stream().sorted(identityOrder()).forEach(identity -> {
            StreakRun oldRun = before.get(identity);
            StreakRun newRun = after.get(identity);
            if (oldRun == null) {
                changes.add(new StreakRunChange(StreakRunChange.Type.ADDED, Optional.empty(), Optional.of(newRun)));
            } else if (newRun == null) {
                changes.add(new StreakRunChange(StreakRunChange.Type.REMOVED, Optional.of(oldRun), Optional.empty()));
            } else if (!oldRun.equals(newRun)) {
                changes.add(new StreakRunChange(StreakRunChange.Type.UPDATED, Optional.of(oldRun), Optional.of(newRun)));
            }
        });
        return List.copyOf(changes);
    }

    private static Map<StreakRunIdentity, StreakRun> index(List<StreakRun> runs) {
        Map<StreakRunIdentity, StreakRun> index = new LinkedHashMap<>();
        runs.forEach(run -> index.put(run.identity(), run));
        return index;
    }

    private static Comparator<StreakRunIdentity> identityOrder() {
        return Comparator.comparing((StreakRunIdentity identity) -> identity.metric().name())
                .thenComparing(identity -> StreakRunAnalysis.scopeKey(identity.ownerScope()))
                .thenComparing(StreakRunIdentity::startDate);
    }
}
