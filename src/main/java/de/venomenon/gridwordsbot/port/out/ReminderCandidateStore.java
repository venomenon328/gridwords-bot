package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Read model for a future reminder transport; it deliberately contains no Discord/JDA types. */
public interface ReminderCandidateStore {
    List<ReminderCandidate> findReminderCandidates(LocalDate gameDate);

    record ReminderCandidate(long discordUserId, String displayName, List<GameType> missingGames) {
        public ReminderCandidate {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
            missingGames = List.copyOf(Objects.requireNonNull(missingGames, "missingGames"));
            if (missingGames.isEmpty()) throw new IllegalArgumentException("a reminder candidate needs missing games");
        }
    }
}
