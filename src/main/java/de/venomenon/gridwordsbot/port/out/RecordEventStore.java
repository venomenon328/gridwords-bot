package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.record.RecordEventAppendResult;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only audit boundary; event facts are invalidated or superseded, never deleted. */
public interface RecordEventStore {
    RecordEventAppendResult append(RecordEventDraft draft);
    Optional<RecordEventSnapshot> find(UUID eventId);
    List<RecordEventSnapshot> findByTriggerKey(long guildId, String triggerKey);
    /** Valid and historical facts emitted from one canonical result identity. */
    default List<RecordEventSnapshot> findBySource(long guildId, RecordSourceReference source) { return List.of(); }
    default List<RecordEventSnapshot> findByResultId(long guildId, long resultId) { return List.of(); }
    boolean invalidate(UUID eventId, Instant invalidatedAt);
    boolean supersede(UUID eventId, UUID supersedingEventId, Instant invalidatedAt);
}
