package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.Objects;
import java.util.Optional;

/** Eine vollständig typisierte Rekorddefinition ohne Infrastrukturbezug. */
public record RecordDefinition<V extends RecordValue>(
        RecordDefinitionKey key,
        RecordDefinitionVersion definitionVersion,
        RecordMetric metric,
        Optional<GameType> game,
        RecordScopeType scopeType,
        RecordValueComparator<V> comparator,
        RecordSourceEligibility sourceEligibility,
        RecordAnnouncementThreshold announcementThreshold) {

    public RecordDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(definitionVersion, "definitionVersion");
        Objects.requireNonNull(metric, "metric");
        game = Objects.requireNonNull(game, "game");
        Objects.requireNonNull(scopeType, "scopeType");
        Objects.requireNonNull(comparator, "comparator");
        Objects.requireNonNull(sourceEligibility, "sourceEligibility");
        Objects.requireNonNull(announcementThreshold, "announcementThreshold");
        if (!metric.valueKind().valueType().equals(comparator.valueType())) {
            throw new IllegalArgumentException("comparator value type does not match metric value kind");
        }
    }

    public RecordPolarity polarity() {
        return metric.polarity();
    }

    public RecordValueKind valueKind() {
        return metric.valueKind();
    }

    public RecordSourceType sourceType() {
        return metric.sourceType();
    }

    public RecordComparison compare(V candidate, V current) {
        return comparator.compare(candidate, current);
    }
}
