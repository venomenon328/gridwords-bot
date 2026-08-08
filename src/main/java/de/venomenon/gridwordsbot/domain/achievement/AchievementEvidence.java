package de.venomenon.gridwordsbot.domain.achievement;

import java.time.LocalDate;
import java.util.Objects;

/** Transportneutraler Beleg dafür, wann und wodurch ein Achievement fachlich erreicht wurde. */
public record AchievementEvidence(
        AchievementKey achievementKey,
        LocalDate earnedOn,
        Kind kind,
        String reference) {

    public enum Kind {
        GAME_RESULT,
        GAME_DAY,
        STREAK,
        RESULT_SEQUENCE,
        AGGREGATE
    }

    public AchievementEvidence {
        Objects.requireNonNull(achievementKey, "achievementKey");
        Objects.requireNonNull(earnedOn, "earnedOn");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reference, "reference");
        if (reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
    }
}
