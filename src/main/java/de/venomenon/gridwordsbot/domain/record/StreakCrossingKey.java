package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;

/** Idempotent identity of the one permitted live crossing per run and definition. */
public record StreakCrossingKey(RecordDefinitionVersion definitionVersion,
        RecordDefinitionKey definitionKey, StreakRunIdentity runIdentity) {
    public StreakCrossingKey {
        Objects.requireNonNull(definitionVersion, "definitionVersion");
        Objects.requireNonNull(definitionKey, "definitionKey");
        Objects.requireNonNull(runIdentity, "runIdentity");
    }
}
