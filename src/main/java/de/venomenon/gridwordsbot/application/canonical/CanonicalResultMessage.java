package de.venomenon.gridwordsbot.application.canonical;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Transport-neutral data required for one canonical game-result embed. */
public record CanonicalResultMessage(
        String playerDisplayName, GameType gameType, LocalDate gameDate, ShareOutcome outcome, Duration duration,
        NormalizedBoard board, StreakSummary streaks, OptionalInt personalComplete, OptionalInt personalPerfect,
        OptionalInt sharedComplete, OptionalInt sharedPerfect, Optional<QuadWordsBoards> quadWordsBoards,
        String publicationKey) {

    public CanonicalResultMessage {
        Objects.requireNonNull(playerDisplayName);
        Objects.requireNonNull(gameType);
        Objects.requireNonNull(gameDate);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(duration);
        Objects.requireNonNull(streaks);
        Objects.requireNonNull(personalComplete);
        Objects.requireNonNull(personalPerfect);
        Objects.requireNonNull(sharedComplete);
        Objects.requireNonNull(sharedPerfect);
        quadWordsBoards = Objects.requireNonNull(quadWordsBoards);
        if (gameType == GameType.GRIDWORDS && (board == null || quadWordsBoards.isPresent())) {
            throw new IllegalArgumentException("a GridWords message requires only its board");
        }
        if (gameType == GameType.QUADWORDS && (board != null || quadWordsBoards.isEmpty())) {
            throw new IllegalArgumentException("a QuadWords message requires exactly four boards");
        }
        Objects.requireNonNull(publicationKey);
    }

    /** Compatibility constructor for existing GridWords publication callers. */
    public CanonicalResultMessage(String playerDisplayName, GameType gameType, LocalDate gameDate, ShareOutcome outcome,
            Duration duration, NormalizedBoard board, StreakSummary streaks, OptionalInt personalComplete,
            OptionalInt personalPerfect, OptionalInt sharedComplete, OptionalInt sharedPerfect, String publicationKey) {
        this(playerDisplayName, gameType, gameDate, outcome, duration, board, streaks, personalComplete,
                personalPerfect, sharedComplete, sharedPerfect, Optional.empty(), publicationKey);
    }
}
