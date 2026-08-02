package de.venomenon.gridwordsbot.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only proof that an interaction still targets the current daily-status message. */
public interface DailyStatusInteractionContextQuery {
    Optional<Context> findCurrent(long guildId, long channelId, long messageId, LocalDate gameDate);
    record Context(List<Participant> participants) { public Context { participants = List.copyOf(Objects.requireNonNull(participants, "participants")); } }
    record Participant(long discordUserId, String displayName) { public Participant { if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive"); Objects.requireNonNull(displayName, "displayName"); } }
}