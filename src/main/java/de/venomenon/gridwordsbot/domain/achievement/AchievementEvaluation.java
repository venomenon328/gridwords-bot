package de.venomenon.gridwordsbot.domain.achievement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Deterministisches Ergebnis einer reinen Achievement-Auswertung für einen Teilnehmer. */
public record AchievementEvaluation(List<AchievementEvidence> achievements) {

    public AchievementEvaluation {
        achievements = List.copyOf(Objects.requireNonNull(achievements, "achievements"));
        Map<AchievementKey, AchievementEvidence> byKey = new LinkedHashMap<>();
        for (AchievementEvidence evidence : achievements) {
            Objects.requireNonNull(evidence, "achievement evidence");
            if (byKey.putIfAbsent(evidence.achievementKey(), evidence) != null) {
                throw new IllegalArgumentException("duplicate achievement evidence: " + evidence.achievementKey());
            }
        }
    }

    public Optional<AchievementEvidence> find(AchievementKey key) {
        Objects.requireNonNull(key, "key");
        return achievements.stream()
                .filter(evidence -> evidence.achievementKey().equals(key))
                .findFirst();
    }
}
