package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;

/** A transport-neutral current state for one result-record definition and concrete scope. */
public record ResultRecordStateSnapshot(
        RecordDefinitionKey definitionKey,
        RecordDefinitionVersion definitionVersion,
        RecordScope scope,
        ResultRecordObservation source) {

    public ResultRecordStateSnapshot {
        Objects.requireNonNull(definitionKey, "definitionKey");
        Objects.requireNonNull(definitionVersion, "definitionVersion");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(source, "source");
        if (scope instanceof RecordScope.Shared) {
            throw new IllegalArgumentException("result records do not support shared scope");
        }
        if (scope instanceof RecordScope.Personal personal && personal.playerId() != source.playerId()) {
            throw new IllegalArgumentException("personal result state source must belong to the scoped player");
        }
    }

    public long holderPlayerId() {
        return source.playerId();
    }

    public RecordSourceReference.GameResult sourceReference() {
        return source.sourceReference();
    }

    public RecordValue valueFor(ResultRecordMetric metric) {
        return source.valueFor(metric);
    }
}
