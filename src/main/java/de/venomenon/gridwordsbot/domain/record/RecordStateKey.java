package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;

/** Stable, non-null business key of one materialized record state. */
public record RecordStateKey(
        long guildId,
        RecordDefinitionKey definitionKey,
        RecordDefinitionVersion definitionVersion,
        RecordScope scope) {
    public RecordStateKey {
        if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
        Objects.requireNonNull(definitionKey, "definitionKey");
        Objects.requireNonNull(definitionVersion, "definitionVersion");
        Objects.requireNonNull(scope, "scope");
    }

    /** Never nullable, unlike a player-id column used by older SQL unique keys. */
    public String scopeKey() {
        return switch (scope) {
            case RecordScope.Personal personal -> "player:" + personal.playerId();
            case RecordScope.ServerIndividual ignored -> "server";
            case RecordScope.Shared ignored -> "shared";
        };
    }
}
