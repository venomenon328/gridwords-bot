package de.venomenon.gridwordsbot.domain.reporting;

import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Existing materialized Award and Record facts scoped to one completed report period. */
public record ReportHighlightFacts(Map<Long, Integer> activeAwardsByParticipant, List<RecordEventSnapshot> recordEvents) {
    public ReportHighlightFacts {
        activeAwardsByParticipant = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(
                activeAwardsByParticipant, "activeAwardsByParticipant")));
        recordEvents = List.copyOf(Objects.requireNonNull(recordEvents, "recordEvents"));
        activeAwardsByParticipant.forEach((participantId, count) -> {
            if (participantId == null || participantId <= 0 || count == null || count <= 0) {
                throw new IllegalArgumentException("award highlight facts must contain positive participant ids and counts");
            }
        });
    }

    public static ReportHighlightFacts empty() {
        return new ReportHighlightFacts(Map.of(), List.of());
    }
}
