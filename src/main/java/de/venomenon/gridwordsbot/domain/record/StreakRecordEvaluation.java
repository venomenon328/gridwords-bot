package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** One transport-neutral live or completion classification for a streak definition. */
public record StreakRecordEvaluation(RecordDefinition<StreakRecordValue> definition,
        RecordScope comparisonScope, StreakRun candidate, Optional<StreakRun> reference,
        StreakRecordPhase phase, StreakRecordClassification classification,
        boolean publicAnnouncementEligible, OptionalInt gapToReference) {
    public StreakRecordEvaluation {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(comparisonScope, "comparisonScope");
        Objects.requireNonNull(candidate, "candidate");
        reference = Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(classification, "classification");
        gapToReference = Objects.requireNonNull(gapToReference, "gapToReference");
        if (definition.scopeType() != comparisonScope.type()) throw new IllegalArgumentException("scope mismatch");
        if (definition.metric() != candidate.identity().metric()) throw new IllegalArgumentException("metric mismatch");
        if (classification == StreakRecordClassification.NONE && publicAnnouncementEligible) {
            throw new IllegalArgumentException("NONE cannot be publicly announced");
        }
        if ((classification == StreakRecordClassification.NEAR_MISS) != gapToReference.isPresent()) {
            throw new IllegalArgumentException("only near misses require a gap");
        }
        if (phase == StreakRecordPhase.LIVE && classification != StreakRecordClassification.NONE
                && classification != StreakRecordClassification.CROSSED) {
            throw new IllegalArgumentException("invalid live classification");
        }
        if (phase == StreakRecordPhase.COMPLETION && classification == StreakRecordClassification.CROSSED) {
            throw new IllegalArgumentException("completion cannot be classified as CROSSED");
        }
    }

    public Optional<StreakCrossingKey> crossingKey() {
        return classification == StreakRecordClassification.CROSSED
                ? Optional.of(new StreakCrossingKey(definition.definitionVersion(), definition.key(), candidate.identity()))
                : Optional.empty();
    }
}
