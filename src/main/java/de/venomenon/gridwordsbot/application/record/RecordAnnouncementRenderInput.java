package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fully resolved data needed by the pure record-announcement renderer. */
public record RecordAnnouncementRenderInput(
        RecordAnnouncementRegistration registration,
        List<RecordEventSnapshot> events,
        Map<Long, String> playerDisplays) {
    public RecordAnnouncementRenderInput {
        Objects.requireNonNull(registration, "registration");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        playerDisplays = Map.copyOf(Objects.requireNonNull(playerDisplays, "playerDisplays"));
    }
}
