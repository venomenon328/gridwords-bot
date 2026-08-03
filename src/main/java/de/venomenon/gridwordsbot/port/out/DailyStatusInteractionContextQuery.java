package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only proof that an interaction still targets the current daily-status message. */
public interface DailyStatusInteractionContextQuery {
    Optional<Context> findCurrent(long guildId, long channelId, long messageId, LocalDate gameDate);

    record Context(List<Participant> gridWordsParticipants, List<Participant> quadWordsParticipants) {
        public Context {
            gridWordsParticipants = List.copyOf(Objects.requireNonNull(gridWordsParticipants, "gridWordsParticipants"));
            quadWordsParticipants = List.copyOf(Objects.requireNonNull(quadWordsParticipants, "quadWordsParticipants"));
        }

        public List<Participant> participants(GameType gameType) {
            Objects.requireNonNull(gameType, "gameType");
            return gameType == GameType.GRIDWORDS ? gridWordsParticipants : quadWordsParticipants;
        }
    }

    record Participant(long discordUserId, String displayName) {
        public Participant {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
        }
    }
}