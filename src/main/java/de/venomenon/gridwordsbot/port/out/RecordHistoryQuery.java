package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;

/** Read boundary for the complete canonical history needed by the one-off record bootstrap. */
public interface RecordHistoryQuery {
    RecordHistorySnapshot load(long guildId);
}
