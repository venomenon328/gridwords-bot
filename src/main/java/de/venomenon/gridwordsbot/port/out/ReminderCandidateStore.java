package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Read model for an aggregated reminder; it deliberately contains no Discord/JDA types. */
public interface ReminderCandidateStore {
    List<ReminderCandidate> findReminderCandidates(LocalDate gameDate);

    /**
     * One active player who still misses at least one game.
     *
     * <p>A reminder is sent only when at least one candidate opted in. If it is sent, all candidates are rendered;
     * {@code reminderOptIn} controls whether the Discord transport addresses this player with an actual user mention
     * or displays the server name as plain text.</p>
     */
    record ReminderCandidate(
            long discordUserId,
            String displayName,
            List<GameType> missingGames,
            boolean reminderOptIn) {

        /** Compatibility constructor for callers that describe a mention-enabled candidate. */
        public ReminderCandidate(long discordUserId, String displayName, List<GameType> missingGames) {
            this(discordUserId, displayName, missingGames, true);
        }

        public ReminderCandidate {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
            if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            missingGames = List.copyOf(Objects.requireNonNull(missingGames, "missingGames"));
            if (missingGames.isEmpty()) throw new IllegalArgumentException("a reminder candidate needs missing games");
        }
    }
}
