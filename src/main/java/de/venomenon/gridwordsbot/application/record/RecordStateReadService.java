package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.port.out.RecordStateStore;
import java.util.List;
import java.util.Optional;

/** Deterministic materialized-state read boundary; it never scans canonical history. */
public final class RecordStateReadService {
    private final RecordStateStore store;
    public RecordStateReadService(RecordStateStore store) { this.store = java.util.Objects.requireNonNull(store); }
    public List<RecordStateSnapshot> list(long guildId, RecordDefinitionVersion version) { return store.findAll(guildId, version); }
    public Optional<RecordStateSnapshot> find(RecordStateKey key) { return store.find(key); }
}
