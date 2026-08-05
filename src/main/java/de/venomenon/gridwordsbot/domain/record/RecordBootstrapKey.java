package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;

/** One bootstrap/rebuild coordination row per guild and catalog version. */
public record RecordBootstrapKey(long guildId, RecordDefinitionVersion definitionVersion) {
    public RecordBootstrapKey {
        if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
        Objects.requireNonNull(definitionVersion, "definitionVersion");
    }
}
