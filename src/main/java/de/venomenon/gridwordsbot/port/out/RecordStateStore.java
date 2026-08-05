package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.record.RecordStateInitialization;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdate;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import java.util.Optional;
import java.util.List;

/** Persistence boundary for materialized, lock-versioned record states. */
public interface RecordStateStore {
    Optional<RecordStateSnapshot> find(RecordStateKey key);
    List<RecordStateSnapshot> findAll(long guildId, de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion definitionVersion);
    RecordStateInitialization initialize(RecordStateKey key, RecordStateWrite write);
    RecordStateUpdateResult update(RecordStateUpdate update);
    boolean remove(RecordStateKey key, de.venomenon.gridwordsbot.domain.record.RecordLockVersion expectedLockVersion);
}
